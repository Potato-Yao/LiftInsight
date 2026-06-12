package com.potato.liftinsight.video

import com.potato.liftinsight.training.data.TimeseriesPoint
import kotlin.math.abs
import kotlin.math.sqrt

object RdpSimplifier {

    /**
     * Simplify a list of TimeseriesPoints using the Ramer-Douglas-Peucker algorithm.
     * Operates on (timestampMs, value) pairs where timestampMs is the X axis and value is the Y axis.
     *
     * @param points The input points, must be sorted by timestampMs ascending.
     * @param epsilon The maximum perpendicular distance tolerance. Points within this
     *                distance from the simplified line are removed. Higher values = more simplification.
     * @return A simplified list of TimeseriesPoints preserving endpoints.
     */
    fun simplify(points: List<TimeseriesPoint>, epsilon: Double): List<TimeseriesPoint> {
        if (points.size <= 2) return points
        if (epsilon <= 0.0) return points

        return rdpRecursive(points, epsilon)
    }

    private fun rdpRecursive(points: List<TimeseriesPoint>, epsilon: Double): List<TimeseriesPoint> {
        // Find the point with the maximum distance from the line between first and last
        val first = points.first()
        val last = points.last()

        var maxDist = 0.0
        var maxIndex = 0

        for (i in 1 until points.size - 1) {
            val dist = perpendicularDistance(points[i], first, last)
            if (dist > maxDist) {
                maxDist = dist
                maxIndex = i
            }
        }

        // If max distance is greater than epsilon, recursively simplify
        return if (maxDist > epsilon) {
            val left = rdpRecursive(points.subList(0, maxIndex + 1), epsilon)
            val right = rdpRecursive(points.subList(maxIndex, points.size), epsilon)

            // Combine results (drop last of left to avoid duplicate at junction)
            left.dropLast(1) + right
        } else {
            // All intermediate points are within epsilon — keep only endpoints
            listOf(first, last)
        }
    }

    /**
     * Calculate the perpendicular distance from a point to a line defined by two endpoints.
     * Uses the cross-product formula for point-to-line-segment distance.
     */
    internal fun perpendicularDistance(
        point: TimeseriesPoint,
        lineStart: TimeseriesPoint,
        lineEnd: TimeseriesPoint
    ): Double {
        val dx = (lineEnd.timestampMs - lineStart.timestampMs).toDouble()
        val dy = lineEnd.value - lineStart.value

        // If line start and end are the same point
        val lineLengthSq = dx * dx + dy * dy
        if (lineLengthSq == 0.0) {
            // Distance from point to lineStart
            val px = (point.timestampMs - lineStart.timestampMs).toDouble()
            val py = point.value - lineStart.value
            return sqrt(px * px + py * py)
        }

        // Cross product: |AP × AB| / |AB|
        // AP = point - lineStart, AB = lineEnd - lineStart
        val apx = (point.timestampMs - lineStart.timestampMs).toDouble()
        val apy = point.value - lineStart.value

        val crossProduct = abs(apx * dy - apy * dx)
        return crossProduct / sqrt(lineLengthSq)
    }

    /**
     * Find the value at a given timestamp by interpolating between the two nearest
     * simplified points that bracket the timestamp.
     *
     * @param simplifiedPoints The RDP-simplified points (sorted by timestampMs).
     * @param timestampMs The target timestamp.
     * @return The interpolated value, or null if interpolation is not possible.
     */
    fun interpolateValue(simplifiedPoints: List<TimeseriesPoint>, timestampMs: Long): Double? {
        if (simplifiedPoints.isEmpty()) return null
        if (simplifiedPoints.size == 1) return simplifiedPoints[0].value

        // Find the two points bracketing the timestamp
        // Binary search for insertion point
        var lo = 0
        var hi = simplifiedPoints.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (simplifiedPoints[mid].timestampMs < timestampMs) {
                lo = mid + 1
            } else {
                hi = mid
            }
        }

        val afterIndex = lo
        val beforeIndex = (afterIndex - 1).coerceAtLeast(0)

        val after = simplifiedPoints[afterIndex]
        val before = simplifiedPoints[beforeIndex]

        // If timestamp is exactly at a point
        if (before.timestampMs == timestampMs) return before.value
        if (after.timestampMs == timestampMs) return after.value

        // If timestamp is outside the range, clamp to nearest endpoint
        if (timestampMs <= before.timestampMs) return before.value
        if (timestampMs >= after.timestampMs) return after.value

        // Linear interpolation
        val t = (timestampMs - before.timestampMs).toDouble() / (after.timestampMs - before.timestampMs).toDouble()
        return before.value + t * (after.value - before.value)
    }
}
