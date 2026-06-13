package com.potato.liftinsight.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.training.data.BarbellFrameEntity
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

internal class BarbellTrackingService(
    private val logger: AppLogger
) {
    /**
     * Tracks a user-selected weight plate circle across all video frames.
     *
     * For each frame:
     * 1. Extract the bitmap at that timestamp
     * 2. Detect circles using HoughCircles
     * 3. Match the nearest circle (by position proximity + size similarity) to the previous tracked position
     * 4. Store the normalized position
     *
     * @param videoFile The video file to process
     * @param initialX Initial normalized x position of the selected circle (0..1)
     * @param initialY Initial normalized y position of the selected circle (0..1)
     * @param initialRadius Initial normalized radius of the selected circle
     * @param metahistoryId The metahistory ID for the resulting entities
     * @param detectionService The circle detection service
     * @param onProgress Progress callback (0..100)
     * @param logger Logger instance
     * @return List of BarbellFrameEntity with normalized coordinates ready for persistence
     */
    fun trackSelectedCircle(
        videoFile: File,
        initialX: Float,
        initialY: Float,
        initialRadius: Float,
        metahistoryId: Int,
        detectionService: BarbellDetectionService,
        onProgress: (Int) -> Unit
    ): List<BarbellFrameEntity> {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(videoFile.absolutePath)

        // Get video dimensions for normalization
        val sampleFrame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: run {
                retriever.release()
                logger.warn(TAG, "Could not decode first frame for tracking")
                return emptyList()
            }
        val frameWidth = sampleFrame.width.toFloat()
        val frameHeight = sampleFrame.height.toFloat()
        val frameDiagonal = sqrt((frameWidth * frameWidth + frameHeight * frameHeight))
        val maxTrackingDistance = frameDiagonal * MAX_JUMP_FRAC
        logger.info(TAG, "Video dimensions for tracking: ${sampleFrame.width}x${sampleFrame.height}")

        // Read frame timestamps
        val timestampsUs = readFrameTimestamps(videoFile)
        if (timestampsUs.isEmpty()) {
            sampleFrame.recycle()
            retriever.release()
            logger.warn(TAG, "No frame timestamps found")
            return emptyList()
        }

        val firstTimestampUs = timestampsUs.first()
        val results = mutableListOf<BarbellFrameEntity>()

        // Initialize tracking with the selected circle (in pixel coordinates)
        var prevPixelX = initialX * frameWidth
        var prevPixelY = initialY * frameHeight
        var prevPixelRadius = initialRadius * frameWidth.coerceAtMost(frameHeight)

        try {
            timestampsUs.forEachIndexed { index, timestampUs ->
                val sourceBitmap: Bitmap? = if (index == 0) {
                    sampleFrame
                } else {
                    retriever.getFrameAtTime(
                        timestampUs.coerceAtLeast(0L),
                        MediaMetadataRetriever.OPTION_CLOSEST
                    )?.also {
                        // Ensure we have an ARGB_8888 bitmap for OpenCV
                        if (it.config != Bitmap.Config.ARGB_8888) {
                            val converted = it.copy(Bitmap.Config.ARGB_8888, false)
                            it.recycle()
                            converted
                        } else {
                            it
                        }
                    }
                }

                if (sourceBitmap == null) {
                    logger.trace(TAG, "Skipping frame $index: null bitmap")
                    return@forEachIndexed
                }

                val normalizedTimestampMs = (timestampUs - firstTimestampUs).coerceAtLeast(0L) / 1000L

                // Detect circles in this frame
                val circles = detectionService.detectCircles(sourceBitmap)

                if (circles.isNotEmpty()) {
                    // Find the nearest circle to the previously tracked position
                    val nearestCircle = findNearestCircle(
                        circles = circles,
                        prevX = prevPixelX,
                        prevY = prevPixelY,
                        prevRadius = prevPixelRadius,
                        maxTrackingDistance = maxTrackingDistance,
                        frameWidth = frameWidth,
                        frameHeight = frameHeight
                    )

                    if (nearestCircle != null) {
                        // Update tracking position (with smoothing toward detection)
                        prevPixelX = prevPixelX * 0.5f + nearestCircle.x * 0.5f
                        prevPixelY = prevPixelY * 0.5f + nearestCircle.y * 0.5f
                        prevPixelRadius = prevPixelRadius * 0.5f + nearestCircle.radius * 0.5f

                        results += BarbellFrameEntity(
                            metahistoryId = metahistoryId,
                            timestampMs = normalizedTimestampMs,
                            x = prevPixelX / frameWidth,
                            y = prevPixelY / frameHeight,
                            radius = prevPixelRadius / frameWidth.coerceAtMost(frameHeight),
                            confidence = nearestCircle.confidence
                        )
                    } else {
                        // No good match found — use predicted position (smoothed)
                        results += BarbellFrameEntity(
                            metahistoryId = metahistoryId,
                            timestampMs = normalizedTimestampMs,
                            x = prevPixelX / frameWidth,
                            y = prevPixelY / frameHeight,
                            radius = prevPixelRadius / frameWidth.coerceAtMost(frameHeight),
                            confidence = 0.5f
                        )
                    }
                } else {
                    // No circles detected — use predicted position
                    results += BarbellFrameEntity(
                        metahistoryId = metahistoryId,
                        timestampMs = normalizedTimestampMs,
                        x = prevPixelX / frameWidth,
                        y = prevPixelY / frameHeight,
                        radius = prevPixelRadius / frameWidth.coerceAtMost(frameHeight),
                        confidence = 0.3f
                    )
                }

                // Recycle non-first frames
                if (index != 0) {
                    sourceBitmap.recycle()
                }

                // Report progress periodically
                if (index % 10 == 0 || index == timestampsUs.lastIndex) {
                    onProgress((index * 100) / timestampsUs.size)
                }
            }
        } finally {
            // sampleFrame (index 0) should not be recycled until the end
            if (!sampleFrame.isRecycled) {
                sampleFrame.recycle()
            }
            retriever.release()
        }

        logger.info(TAG, "Tracked ${results.size} barbell positions across ${timestampsUs.size} frames")
        return results
    }

    /**
     * Hybrid barbell tracking that tries line detection first, then falls back to circle tracking.
     *
     * For each frame:
     * 1. Extract bitmap + pose landmarks (if poseDetectionService available)
     * 2. Try detectBarbellLine() with pose landmarks
     * 3. If line found, track by center proximity + angle similarity
     * 4. If line fails, fall back to circle detection and tracking
     * 5. Store x2/y2 for line detections, null for circle detections
     *
     * @param videoFile The video file to process
     * @param initialX Initial normalized x position (or line center x)
     * @param initialY Initial normalized y position (or line center y)
     * @param initialRadius Initial normalized radius (or line length as "radius")
     * @param initialX2 Initial normalized x2 for line detection (null for circle)
     * @param initialY2 Initial normalized y2 for line detection (null for circle)
     * @param metahistoryId The metahistory ID for the resulting entities
     * @param detectionService The barbell detection service (with hybrid support)
     * @param poseDetectionService Pose detection service for per-frame landmarks (nullable)
     * @param onProgress Progress callback (0..100)
     * @return List of BarbellFrameEntity with normalized coordinates ready for persistence
     */
    fun trackBarbellHybrid(
        videoFile: File,
        initialX: Float,
        initialY: Float,
        initialRadius: Float,
        initialX2: Float?,
        initialY2: Float?,
        metahistoryId: Int,
        detectionService: BarbellDetectionService,
        poseDetectionService: PoseDetectionService?,
        onProgress: (Int) -> Unit
    ): List<BarbellFrameEntity> {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(videoFile.absolutePath)

        val sampleFrame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: run {
                retriever.release()
                logger.warn(TAG, "Could not decode first frame for hybrid tracking")
                return emptyList()
            }
        val frameWidth = sampleFrame.width.toFloat()
        val frameHeight = sampleFrame.height.toFloat()
        val frameDiagonal = sqrt((frameWidth * frameWidth + frameHeight * frameHeight))
        val maxTrackingDistance = frameDiagonal * MAX_JUMP_FRAC

        val timestampsUs = readFrameTimestamps(videoFile)
        if (timestampsUs.isEmpty()) {
            sampleFrame.recycle()
            retriever.release()
            logger.warn(TAG, "No frame timestamps found")
            return emptyList()
        }

        val firstTimestampUs = timestampsUs.first()
        val results = mutableListOf<BarbellFrameEntity>()

        val isLineTracking = initialX2 != null && initialY2 != null

        // Initialize tracking state in pixel coordinates
        var prevPixelX = initialX * frameWidth
        var prevPixelY = initialY * frameHeight
        var prevPixelRadius = initialRadius * frameWidth.coerceAtMost(frameHeight)

        var prevPixelX2 = initialX2?.let { it * frameWidth }
        var prevPixelY2 = initialY2?.let { it * frameHeight }
        var prevAngle = if (prevPixelX2 != null && prevPixelY2 != null) {
            val dx = prevPixelX2 - prevPixelX
            val dy = prevPixelY2 - prevPixelY
            atan2(dy.toDouble(), dx.toDouble())
        } else {
            0.0
        }

        val historyX = mutableListOf<Float>()
        val historyY = mutableListOf<Float>()
        var lastGoodPixelX = prevPixelX
        var lastGoodPixelY = prevPixelY
        var consecutiveFailures = 0

        try {
            timestampsUs.forEachIndexed { index, timestampUs ->
                val sourceBitmap: Bitmap? = if (index == 0) {
                    sampleFrame
                } else {
                    retriever.getFrameAtTime(
                        timestampUs.coerceAtLeast(0L),
                        MediaMetadataRetriever.OPTION_CLOSEST
                    )?.also {
                        if (it.config != Bitmap.Config.ARGB_8888) {
                            val converted = it.copy(Bitmap.Config.ARGB_8888, false)
                            it.recycle()
                            converted
                        } else {
                            it
                        }
                    }
                }

                if (sourceBitmap == null) {
                    logger.trace(TAG, "Skipping frame $index: null bitmap")
                    return@forEachIndexed
                }

                val normalizedTimestampMs = (timestampUs - firstTimestampUs).coerceAtLeast(0L) / 1000L

                // Compute smoothed position from history buffer (temporal smoothing)
                val outputX = if (historyX.isNotEmpty()) historyX.average().toFloat() else prevPixelX
                val outputY = if (historyY.isNotEmpty()) historyY.average().toFloat() else prevPixelY

                // Get pose landmarks for this frame if available
                val handLandmarks: Map<Int, PoseOverlayLandmark>? = if (poseDetectionService != null) {
                    try {
                        val result = poseDetectionService.detectAndDrawPose(
                            sourceBitmap,
                            DrawingOptions(drawLandmarks = false, drawAngles = false)
                        )
                        result.landmarkPositions.ifEmpty { null }
                    } catch (_: Exception) {
                        null
                    }
                } else null

                // Try hybrid detection
                val detectionResult = detectionService.detectBarbellHybrid(sourceBitmap, handLandmarks)

                if (detectionResult != null) {
                    when (detectionResult) {
                        is BarbellDetectionResult.Line -> {
                            val line = detectionResult.line
                            // Match by center proximity + angle similarity
                            val newCenterX = line.centerX
                            val newCenterY = line.centerY
                            val newX2 = line.x2
                            val newY2 = line.y2
                            val newAngle = Math.toRadians(line.angle.toDouble())

                            val centerDist = distance(prevPixelX, prevPixelY, newCenterX, newCenterY)

                            if (centerDist <= maxTrackingDistance * 1.5f) {
                                // Smooth toward detection
                                val smoothFactor = 0.5f
                                prevPixelX = prevPixelX * (1 - smoothFactor) + newCenterX * smoothFactor
                                prevPixelY = prevPixelY * (1 - smoothFactor) + newCenterY * smoothFactor

                                if (prevPixelX2 != null && prevPixelY2 != null) {
                                    prevPixelX2 = prevPixelX2 * (1 - smoothFactor) + newX2 * smoothFactor
                                    prevPixelY2 = prevPixelY2 * (1 - smoothFactor) + newY2 * smoothFactor
                                } else {
                                    prevPixelX2 = newX2
                                    prevPixelY2 = newY2
                                }

                                prevPixelRadius = prevPixelRadius * (1 - smoothFactor) + line.length * smoothFactor
                                prevAngle = prevAngle * (1 - smoothFactor) + newAngle * smoothFactor

                                historyX.add(prevPixelX)
                                historyY.add(prevPixelY)
                                if (historyX.size > 8) {
                                    historyX.removeAt(0)
                                    historyY.removeAt(0)
                                }
                                lastGoodPixelX = prevPixelX
                                lastGoodPixelY = prevPixelY
                                consecutiveFailures = 0

                                results += BarbellFrameEntity(
                                    metahistoryId = metahistoryId,
                                    timestampMs = normalizedTimestampMs,
                                    x = outputX / frameWidth,
                                    y = outputY / frameHeight,
                                    radius = prevPixelRadius / frameWidth.coerceAtMost(frameHeight),
                                    confidence = line.confidence,
                                    x2 = prevPixelX2?.let { it / frameWidth },
                                    y2 = prevPixelY2?.let { it / frameHeight }
                                )
                            } else {
                                results += BarbellFrameEntity(
                                    metahistoryId = metahistoryId,
                                    timestampMs = normalizedTimestampMs,
                                    x = outputX / frameWidth,
                                    y = outputY / frameHeight,
                                    radius = prevPixelRadius / frameWidth.coerceAtMost(frameHeight),
                                    confidence = 0.5f,
                                    x2 = prevPixelX2?.let { it / frameWidth },
                                    y2 = prevPixelY2?.let { it / frameHeight }
                                )
                            }
                        }
                        is BarbellDetectionResult.Circle -> {
                            val circle = detectionResult.circle
                            val circleDist = distance(prevPixelX, prevPixelY, circle.x, circle.y)

                            if (circleDist <= maxTrackingDistance) {
                                val radiusDiff = abs(circle.radius - prevPixelRadius) / prevPixelRadius.coerceAtLeast(1f)
                                if (radiusDiff <= RADIUS_TOLERANCE) {
                                    prevPixelX = prevPixelX * 0.5f + circle.x * 0.5f
                                    prevPixelY = prevPixelY * 0.5f + circle.y * 0.5f
                                    prevPixelRadius = prevPixelRadius * 0.5f + circle.radius * 0.5f
                                    prevPixelX2 = null
                                    prevPixelY2 = null

                                    historyX.add(prevPixelX)
                                    historyY.add(prevPixelY)
                                    if (historyX.size > 8) {
                                        historyX.removeAt(0)
                                        historyY.removeAt(0)
                                    }
                                    lastGoodPixelX = prevPixelX
                                    lastGoodPixelY = prevPixelY
                                    consecutiveFailures = 0

                                    results += BarbellFrameEntity(
                                        metahistoryId = metahistoryId,
                                        timestampMs = normalizedTimestampMs,
                                        x = outputX / frameWidth,
                                        y = outputY / frameHeight,
                                        radius = prevPixelRadius / frameWidth.coerceAtMost(frameHeight),
                                        confidence = circle.confidence,
                                        x2 = null,
                                        y2 = null
                                    )
                                } else {
                                    results += BarbellFrameEntity(
                                        metahistoryId = metahistoryId,
                                        timestampMs = normalizedTimestampMs,
                                        x = outputX / frameWidth,
                                        y = outputY / frameHeight,
                                        radius = prevPixelRadius / frameWidth.coerceAtMost(frameHeight),
                                        confidence = 0.5f
                                    )
                                }
                            } else {
                                results += BarbellFrameEntity(
                                    metahistoryId = metahistoryId,
                                    timestampMs = normalizedTimestampMs,
                                    x = outputX / frameWidth,
                                    y = outputY / frameHeight,
                                    radius = prevPixelRadius / frameWidth.coerceAtMost(frameHeight),
                                    confidence = 0.5f
                                )
                            }
                        }
                    }
                } else {
                    // No detection — use last good position to prevent drift
                    consecutiveFailures++
                    if (consecutiveFailures <= 15) {
                        prevPixelX = lastGoodPixelX
                        prevPixelY = lastGoodPixelY
                    }
                    // If > 15 failures, keep using prevPixelX/Y (which hasn't drifted much)
                    results += BarbellFrameEntity(
                        metahistoryId = metahistoryId,
                        timestampMs = normalizedTimestampMs,
                        x = outputX / frameWidth,
                        y = outputY / frameHeight,
                        radius = prevPixelRadius / frameWidth.coerceAtMost(frameHeight),
                        confidence = 0.3f,
                        x2 = prevPixelX2?.let { it / frameWidth },
                        y2 = prevPixelY2?.let { it / frameHeight }
                    )
                }

                if (index != 0) {
                    sourceBitmap.recycle()
                }

                if (index % 10 == 0 || index == timestampsUs.lastIndex) {
                    onProgress((index * 100) / timestampsUs.size)
                }
            }
        } finally {
            if (!sampleFrame.isRecycled) {
                sampleFrame.recycle()
            }
            retriever.release()
        }

        logger.info(TAG, "Hybrid tracked ${results.size} barbell positions across ${timestampsUs.size} frames (lineTracking=$isLineTracking)")
        return results
    }

    /**
     * Find the circle nearest to the previously tracked position, considering both
     * position proximity and radius similarity.
     */
    private fun findNearestCircle(
        circles: List<DetectedCircle>,
        prevX: Float,
        prevY: Float,
        prevRadius: Float,
        maxTrackingDistance: Float,
        frameWidth: Float,
        frameHeight: Float
    ): DetectedCircle? {
        if (circles.isEmpty()) return null

        var bestCircle: DetectedCircle? = null
        var bestScore = Float.MAX_VALUE

        for (circle in circles) {
            val dist = distance(prevX, prevY, circle.x, circle.y)

            // Hard limit on tracking distance in pixel space
            if (dist > maxTrackingDistance) continue

            // Radius similarity check
            val radiusDiff = abs(circle.radius - prevRadius) / prevRadius.coerceAtLeast(1f)
            if (radiusDiff > RADIUS_TOLERANCE) continue

            // Combined score: distance weighted more heavily than radius difference
            val score = dist + radiusDiff * 50f

            if (score < bestScore) {
                bestScore = score
                bestCircle = circle
            }
        }

        return bestCircle
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun readFrameTimestamps(videoFile: File): List<Long> {
        val extractor = android.media.MediaExtractor()
        try {
            extractor.setDataSource(videoFile.absolutePath)
            val videoTrackIndex = selectVideoTrack(extractor) ?: return emptyList()
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

    private fun selectVideoTrack(extractor: android.media.MediaExtractor): Int? {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) return index
        }
        return null
    }

    companion object {
        private const val TAG = "BarbellTrackingService"

        /** Maximum jump as fraction of frame diagonal (12% — more generous than Python's 8%) */
        private const val MAX_JUMP_FRAC = 0.12f

        /** Maximum fractional difference in radius for matching */
        private const val RADIUS_TOLERANCE = 0.5f
    }
}
