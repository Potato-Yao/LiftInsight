package com.potato.liftinsight.video

import com.potato.liftinsight.training.data.TimeseriesPoint
import kotlin.math.abs

/**
 * Detects repetition split timestamps from angle timeseries curves by
 * porting the split-detection logic from tools/main.py (analyze_curve_splits,
 * consensus_split_times).
 *
 * Reuses [RdpSimplifier.simplify] for RDP smoothing; does not reimplement RDP.
 */
object RepSplitDetector {

    data class CurveSplitResult(
        val splitTimesMs: List<Long>,
        val score: Double,
        val kind: String,
        val level: Double,
        val levelStd: Double,
        val angleRange: Double,
        val intervalCv: Double,
        val count: Int,
        val name: String
    )

    data class ConsensusResult(
        val splitTimesMs: List<Long>,
        val selectedCurves: List<CurveSplitResult>
    )

    /**
     * Main entry point: detect rep splits from multiple angle timeseries.
     *
     * @param timeseries Map of metric name → sorted points (by timestampMs).
     * @param rdpEpsilon RDP simplification tolerance in degrees.
     * @param levelTolerance Maximum angle gap (degrees) to group extrema into the same level.
     * @param minGapMs Minimum gap in ms between consecutive split timestamps.
     * @param maxCurves Maximum number of top-scoring curves considered for consensus.
     * @param mergeWindowMs Window (ms) for merging split times from different curves.
     * @return Sorted list of split timestamps in ms, or empty if detection fails.
     */
    fun detectRepSplits(
        timeseries: Map<String, List<TimeseriesPoint>>,
        rdpEpsilon: Double = 1.5,
        levelTolerance: Double = 8.0,
        minGapMs: Long = 600L,
        maxCurves: Int = 3,
        mergeWindowMs: Long = 500L
    ): List<Long> {
        val curveResults = mutableListOf<CurveSplitResult>()

        for ((name, points) in timeseries) {
            if (points.size < 3) continue

            val simplified = RdpSimplifier.simplify(points, rdpEpsilon)
            val result = analyzeCurveSplits(
                simplified = simplified,
                levelTolerance = levelTolerance,
                minGapMs = minGapMs,
                name = name
            )
            if (result != null) {
                curveResults.add(result)
            }
        }

        if (curveResults.isEmpty()) return emptyList()

        val consensus = consensusSplitTimes(
            curveResults = curveResults,
            maxCurves = maxCurves,
            mergeWindowMs = mergeWindowMs,
            minGapMs = minGapMs
        )

        return consensus.splitTimesMs.sorted()
    }

