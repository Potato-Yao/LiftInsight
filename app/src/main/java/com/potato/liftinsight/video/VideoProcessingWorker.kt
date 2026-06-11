package com.potato.liftinsight.video

import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.training.data.MetahistoryTimeseriesEntity
import com.potato.liftinsight.training.data.PoseFrameEntity
import com.potato.liftinsight.training.data.TimeseriesMetric
import com.potato.liftinsight.training.data.VideoProcessState
import com.potato.liftinsight.training.data.VideoProcessStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal class VideoProcessingWorker(
    private val videoFileManager: VideoFileManager,
    private val poseDetectionService: PoseDetectionService,
    private val logger: AppLogger,
    private val videoProcessStore: VideoProcessStore
) {
    suspend fun processVideo(videoName: String) = withContext(Dispatchers.IO) {
        val inputFile = awaitInputVideoFile(videoName)

        if (inputFile == null) {
            logger.warn(TAG, "Cannot process video because the finalized source file was not ready: videoName=$videoName")
            videoProcessStore.upsertVideoProcessState(
                VideoProcessStateEntity(
                    videoName = videoName,
                    state = VideoProcessState.ERROR.name,
                    progress = 0,
                    processedVideoName = null
                )
            )
            return@withContext
        }

        val metahistoryId = videoProcessStore.getMetaHistoryIdByVideoName(videoName)

        logger.debug(TAG, "Starting pose detection: videoName=$videoName")

        videoProcessStore.upsertVideoProcessState(
            VideoProcessStateEntity(
                videoName = videoName,
                state = VideoProcessState.PROCESSING.name,
                progress = 0,
                processedVideoName = null
            )
        )

        try {
            val result = processFrames(
                inputFile = inputFile,
                videoName = videoName,
                metahistoryId = metahistoryId
            )

            if (metahistoryId != null && result.timeseriesEntries.isNotEmpty()) {
                videoProcessStore.replaceTimeseries(metahistoryId, result.timeseriesEntries)
                logger.info(TAG, "Persisted ${result.timeseriesEntries.size} timeseries data points: videoName=$videoName, metahistoryId=$metahistoryId")
            } else if (metahistoryId == null) {
                logger.warn(TAG, "No metahistory record found for videoName=$videoName, skipping timeseries persistence")
            }

            if (metahistoryId != null && result.poseFrameEntries.isNotEmpty()) {
                videoProcessStore.replacePoseFrames(metahistoryId, result.poseFrameEntries)
                logger.info(TAG, "Persisted ${result.poseFrameEntries.size} pose frame entries: videoName=$videoName, metahistoryId=$metahistoryId")
            } else if (metahistoryId == null) {
                logger.warn(TAG, "No metahistory record found for videoName=$videoName, skipping pose frame persistence")
            }

            videoProcessStore.upsertVideoProcessState(
                VideoProcessStateEntity(
                    videoName = videoName,
                    state = VideoProcessState.DONE.name,
                    progress = 100,
                    processedVideoName = null
                )
            )
            logger.info(TAG, "Finished pose detection: videoName=$videoName")
        } catch (error: Exception) {
            logger.error(TAG, "Video processing failed: videoName=$videoName", error)

            videoProcessStore.upsertVideoProcessState(
                VideoProcessStateEntity(
                    videoName = videoName,
                    state = VideoProcessState.ERROR.name,
                    progress = 0,
                    processedVideoName = null
                )
            )
        }
    }

    private fun processFrames(
        inputFile: java.io.File,
        videoName: String,
        metahistoryId: Int?
    ): ProcessFrameResult {
        val timestampsUs = readFrameTimestamps(inputFile)
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(inputFile.absolutePath)

        val firstFrameData = loadFirstAvailableFrame(
            retriever = retriever,
            timestampsUs = timestampsUs
        ) ?: throw IllegalStateException("Could not decode any video frame")
        val firstFrame = firstFrameData.bitmap
        val firstFrameTimestampUs = firstFrameData.timestampUs

        val timeseriesEntries = mutableListOf<MetahistoryTimeseriesEntity>()
        val poseFrameEntries = mutableListOf<PoseFrameEntity>()
        var processedFrameCount = 0

        try {
            timestampsUs.forEachIndexed { index, timestampUs ->
                val sourceFrame = if (index == 0) {
                    firstFrame
                } else {
                    loadFrameBitmap(
                        retriever = retriever,
                        presentationTimeUs = timestampUs
                    ) ?: return@forEachIndexed
                }

                val detectionResult = poseDetectionService.detectAndDrawPose(sourceFrame)
                val normalizedPresentationTimeUs = (timestampUs - firstFrameTimestampUs).coerceAtLeast(0L)

                if (metahistoryId != null) {
                    val normalizedPresentationTimeMs = normalizedPresentationTimeUs / 1000L
                    timeseriesEntries.addAll(
                        buildTimeseriesEntries(
                            metahistoryId = metahistoryId,
                            timestampMs = normalizedPresentationTimeMs,
                            angles = detectionResult.angles
                        )
                    )
                    if (detectionResult.landmarkPositions.isNotEmpty()) {
                        poseFrameEntries += PoseFrameEntity(
                            metahistoryId = metahistoryId,
                            timestampMs = normalizedPresentationTimeMs,
                            landmarksJson = buildLandmarksJson(
                                positions = detectionResult.landmarkPositions,
                                frameWidth = sourceFrame.width,
                                frameHeight = sourceFrame.height
                            )
                        )
                    }
                }

                processedFrameCount++

                if (processedFrameCount % 100 == 0) {
                    logger.trace(TAG, "Detected pose in frames: videoName=$videoName, framesProcessed=$processedFrameCount")
                }

                // Recycle non-first frames (firstFrame is recycled in finally)
                if (sourceFrame !== firstFrame) {
                    sourceFrame.recycle()
                }

                if (index == timestampsUs.lastIndex || index % 3 == 0) {
                    videoProcessStore.updateVideoProcessProgress(
                        videoName = videoName,
                        state = VideoProcessState.PROCESSING.name,
                        progress = (((index + 1) * 100) / timestampsUs.size).coerceIn(0, 99)
                    )
                }
            }

            if (processedFrameCount == 0) {
                throw IllegalStateException("No frames were detected")
            }
        } finally {
            if (!firstFrame.isRecycled) {
                firstFrame.recycle()
            }
            retriever.release()
        }

        return ProcessFrameResult(timeseriesEntries, poseFrameEntries)
    }

    private fun buildTimeseriesEntries(
        metahistoryId: Int,
        timestampMs: Long,
        angles: PoseOverlayAngles
    ): List<MetahistoryTimeseriesEntity> {
        val entries = mutableListOf<MetahistoryTimeseriesEntity>()

        angles.spineAngle?.let { angle ->
            entries += MetahistoryTimeseriesEntity(
                metahistoryId = metahistoryId,
                timestampMs = timestampMs,
                metricName = TimeseriesMetric.SPINE_ANGLE,
                value = angle
            )
        }
        angles.leftLegSpineAngle?.let { angle ->
            entries += MetahistoryTimeseriesEntity(
                metahistoryId = metahistoryId,
                timestampMs = timestampMs,
                metricName = TimeseriesMetric.LEFT_LEG_SPINE_ANGLE,
                value = angle
            )
        }
        angles.rightLegSpineAngle?.let { angle ->
            entries += MetahistoryTimeseriesEntity(
                metahistoryId = metahistoryId,
                timestampMs = timestampMs,
                metricName = TimeseriesMetric.RIGHT_LEG_SPINE_ANGLE,
                value = angle
            )
        }
        angles.leftKneeAngle?.let { angle ->
            entries += MetahistoryTimeseriesEntity(
                metahistoryId = metahistoryId,
                timestampMs = timestampMs,
                metricName = TimeseriesMetric.LEFT_KNEE_ANGLE,
                value = angle
            )
        }
        angles.rightKneeAngle?.let { angle ->
            entries += MetahistoryTimeseriesEntity(
                metahistoryId = metahistoryId,
                timestampMs = timestampMs,
                metricName = TimeseriesMetric.RIGHT_KNEE_ANGLE,
                value = angle
            )
        }

        return entries
    }

    private fun buildLandmarksJson(positions: Map<Int, PoseOverlayLandmark>, frameWidth: Int, frameHeight: Int): String {
        return buildLandmarksJsonInternal(positions, frameWidth, frameHeight)
    }

    private fun readFrameTimestamps(inputFile: java.io.File): List<Long> {
        val extractor = MediaExtractor()

        try {
            extractor.setDataSource(inputFile.absolutePath)

            val videoTrackIndex = selectVideoTrack(extractor)
                ?: throw IllegalStateException("No video track found")

            extractor.selectTrack(videoTrackIndex)

            val timestampsUs = mutableListOf<Long>()

            while (true) {
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0L) {
                    break
                }

                timestampsUs += sampleTimeUs

                if (!extractor.advance()) {
                    break
                }
            }

            return if (timestampsUs.isEmpty()) {
                listOf(0L)
            } else {
                timestampsUs
            }
        } finally {
            extractor.release()
        }
    }

    private suspend fun awaitInputVideoFile(videoName: String): java.io.File? {
        val inputFile = videoFileManager.resolveVideoFile(videoName)

        repeat(VideoFileManager.INPUT_FILE_READY_CHECK_COUNT) {
            if (isVideoFileReady(inputFile)) {
                return inputFile
            }

            delay(VideoFileManager.INPUT_FILE_READY_DELAY_MS)
        }

        return inputFile.takeIf(::isVideoFileReady)
    }

    private fun isVideoFileReady(inputFile: java.io.File): Boolean {
        if (!inputFile.exists() || inputFile.length() <= 0L) {
            return false
        }

        val retriever = MediaMetadataRetriever()

        return try {
            retriever.setDataSource(inputFile.absolutePath)

            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            val firstFrame = loadFrameBitmap(retriever, 0L)
            val isReady = durationMs > 0L && firstFrame != null

            firstFrame?.recycle()
            isReady
        } catch (_: Exception) {
            false
        } finally {
            retriever.release()
        }
    }

    private fun selectVideoTrack(extractor: MediaExtractor): Int? {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

            if (mime.startsWith("video/")) {
                return index
            }
        }

        return null
    }

    private fun loadFrameBitmap(
        retriever: MediaMetadataRetriever,
        presentationTimeUs: Long,
        width: Int? = null,
        height: Int? = null
    ): Bitmap? {
        val normalizedTimeUs = presentationTimeUs.coerceAtLeast(0L)

        return if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
            width != null &&
            height != null
        ) {
            retriever.getScaledFrameAtTime(
                normalizedTimeUs,
                MediaMetadataRetriever.OPTION_CLOSEST,
                width,
                height
            )
        } else {
            val bitmap = retriever.getFrameAtTime(
                normalizedTimeUs,
                MediaMetadataRetriever.OPTION_CLOSEST
            ) ?: return null

            if (width == null || height == null || (bitmap.width == width && bitmap.height == height)) {
                bitmap
            } else {
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
                bitmap.recycle()
                scaledBitmap
            }
        }
    }

    private fun loadFirstAvailableFrame(
        retriever: MediaMetadataRetriever,
        timestampsUs: List<Long>
    ): DecodedFrame? {
        timestampsUs.forEach { timestampUs ->
            val bitmap = loadFrameBitmap(
                retriever = retriever,
                presentationTimeUs = timestampUs
            )

            if (bitmap != null) {
                return DecodedFrame(
                    bitmap = bitmap,
                    timestampUs = timestampUs
                )
            }
        }

        return loadFrameBitmap(
            retriever = retriever,
            presentationTimeUs = 0L
        )?.let { bitmap ->
            DecodedFrame(
                bitmap = bitmap,
                timestampUs = 0L
            )
        }
    }

    private companion object {
        private const val TAG = "VideoProcessingWorker"
    }
}

private data class ProcessFrameResult(
    val timeseriesEntries: List<MetahistoryTimeseriesEntity>,
    val poseFrameEntries: List<PoseFrameEntity>
)

private data class DecodedFrame(
    val bitmap: Bitmap,
    val timestampUs: Long
)

internal fun buildLandmarksJsonInternal(
    positions: Map<Int, PoseOverlayLandmark>,
    frameWidth: Int,
    frameHeight: Int
): String {
    val jsonArray = org.json.JSONArray()
    positions.forEach { (type, landmark) ->
        val obj = org.json.JSONObject()
        obj.put("t", type)
        obj.put("x", (landmark.x / frameWidth).toDouble())
        obj.put("y", (landmark.y / frameHeight).toDouble())
        obj.put("v", landmark.visibility.toDouble())
        jsonArray.put(obj)
    }
    return jsonArray.toString()
}
