package com.potato.liftinsight.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import com.potato.liftinsight.R
import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.record.LandmarkPosition
import com.potato.liftinsight.record.parseLandmarksJson
import com.potato.liftinsight.training.data.PoseFrameDao
import com.potato.liftinsight.training.data.PoseFrameEntity
import com.potato.liftinsight.training.data.BarbellFrameDao
import com.potato.liftinsight.training.data.TimeseriesDao
import com.potato.liftinsight.training.data.TimeseriesPoint
import java.io.File

internal class VideoExportRenderWorker(
    private val context: Context,
    private val videoFileManager: VideoFileManager,
    private val poseFrameDao: PoseFrameDao,
    private val timeseriesDao: TimeseriesDao,
    private val barbellFrameDao: BarbellFrameDao,
    private val logger: AppLogger
) {

    suspend fun renderExport(
        videoName: String,
        metahistoryId: Int,
        motionName: String,
        date: String,
        options: ExportOverlayOptions,
        progressCallback: (Int) -> Unit
    ): File? {
        logger.info(TAG, "renderExport START: videoName=$videoName, metahistoryId=$metahistoryId, motionName=$motionName, date=$date, options=$options")

        // 1. Resolve original video file
        val inputFile = videoFileManager.resolveVideoFile(videoName)
        logger.info(TAG, "Input file resolved: ${inputFile.absolutePath}, exists=${inputFile.exists()}, size=${inputFile.length()}")
        if (!inputFile.exists()) {
            logger.warn(TAG, "Original video file not found: videoName=$videoName")
            return null
        }

        // 2. Read frame timestamps
        val timestampsUs = readFrameTimestamps(inputFile)
        logger.info(TAG, "Frame timestamps read: count=${timestampsUs.size}, first=${timestampsUs.firstOrNull()}, last=${timestampsUs.lastOrNull()}")

        // 3. Load ALL pose frames sorted by timestampMs
        val rawPoseFrames = poseFrameDao.getPoseFrames(metahistoryId)
            .sortedBy { it.timestampMs }
        logger.info(TAG, "Pose frames loaded: count=${rawPoseFrames.size}")

        // Load barbell frames if barbell trace overlay is enabled
        val rawBarbellFrames = if (options.showBarbellTrace) {
            barbellFrameDao.getBarbellFrames(metahistoryId).sortedBy { it.timestampMs }
        } else {
            emptyList()
        }
        logger.info(TAG, "Barbell frames loaded: count=${rawBarbellFrames.size}, showBarbellTrace=${options.showBarbellTrace}")

        // Apply RDP smoothing to pose frames if enabled
        val poseFrames = if (options.rdpSmoothSkeleton && rawPoseFrames.size > 2) {
            applyRdpSmoothingToPoseFrames(rawPoseFrames, options.rdpEpsilon)
        } else {
            rawPoseFrames
        }
        logger.info(TAG, "Effective pose frames after RDP smoothing: count=${poseFrames.size}")

        // 4. Load ALL timeseries grouped by metricName
        val timeseriesByMetric = loadTimeseries(metahistoryId)
        logger.info(TAG, "Timeseries loaded: metrics=${timeseriesByMetric.keys}, counts=${timeseriesByMetric.mapValues { it.value.size }}")

        // Compute period boundaries for angle plot
        val periods = PeriodDetector.detect(timeseriesByMetric, activeRange = null)

        // 5. Get video dimensions
        val retriever = MediaMetadataRetriever()
        var width: Int
        var height: Int
        try {
            retriever.setDataSource(inputFile.absolutePath)

            // Get dimensions from the first decoded frame (getFrameAtTime returns
            // display-oriented frames, so this already accounts for rotation)
            val sampleFrame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)
            if (sampleFrame != null) {
                width = sampleFrame.width
                height = sampleFrame.height
                sampleFrame.recycle()
            } else {
                // Fallback to metadata
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                if (width <= 0 || height <= 0) {
                    logger.warn(TAG, "Could not determine video dimensions")
                    retriever.release()
                    return null
                }
            }
        } catch (e: Exception) {
            logger.error(TAG, "Failed to get video dimensions", e)
            retriever.release()
            return null
        }
        logger.info(TAG, "Video dimensions: ${width}x${height}")

        // 6. Build output filename
        val safeMotionName = sanitizeForFilename(motionName)
        val safeDate = sanitizeForFilename(date)
        val renderedItems = options.renderedItemsCode
        val outputFileName = "LiftInsight-${safeMotionName}-${safeDate}-${renderedItems}.mp4"
        val outputFile = File(videoFileManager.videoDirectory(), outputFileName)
        logger.info(TAG, "Output file: ${outputFile.absolutePath}")

        // 7. Create encoder session
        val encoderService = VideoEncoderService()
        val session = encoderService.createSession(outputFile, width, height, 30)
        logger.info(TAG, "Encoder session created: width=$width, height=$height, fps=30")

        try {
            val firstTimestampUs = timestampsUs.firstOrNull() ?: 0L
            var lastTimestampUs = 0L

            // 9. Process each frame
            timestampsUs.forEachIndexed { index, timestampUs ->
                try {
                val normalizedTimestampUs = (timestampUs - firstTimestampUs).coerceAtLeast(0L)
                val timestampMs = normalizedTimestampUs / 1000L

                if (index % 100 == 0) {
                    logger.info(TAG, "Processing frame $index/${timestampsUs.size}, timestampUs=$timestampUs, normalizedTimestampUs=$normalizedTimestampUs")
                }

                // Decode source bitmap
                val sourceBitmap = retriever.getFrameAtTime(
                    timestampUs.coerceAtLeast(0L),
                    MediaMetadataRetriever.OPTION_CLOSEST
                )

                if (sourceBitmap == null) {
                    logger.warn(TAG, "Skipping frame $index: source bitmap is null at timestampUs=$timestampUs")
                    progressCallback((index * 100) / timestampsUs.size)
                    return@forEachIndexed
                }

                // Create a fresh ARGB_8888 bitmap at output dimensions
                val compositedFrame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(compositedFrame)

                // Draw source frame onto composited frame
                val srcRect = android.graphics.Rect(0, 0, sourceBitmap.width, sourceBitmap.height)
                val dstRect = android.graphics.Rect(0, 0, width, height)
                canvas.drawBitmap(sourceBitmap, srcRect, dstRect, null)
                sourceBitmap.recycle()

                // Draw overlays on top of the source frame
                // If showSkeleton: use proportional index matching
                if (options.showSkeleton && poseFrames.isNotEmpty()) {
                    val poseIndex = if (timestampsUs.size > 1) {
                        (index.toLong() * (poseFrames.size - 1) / timestampsUs.size).toInt()
                            .coerceIn(0, poseFrames.size - 1)
                    } else {
                        0
                    }
                    val poseFrame = poseFrames[poseIndex]
                    val landmarks = parseLandmarksJson(poseFrame.landmarksJson)
                    if (landmarks.isNotEmpty()) {
                        val pixelPositions = landmarks.mapValues { (_, lp) ->
                            PoseOverlayLandmark(
                                x = lp.x * width,
                                y = lp.y * height,
                                visibility = lp.visibility
                            )
                        }
                        PoseOverlayRenderer.drawPoseLandmarks(canvas, pixelPositions)
                    }
                }

                // If showAngleDisplay: use proportional index matching with RDP interpolation
                if (options.showAngleDisplay && timeseriesByMetric.isNotEmpty()) {
                    val currentAngles = mutableMapOf<String, Double?>()
                    timeseriesByMetric.forEach { (metricName, points) ->
                        if (points.isNotEmpty()) {
                            val simplified = RdpSimplifier.simplify(points, options.rdpEpsilon)
                            val interpolated = RdpSimplifier.interpolateValue(simplified, timestampMs)
                            currentAngles[metricName] = interpolated
                        }
                    }
                    val lines = buildAngleTextLinesFromMap(currentAngles)
                    PoseOverlayRenderer.drawAngleOverlay(canvas, lines)
                }

                // If showAnglePlot: draw angle plot
                if (options.showAnglePlot && timeseriesByMetric.values.any { it.isNotEmpty() }) {
                    val totalDurationMs = (timestampsUs.last() - timestampsUs.first()).coerceAtLeast(1L) / 1000L
                    AnglePlotRenderer.drawAnglePlotOnCanvas(
                        canvas = canvas,
                        angleTimeSeries = timeseriesByMetric,
                        currentPositionMs = timestampMs,
                        totalDurationMs = totalDurationMs,
                        canvasWidth = width.toFloat(),
                        canvasHeight = height.toFloat(),
                        rdpEpsilon = options.rdpEpsilon,
                        activeRange = null,
                        periods = periods
                    )
                }

                // If showBarbellTrace: draw barbell position and trace
                if (options.showBarbellTrace && rawBarbellFrames.isNotEmpty()) {
                    val barbellIndex = if (timestampsUs.size > 1) {
                        (index.toLong() * (rawBarbellFrames.size - 1) / timestampsUs.size).toInt()
                            .coerceIn(0, rawBarbellFrames.size - 1)
                    } else {
                        0
                    }
                    val tracePositions = rawBarbellFrames.subList(0, barbellIndex + 1).map { frame ->
                        BarbellPosition(
                            x = frame.x * width,
                            y = frame.y * height,
                            radius = frame.radius * width.coerceAtMost(height).toFloat(),
                            confidence = frame.confidence
                        )
                    }
                    BarbellOverlayRenderer.drawBarbellTraceAndPosition(canvas, tracePositions, tracePositions.lastIndex)
                }

                // Write frame to encoder
                session.writeFrame(compositedFrame, normalizedTimestampUs)
                lastTimestampUs = normalizedTimestampUs

                // Recycle composited frame
                compositedFrame.recycle()

                // Report progress
                progressCallback((index * 100) / timestampsUs.size)
                } catch (e: Exception) {
                    logger.error(TAG, "Failed to process frame $index at timestampUs=$timestampUs", e)
                    // Continue with next frame instead of failing the whole export
                }
            }

            session.finish(lastTimestampUs)
            logger.info(TAG, "Encoder finished, checking output: outputFile.exists=${outputFile.exists()}, outputFile.length=${outputFile.length()}")
            session.release()

            logger.info(TAG, "Export complete: outputFile=${outputFile.absolutePath}")
            return outputFile
        } catch (e: Exception) {
            logger.error(TAG, "Export rendering FAILED at frame processing: ${e.message}", e)
            try { session.release() } catch (_: Exception) {}
            // Clean up failed output
            if (outputFile.exists()) {
                outputFile.delete()
            }
            // Copy original as fallback
            return try {
                inputFile.copyTo(outputFile, overwrite = true)
                logger.info(TAG, "Fallback SUCCESS: copied original video, output exists=${outputFile.exists()}, size=${outputFile.length()}")
                outputFile
            } catch (e2: Exception) {
                logger.error(TAG, "Fallback copy FAILED: ${e2.message}", e2)
                null
            }
        } finally {
            retriever.release()
        }
    }

    private fun loadTimeseries(metahistoryId: Int): Map<String, List<TimeseriesPoint>> {
        val metricNames = timeseriesDao.getAvailableMetrics(metahistoryId)
        val result = mutableMapOf<String, List<TimeseriesPoint>>()
        metricNames.forEach { metricName ->
            val points = timeseriesDao.getTimeSeries(metahistoryId, metricName)
            if (points.isNotEmpty()) {
                result[metricName] = points
            }
        }
        return result
    }

    private fun readFrameTimestamps(inputFile: File): List<Long> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(inputFile.absolutePath)

            val videoTrackIndex = selectVideoTrack(extractor)
                ?: throw IllegalStateException("No video track found")
            logger.info(TAG, "readFrameTimestamps: trackCount=${extractor.trackCount}, videoTrackIndex=$videoTrackIndex")

            extractor.selectTrack(videoTrackIndex)

            val timestampsUs = mutableListOf<Long>()
            while (true) {
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0L) break
                timestampsUs += sampleTimeUs
                if (!extractor.advance()) break
            }

            return if (timestampsUs.isEmpty()) listOf(0L) else timestampsUs
        } finally {
            extractor.release()
        }
    }

    private fun selectVideoTrack(extractor: MediaExtractor): Int? {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) return index
        }
        return null
    }

    private fun buildAngleTextLinesFromMap(angles: Map<String, Double?>): List<String> {
        val lines = mutableListOf<String>()
        angles["spine_angle"]?.let { lines += context.getString(R.string.training_video_overlay_spine_angle, it) }
        angles["left_leg_spine_angle"]?.let { lines += context.getString(R.string.training_video_overlay_left_leg_spine_angle, it) }
        angles["right_leg_spine_angle"]?.let { lines += context.getString(R.string.training_video_overlay_right_leg_spine_angle, it) }
        angles["left_knee_angle"]?.let { lines += context.getString(R.string.training_video_overlay_left_knee_angle, it) }
        angles["right_knee_angle"]?.let { lines += context.getString(R.string.training_video_overlay_right_knee_angle, it) }
        return lines
    }

    private fun sanitizeForFilename(input: String): String {
        return input
            .replace(" ", "_")
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "")
    }

    private fun applyRdpSmoothingToPoseFrames(
        frames: List<PoseFrameEntity>,
        epsilon: Double
    ): List<PoseFrameEntity> {
        // Build a "motion magnitude" time series: total landmark displacement per frame
        val motionPoints = mutableListOf<TimeseriesPoint>()
        for (i in frames.indices) {
            val frame = frames[i]
            val prevFrame = frames.getOrNull(i - 1)
            val displacement = if (prevFrame != null) {
                val currLandmarks = parseLandmarksJson(frame.landmarksJson)
                val prevLandmarks = parseLandmarksJson(prevFrame.landmarksJson)
                var totalDist = 0.0
                var count = 0
                for ((type, curr) in currLandmarks) {
                    val prev = prevLandmarks[type] ?: continue
                    val dx = curr.x - prev.x
                    val dy = curr.y - prev.y
                    totalDist += Math.sqrt((dx * dx + dy * dy).toDouble())
                    count++
                }
                if (count > 0) totalDist / count else 0.0
            } else {
                1.0 // Keep first frame
            }
            motionPoints.add(TimeseriesPoint(frame.timestampMs, displacement))
        }

        // Apply RDP to the motion series
        val simplifiedMotion = RdpSimplifier.simplify(motionPoints, epsilon / 1000.0)
        val simplifiedTimestamps = simplifiedMotion.map { it.timestampMs }.toSet()

        // Keep frames that are at simplified timestamps (or nearest), always keep first and last
        return frames.filter { frame ->
            simplifiedTimestamps.any { ts -> Math.abs(ts - frame.timestampMs) < 16 } ||
                frame == frames.first() ||
                frame == frames.last()
        }
    }

    companion object {
        private const val TAG = "VideoExportRenderWorker"
    }
}