    /**
     * Analyze a single simplified curve to detect candidate split timestamps
     * based on grouped extrema.
     */
    internal fun analyzeCurveSplits(
        simplified: List<TimeseriesPoint>,
        levelTolerance: Double = 8.0,
        minGapMs: Long = 600L,
        name: String = "unknown"
    ): CurveSplitResult? {
        if (simplified.size < 3) return null

        // Detect local extrema
        val minima = mutableListOf<TimeseriesPoint>()
        val maxima = mutableListOf<TimeseriesPoint>()

        for (i in 1 until simplified.size - 1) {
            val prev = simplified[i - 1].value
            val curr = simplified[i].value
            val next = simplified[i + 1].value

            if (curr <= prev && curr <= next) {
                minima.add(simplified[i])
            }
            if (curr >= prev && curr >= next) {
                maxima.add(simplified[i])
            }
        }

        // Group extrema by angle level
        val minGroups = groupPointsByLevel(minima, tolerance = levelTolerance, minPoints = 2)
        val maxGroups = groupPointsByLevel(maxima, tolerance = levelTolerance, minPoints = 2)

        // Pick best candidate from minima and maxima
        val candidates = mutableListOf<LevelGroup>()

        if (minGroups.isNotEmpty()) {
            // For minima, prefer the group with the lowest mean value
            val bestMin = minGroups.minByOrNull { it.meanValue }!!
            candidates.add(bestMin.copy(kind = "min"))
        }
        if (maxGroups.isNotEmpty()) {
            // For maxima, prefer the group with the highest mean value
            val bestMax = maxGroups.maxByOrNull { it.meanValue }!!
            candidates.add(bestMax.copy(kind = "max"))
        }

        if (candidates.isEmpty()) return null

        // Clean candidates: filter points by min time gap and recalculate stats
        val cleanedCandidates = candidates.mapNotNull { group ->
            val cleanPoints = filterPointsByMinGap(group.points, minGapMs)
            if (cleanPoints.size < 2) return@mapNotNull null
            group.copy(
                points = cleanPoints,
                count = cleanPoints.size,
                xMin = cleanPoints.minOf { it.timestampMs },
                xMax = cleanPoints.maxOf { it.timestampMs }
            )
        }

        if (cleanedCandidates.isEmpty()) return null

        // Select the better candidate: primary by count, secondary by time span
        val selected = cleanedCandidates.maxByOrNull { group: LevelGroup ->
            group.count.toLong() * 1_000_000L + (group.xMax - group.xMin)
        } ?: return null

        // Split times from already-cleaned points
        val splitTimesMs = selected.points.map { it.timestampMs }

        if (splitTimesMs.size < 2) return null

        // Score the detection
        val intervals = splitTimesMs.zipWithNext { a, b -> (b - a).toDouble() }
        val intervalMean = if (intervals.isNotEmpty()) intervals.average() else 0.0
        val intervalStd = if (intervals.size >= 2) {
            kotlin.math.sqrt(intervals.map { (it - intervalMean) * (it - intervalMean) }.average())
        } else 0.0
        val intervalCv = if (intervalMean > 1e-6) intervalStd / intervalMean else 1.0

        val selectedValues = selected.points.map { it.value }
        val levelStd = if (selectedValues.size >= 2) {
            val mean = selectedValues.average()
            kotlin.math.sqrt(selectedValues.map { (it - mean) * (it - mean) }.average())
        } else levelTolerance

        val allValues = simplified.map { it.value }.sorted()
        val angleRange = if (allValues.size >= 2) {
            val p5 = allValues[((allValues.size - 1) * 0.05).toInt().coerceIn(0, allValues.size - 1)]
            val p95 = allValues[((allValues.size - 1) * 0.95).toInt().coerceIn(0, allValues.size - 1)]
            p95 - p5
        } else 0.0

        val countScore = (splitTimesMs.size / 5.0).coerceAtMost(1.0)
        val regularityScore = 1.0 / (1.0 + intervalCv)
        val levelScore = 1.0 / (1.0 + levelStd / levelTolerance.coerceAtLeast(1e-6))
        val amplitudeScore = (angleRange / 60.0).coerceAtMost(1.0)
        val score = countScore * 0.25 + regularityScore * 0.35 + levelScore * 0.25 + amplitudeScore * 0.15

        return CurveSplitResult(
            splitTimesMs = splitTimesMs,
            score = score,
            kind = selected.kind,
            level = selected.meanValue,
            levelStd = levelStd,
            angleRange = angleRange,
            intervalCv = intervalCv,
            count = splitTimesMs.size,
            name = name
        )
    }

    /**
     * Group points by nearby angle values using a tolerance-based clustering.
     * Points are sorted by value, then consecutive points within [tolerance] are grouped.
     */
    internal data class LevelGroup(
        val meanValue: Double,
        val xMin: Long,
        val xMax: Long,
        val count: Int,
        val points: List<TimeseriesPoint>,
        val kind: String = ""
    )

