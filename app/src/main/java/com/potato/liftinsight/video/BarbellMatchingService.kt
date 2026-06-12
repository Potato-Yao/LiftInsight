package com.potato.liftinsight.video

import com.google.mlkit.vision.pose.PoseLandmark
import com.potato.liftinsight.common.logging.AppLogger

internal data class FrameCircleData(
    val timestampMs: Long,
    val circles: List<DetectedCircle>
)

internal data class FramePoseData(
    val timestampMs: Long,
    val landmarks: Map<Int, PoseOverlayLandmark>
)

internal data class MatchedBarbellPosition(
    val timestampMs: Long,
    val x: Float,
    val y: Float,
    val radius: Float,
    val confidence: Float
)

internal class BarbellMatchingService(
    private val logger: AppLogger
) {

    fun matchBarbellTrajectory(
        frameCircleData: List<FrameCircleData>,
        framePoseData: List<FramePoseData>
    ): List<MatchedBarbellPosition> {
        if (frameCircleData.isEmpty()) {
            logger.debug(TAG, "No circle data to match")
            return emptyList()
        }

        // Build a map of pose data by timestamp for fast lookup
        val poseByTimestamp = framePoseData.associateBy { it.timestampMs }

        // For each frame with circles, find the circle closest to the hand center
        val frameMatches = mutableListOf<Pair<Long, DetectedCircle?>>()

        for (frameData in frameCircleData) {
            val poseData = poseByTimestamp[frameData.timestampMs]
            val handCenter = poseData?.let { getHandCenter(it.landmarks) }

            if (handCenter == null) {
                // No hand landmarks visible — skip this frame
                frameMatches += Pair(frameData.timestampMs, null)
                continue
            }

            if (frameData.circles.isEmpty()) {
                frameMatches += Pair(frameData.timestampMs, null)
                continue
            }

            // Find the circle closest to the hand center
            val nearestCircle = frameData.circles.minByOrNull { circle ->
                distance(circle.x, circle.y, handCenter.first, handCenter.second)
            }

            frameMatches += Pair(frameData.timestampMs, nearestCircle)
        }

        // Filter out frames with no match
        val matchedPositions = frameMatches
            .filter { it.second != null }
            .map { (timestamp, circle) ->
                MatchedBarbellPosition(
                    timestampMs = timestamp,
                    x = circle!!.x,
                    y = circle.y,
                    radius = circle.radius,
                    confidence = circle.confidence
                )
            }

        logger.info(TAG, "Matched ${matchedPositions.size} barbell positions out of ${frameCircleData.size} frames")

        // Apply temporal smoothing: remove outliers
        return smoothTrajectory(matchedPositions)
    }

    private fun getHandCenter(landmarks: Map<Int, PoseOverlayLandmark>): Pair<Float, Float>? {
        val handLandmarkTypes = listOf(
            PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_INDEX,
            PoseLandmark.RIGHT_INDEX,
            PoseLandmark.LEFT_THUMB,
            PoseLandmark.RIGHT_THUMB
        )

        var sumX = 0f
        var sumY = 0f
        var count = 0

        for (type in handLandmarkTypes) {
            val landmark = landmarks[type] ?: continue
            if (landmark.visibility < 0.5f) continue
            sumX += landmark.x
            sumY += landmark.y
            count++
        }

        if (count == 0) return null
        return Pair(sumX / count, sumY / count)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun smoothTrajectory(positions: List<MatchedBarbellPosition>): List<MatchedBarbellPosition> {
        if (positions.size <= 2) return positions

        val smoothed = mutableListOf<MatchedBarbellPosition>()

        for (i in positions.indices) {
            val windowStart = (i - SMOOTH_WINDOW).coerceAtLeast(0)
            val windowEnd = (i + SMOOTH_WINDOW).coerceAtMost(positions.size - 1)
            val window = positions.subList(windowStart, windowEnd + 1)

            val avgX = window.map { it.x }.average().toFloat()
            val avgY = window.map { it.y }.average().toFloat()
            val avgRadius = window.map { it.radius }.average().toFloat()
            val avgConfidence = window.map { it.confidence }.average().toFloat()

            smoothed += MatchedBarbellPosition(
                timestampMs = positions[i].timestampMs,
                x = avgX,
                y = avgY,
                radius = avgRadius,
                confidence = avgConfidence
            )
        }

        return smoothed
    }

    companion object {
        private const val TAG = "BarbellMatchingService"
        private const val SMOOTH_WINDOW = 2 // ±2 frames for moving average
    }
}
