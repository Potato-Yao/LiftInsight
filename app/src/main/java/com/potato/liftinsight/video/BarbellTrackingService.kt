package com.potato.liftinsight.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.training.data.BarbellFrameEntity
import java.io.File
import kotlin.math.abs
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
                        frameWidth = frameWidth,
                        frameHeight = frameHeight
                    )

                    if (nearestCircle != null) {
                        // Update tracking position (with smoothing toward detection)
                        prevPixelX = prevPixelX * 0.7f + nearestCircle.x * 0.3f
                        prevPixelY = prevPixelY * 0.7f + nearestCircle.y * 0.3f
                        prevPixelRadius = prevPixelRadius * 0.7f + nearestCircle.radius * 0.3f

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
     * Find the circle nearest to the previously tracked position, considering both
     * position proximity and radius similarity.
     */
    private fun findNearestCircle(
        circles: List<DetectedCircle>,
        prevX: Float,
        prevY: Float,
        prevRadius: Float,
        frameWidth: Float,
        frameHeight: Float
    ): DetectedCircle? {
        if (circles.isEmpty()) return null

        var bestCircle: DetectedCircle? = null
        var bestScore = Float.MAX_VALUE

        for (circle in circles) {
            val dist = distance(prevX, prevY, circle.x, circle.y)

            // Hard limit on tracking distance in pixel space
            if (dist > MAX_TRACKING_DISTANCE) continue

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

        /** Maximum pixel distance to consider a circle a candidate for tracking continuity */
        private const val MAX_TRACKING_DISTANCE = 150f

        /** Maximum fractional difference in radius for matching */
        private const val RADIUS_TOLERANCE = 0.5f
    }
}