    internal fun groupPointsByLevel(
        points: List<TimeseriesPoint>,
        tolerance: Double,
        minPoints: Int
    ): List<LevelGroup> {
        if (points.size < minPoints) return emptyList()

        // Sort by value for grouping
        val sorted = points.sortedBy { it.value }
        val groups = mutableListOf<MutableList<TimeseriesPoint>>()
        groups.add(mutableListOf(sorted[0]))

        for (i in 1 until sorted.size) {
            val current = sorted[i]
            val lastGroupMean = groups.last().map { it.value }.average()
            if (abs(current.value - lastGroupMean) <= tolerance) {
                groups.last().add(current)
            } else {
                groups.add(mutableListOf(current))
            }
        }

        return groups
            .filter { it.size >= minPoints }
            .map { group ->
                val times = group.map { it.timestampMs }
                val values = group.map { it.value }
                LevelGroup(
                    meanValue = values.average(),
                    xMin = times.min(),
                    xMax = times.max(),
                    count = group.size,
                    points = group.sortedBy { it.timestampMs }
                )
            }
    }

    /**
     * Filter a list of timeseries points by minimum gap between consecutive timestamps.
     * Returns points sorted by timestamp, keeping the first of any too-close pair.
     * Ported from Python filter_points_by_time_gap.
     */
    internal fun filterPointsByMinGap(
        points: List<TimeseriesPoint>,
        minGapMs: Long
    ): List<TimeseriesPoint> {
        val filtered = mutableListOf<TimeseriesPoint>()
        for (point in points.sortedBy { it.timestampMs }) {
            if (filtered.isEmpty() || point.timestampMs - filtered.last().timestampMs >= minGapMs) {
                filtered.add(point)
            }
        }
        return filtered
    }

    /**
     * Filter a list of timestamps by minimum gap between consecutive entries.
     * Returns timestamps in ascending order, keeping the first of any too-close pair.
     */
    internal fun filterByMinGap(times: List<Long>, minGapMs: Long): List<Long> {
        val filtered = mutableListOf<Long>()
        for (t in times.sorted()) {
            if (filtered.isEmpty() || t - filtered.last() >= minGapMs) {
                filtered.add(t)
            }
        }
        return filtered
    }

    /**
     * Build consensus split times from multiple curve results.
     * Ported from the Python consensus_split_times.
     */
    internal fun consensusSplitTimes(
        curveResults: List<CurveSplitResult>,
        maxCurves: Int = 3,
        mergeWindowMs: Long = 500L,
        minGapMs: Long = 600L
    ): ConsensusResult {
        val valid = curveResults.filter { it.splitTimesMs.isNotEmpty() }
        if (valid.isEmpty()) return ConsensusResult(emptyList(), emptyList())

        val selected = valid
            .sortedByDescending { it.score }
            .take(maxCurves)

        // Collect all events: (splitTime, score, curveName)
        data class Event(val timeMs: Long, val score: Double, val name: String)

        val events = selected.flatMap { curve ->
            curve.splitTimesMs.map { time -> Event(time, curve.score, curve.name) }
        }.sortedBy { it.timeMs }

        if (events.isEmpty()) return ConsensusResult(emptyList(), selected)

        // Cluster events within mergeWindowMs
        val clusters = mutableListOf<MutableList<Event>>()
        for (event in events) {
            if (clusters.isEmpty() || event.timeMs - clusters.last().last().timeMs > mergeWindowMs) {
                clusters.add(mutableListOf(event))
            } else {
                clusters.last().add(event)
            }
        }

        // Minimum votes: 1 if only 1 curve, 2 otherwise
        val minVotes = if (selected.size == 1) 1 else 2

        val consensus = clusters
            .filter { cluster ->
                cluster.map { it.name }.distinct().size >= minVotes
            }
            .map { cluster ->
                val totalWeight = cluster.sumOf { it.score }
                if (totalWeight > 0.0) {
                    val weightedSum = cluster.sumOf { it.timeMs.toDouble() * it.score }
                    (weightedSum / totalWeight).toLong()
                } else {
                    cluster.map { it.timeMs }.average().toLong()
                }
            }

        val filtered = if (consensus.isNotEmpty()) {
            filterByMinGap(consensus, minGapMs)
        } else if (selected.isNotEmpty()) {
            filterByMinGap(selected.first().splitTimesMs, minGapMs)
        } else {
            emptyList()
        }

        return ConsensusResult(
            splitTimesMs = filtered,
            selectedCurves = selected
        )
    }
}
