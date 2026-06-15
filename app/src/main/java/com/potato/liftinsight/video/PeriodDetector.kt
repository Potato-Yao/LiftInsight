package com.potato.liftinsight.video

import com.potato.liftinsight.training.data.TimeseriesPoint
import kotlin.math.abs

enum class PeriodBoundaryType { PEAK, TROUGH }

data class PeriodBoundary(
    val timestampMs: Long,
    val type: PeriodBoundaryType
)

data class PeriodDetectorConfig(
    val smoothWindowSize: Int = 5,
    val minPeakDistanceMs: Long = 300
)

object PeriodDetector {

    /**
     * Detects period boundaries (peaks and troughs) in an angle-time series.
     *
     * Algorithm:
     * 1. Filter each metric's points to only those within activeRange (if provided)
     * 2. Auto-select the metric with highest amplitude (max - min value) within active range
     * 3. If no metric has data, return emptyList()
     * 4. Smooth the selected metric using moving average (window = config.smoothWindowSize)
     * 5. Find peaks (value > both neighbors) and troughs (value < both neighbors)
     * 6. Filter out boundaries too close together (within minPeakDistanceMs) — keep the more extreme one
     * 7. Return sorted list by timestampMs
     *
     * @param angleTimeSeries Map of metric name to list of timeseries points
     * @param activeRange Optional active range to focus detection on
     * @param config Configuration for detection parameters
     * @return List of period boundaries sorted by timestamp
     */
    fun detect(
        angleTimeSeries: Map<String, List<TimeseriesPoint>>,
        activeRange: ActiveRange?,
        config: PeriodDetectorConfig = PeriodDetectorConfig()
    ): List<PeriodBoundary> {
        require(config.smoothWindowSize >= 1) { "smoothWindowSize must be >= 1" }
        require(config.minPeakDistanceMs >= 0) { "minPeakDistanceMs must be >= 0" }

        // Step 1: Filter points within active range
        val filteredSeries: Map<String, List<TimeseriesPoint>> = if (activeRange != null) {
            angleTimeSeries.mapValues { (_, points) ->
                points.filter { it.timestampMs in activeRange.startTimestampMs..activeRange.endTimestampMs }
            }
        } else {
            angleTimeSeries
        }

        // Filter out metrics with no data after range filtering
        val nonEmptySeries = filteredSeries.filterValues { it.isNotEmpty() }

        // Step 2: Auto-select the metric with highest amplitude (max - min value)
        val bestMetric = selectBestMetric(nonEmptySeries)
        if (bestMetric == null) return emptyList()

        val points = nonEmptySeries[bestMetric]!!

        // Step 3: Apply moving average smoothing
        val smoothed = smooth(points, config.smoothWindowSize)
        if (smoothed.size < 3) return emptyList()

        // Step 4: Find peaks and troughs
        val boundaries = findPeaksAndTroughs(smoothed)
        if (boundaries.isEmpty()) return emptyList()

        // Step 5: Filter out boundaries too close together, keeping the more extreme one
        val filtered = filterCloseBoundaries(boundaries, smoothed, config.minPeakDistanceMs)

        // Step 6: Return sorted by timestampMs
        return filtered.sortedBy { it.timestampMs }
    }

    internal fun selectBestMetric(
        nonEmptySeries: Map<String, List<TimeseriesPoint>>
    ): String? {
        if (nonEmptySeries.isEmpty()) return null

        return nonEmptySeries.maxByOrNull { (_, points) ->
            val minVal = points.minOf { it.value }
            val maxVal = points.maxOf { it.value }
            maxVal - minVal
        }?.key
    }

    internal fun smooth(
        points: List<TimeseriesPoint>,
        windowSize: Int
    ): List<TimeseriesPoint> {
        if (windowSize <= 1) return points

        val halfWindow = windowSize / 2
        return List(points.size) { i ->
            val start = maxOf(0, i - halfWindow)
            val end = minOf(points.size - 1, i + halfWindow)
            val values = (start..end).map { points[it].value }
            val avgValue = values.sum() / values.size
            TimeseriesPoint(timestampMs = points[i].timestampMs, value = avgValue)
        }
    }

    internal fun findPeaksAndTroughs(points: List<TimeseriesPoint>): List<PeriodBoundary> {
        if (points.size < 3) return emptyList()

        val boundaries = mutableListOf<PeriodBoundary>()
        for (i in 1 until points.size - 1) {
            val prev = points[i - 1].value
            val curr = points[i].value
            val next = points[i + 1].value

            if (curr > prev && curr > next) {
                boundaries.add(PeriodBoundary(timestampMs = points[i].timestampMs, type = PeriodBoundaryType.PEAK))
            } else if (curr < prev && curr < next) {
                boundaries.add(PeriodBoundary(timestampMs = points[i].timestampMs, type = PeriodBoundaryType.TROUGH))
            }
        }
        return boundaries
    }

    internal fun filterCloseBoundaries(
        boundaries: List<PeriodBoundary>,
        smoothed: List<TimeseriesPoint>,
        minDistanceMs: Long
    ): List<PeriodBoundary> {
        if (boundaries.size <= 1) return boundaries
        if (minDistanceMs <= 0) return boundaries

        // Build a map from timestamp to value for quick lookup
        val valueMap = smoothed.associate { it.timestampMs to it.value }

        val sorted = boundaries.sortedBy { it.timestampMs }
        val result = mutableListOf<PeriodBoundary>()
        result.add(sorted[0])

        for (i in 1 until sorted.size) {
            val prev = result.last()
            val curr = sorted[i]

            if (curr.timestampMs - prev.timestampMs >= minDistanceMs) {
                result.add(curr)
            } else {
                // Keep the more extreme one (farther from the overall mean)
                val prevValue = valueMap[prev.timestampMs] ?: 0.0
                val currValue = valueMap[curr.timestampMs] ?: 0.0

                val allValues = smoothed.map { it.value }
                val meanValue = allValues.sum() / allValues.size

                val prevExtreme = abs(prevValue - meanValue)
                val currExtreme = abs(currValue - meanValue)

                if (currExtreme > prevExtreme) {
                    result[result.size - 1] = curr
                }
            }
        }

        return result
    }
}
