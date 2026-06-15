package com.potato.liftinsight.video

import com.potato.liftinsight.training.data.TimeseriesPoint
import kotlin.math.abs
import kotlin.math.min

data class ActiveRange(
    val startTimestampMs: Long,
    val endTimestampMs: Long
) {
    val durationMs: Long get() = endTimestampMs - startTimestampMs
}

data class ActiveRangeConfig(
    val smoothWindowSize: Int = 5,
    val energyWindowMs: Long = 1500,
    val energyThreshold: Double = 15.0,
    val bufferMs: Long = 300
)

object ActiveRangeDetector {

    /**
     * Detects the active motion range in an angle-time series.
     *
     * Returns null if:
     * - Less than 2 data points
     * - Video duration less than 2000ms
     * - No active motion detected (all-idle)
     */
    fun detect(points: List<TimeseriesPoint>, config: ActiveRangeConfig = ActiveRangeConfig()): ActiveRange? {
        require(config.smoothWindowSize >= 1) { "smoothWindowSize must be >= 1" }
        require(config.energyWindowMs > 0) { "energyWindowMs must be > 0" }
        require(config.energyThreshold >= 0) { "energyThreshold must be >= 0" }
        require(config.bufferMs >= 0) { "bufferMs must be >= 0" }

        if (points.size < 2) return null

        val videoDurationMs = points.last().timestampMs - points.first().timestampMs
        if (videoDurationMs < 2000) return null

        // Step 2: Smooth using moving average
        val smoothedPoints = smooth(points, config.smoothWindowSize)

        if (smoothedPoints.size < 2) return null

        // Step 3: Compute motion magnitudes: abs(valueDiff) / timeDiffSeconds
        val motionMagnitudes = mutableListOf<Pair<Long, Double>>()
        for (i in 1 until smoothedPoints.size) {
            val timeDiffSeconds = (smoothedPoints[i].timestampMs - smoothedPoints[i - 1].timestampMs) / 1000.0
            if (timeDiffSeconds <= 0.0) continue
            val valueDiff = abs(smoothedPoints[i].value - smoothedPoints[i - 1].value)
            val magnitude = valueDiff / timeDiffSeconds
            motionMagnitudes.add(Pair(smoothedPoints[i].timestampMs, magnitude))
        }

        if (motionMagnitudes.isEmpty()) return null

        // Step 4: Compute sliding window energy: average motion within energyWindowMs range
        val energies = mutableListOf<Pair<Long, Double>>()
        for ((timestamp, _) in motionMagnitudes) {
            val windowStart = timestamp - config.energyWindowMs
            val windowEnd = timestamp + config.energyWindowMs
            var sum = 0.0
            var count = 0
            for ((t, mag) in motionMagnitudes) {
                if (t >= windowStart && t <= windowEnd) {
                    sum += mag
                    count++
                }
            }
            if (count > 0) {
                energies.add(Pair(timestamp, sum / count))
            }
        }

        if (energies.isEmpty()) return null

        // Step 5-6: Classify each energy value as active (>= threshold), find first/last active
        var firstActiveMs: Long? = null
        var lastActiveMs: Long? = null
        for ((timestamp, energy) in energies) {
            if (energy >= config.energyThreshold) {
                if (firstActiveMs == null) firstActiveMs = timestamp
                lastActiveMs = timestamp
            }
        }

        if (firstActiveMs == null || lastActiveMs == null) return null

        // Step 8: Expand by buffer
        val startMs = maxOf(points.first().timestampMs, firstActiveMs - config.bufferMs)
        val endMs = min(points.last().timestampMs, lastActiveMs + config.bufferMs)

        return ActiveRange(startTimestampMs = startMs, endTimestampMs = endMs)
    }

    private fun smooth(points: List<TimeseriesPoint>, windowSize: Int): List<TimeseriesPoint> {
        if (windowSize <= 1) return points

        val halfWindow = windowSize / 2
        return List(points.size) { i ->
            val start = maxOf(0, i - halfWindow)
            val end = min(points.size - 1, i + halfWindow)
            val values = (start..end).map { points[it].value }
            val avgValue = values.sum() / values.size
            TimeseriesPoint(timestampMs = points[i].timestampMs, value = avgValue)
        }
    }
}
