package com.potato.liftinsight.video

import com.potato.liftinsight.training.data.TimeseriesPoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodDetectorTest {

    private val defaultIntervalMs = 33L // ~30 fps

    // === Test: empty map returns empty list ===

    @Test
    fun `empty map returns empty list`() {
        val result = PeriodDetector.detect(emptyMap(), activeRange = null)
        assertTrue(result.isEmpty())
    }

    // === Test: single metric with no peaks returns empty list ===

    @Test
    fun `single metric with no peaks returns empty list`() {
        // All values monotonically increasing — no peaks or troughs
        val points = (0..50).map { i ->
            TimeseriesPoint(timestampMs = i * defaultIntervalMs, value = 80.0 + i * 0.5)
        }
        val series = mapOf("spine_angle" to points)
        val result = PeriodDetector.detect(series, activeRange = null)
        assertTrue(result.isEmpty())
    }

    // === Test: sinusoidal signal detects correct number of peaks and troughs ===

    @Test
    fun `sinusoidal signal detects correct number of peaks and troughs`() {
        // Generate 5 full cycles of a sinusoid
        val totalDurationMs = 5000L
        val points = generateSignal(totalDurationMs) { tMs ->
            90.0 + sin(2.0 * PI * tMs / 1000.0) * 20.0 // 1 Hz sine wave, 5 cycles over 5s
        }
        val series = mapOf("spine_angle" to points)

        val config = PeriodDetectorConfig(smoothWindowSize = 5, minPeakDistanceMs = 200)
        val result = PeriodDetector.detect(series, activeRange = null, config)

        // A 1Hz sine over 5s should have ~5 peaks and ~5 troughs = 10 boundaries
        // But the very ends may or may not have a boundary (need neighbors), so expect 8-10
        assertTrue("Expected at least 8 boundaries, got ${result.size}", result.size >= 8)
        assertTrue("Expected at most 12 boundaries, got ${result.size}", result.size <= 12)

        // Count peaks and troughs
        val peaks = result.count { it.type == PeriodBoundaryType.PEAK }
        val troughs = result.count { it.type == PeriodBoundaryType.TROUGH }
        assertTrue("Expected at least 3 peaks, got $peaks", peaks >= 3)
        assertTrue("Expected at least 3 troughs, got $troughs", troughs >= 3)
    }

    // === Test: peaks and troughs alternate in result ===

    @Test
    fun `peaks and troughs alternate in result`() {
        val totalDurationMs = 5000L
        val points = generateSignal(totalDurationMs) { tMs ->
            90.0 + sin(2.0 * PI * tMs / 1000.0) * 20.0
        }
        val series = mapOf("spine_angle" to points)

        val config = PeriodDetectorConfig(smoothWindowSize = 5, minPeakDistanceMs = 200)
        val result = PeriodDetector.detect(series, activeRange = null, config)

        assertTrue("Expected non-empty result", result.isNotEmpty())

        // Check that types alternate
        for (i in 1 until result.size) {
            val prev = result[i - 1].type
            val curr = result[i].type
            assertTrue(
                "Boundaries should alternate at index $i, got $prev then $curr",
                prev != curr
            )
        }
    }

    // === Test: active range filtering excludes boundaries outside range ===

    @Test
    fun `active range filtering excludes boundaries outside range`() {
        val totalDurationMs = 5000L
        val points = generateSignal(totalDurationMs) { tMs ->
            90.0 + sin(2.0 * PI * tMs / 1000.0) * 20.0
        }
        val series = mapOf("spine_angle" to points)

        // Active range only covers 1000ms to 3000ms
        val activeRange = ActiveRange(startTimestampMs = 1000L, endTimestampMs = 3000L)

        val config = PeriodDetectorConfig(smoothWindowSize = 5, minPeakDistanceMs = 200)
        val result = PeriodDetector.detect(series, activeRange, config)

        assertTrue("Expected non-empty result", result.isNotEmpty())

        // All boundaries must be within the active range
        for (boundary in result) {
            assertTrue(
                "Boundary at ${boundary.timestampMs} should be within [$activeRange.startTimestampMs, ${activeRange.endTimestampMs}]",
                boundary.timestampMs >= activeRange.startTimestampMs
            )
            assertTrue(
                "Boundary at ${boundary.timestampMs} should be within [$activeRange.startTimestampMs, ${activeRange.endTimestampMs}]",
                boundary.timestampMs <= activeRange.endTimestampMs
            )
        }
    }

    // === Test: multi-metric auto-selection picks highest amplitude ===

    @Test
    fun `multi-metric auto-selection picks highest amplitude`() {
        val totalDurationMs = 3000L
        val lowAmpPoints = generateSignal(totalDurationMs) { tMs ->
            90.0 + sin(2.0 * PI * tMs / 1000.0) * 5.0 // amplitude ~5
        }
        val highAmpPoints = generateSignal(totalDurationMs) { tMs ->
            90.0 + sin(2.0 * PI * tMs / 1000.0) * 30.0 // amplitude ~30
        }

        val series = mapOf(
            "spine_angle" to lowAmpPoints,
            "left_knee_angle" to highAmpPoints
        )

        // The left_knee_angle has higher amplitude, so peaks should correspond to its timestamps
        val result = PeriodDetector.detect(series, activeRange = null)

        assertTrue("Expected non-empty result", result.isNotEmpty())

        // Verify that the selected metric is the one with higher amplitude
        // We can verify indirectly: low-amp signal might not produce as many clear peaks
        val lowAmpResult = PeriodDetector.detect(mapOf("spine_angle" to lowAmpPoints), activeRange = null)
        val highAmpResult = PeriodDetector.detect(mapOf("left_knee_angle" to highAmpPoints), activeRange = null)

        // The result should be closer to the high-amp result
        val highAmpTimestamps = highAmpResult.map { it.timestampMs }.toSet()
        val matchCount = result.count { it.timestampMs in highAmpTimestamps }
        assertTrue("Most boundaries should match high-amplitude metric", matchCount >= result.size / 2)
    }

    // === Test: minPeakDistanceMs filtering removes close duplicates ===

    @Test
    fun `minPeakDistanceMs filtering removes close duplicates`() {
        // Generate a signal with closely-spaced peaks (high frequency)
        val totalDurationMs = 4000L
        val points = generateSignal(totalDurationMs) { tMs ->
            90.0 + sin(2.0 * PI * tMs / 200.0) * 20.0 // 5 Hz — peaks every ~100ms
        }
        val series = mapOf("spine_angle" to points)

        // With no min distance, many boundaries
        val resultNoFilter = PeriodDetector.detect(
            series,
            activeRange = null,
            PeriodDetectorConfig(smoothWindowSize = 3, minPeakDistanceMs = 0)
        )

        // With large min distance, fewer boundaries
        val resultWithFilter = PeriodDetector.detect(
            series,
            activeRange = null,
            PeriodDetectorConfig(smoothWindowSize = 3, minPeakDistanceMs = 400)
        )

        assertTrue("Without filter there should be more boundaries", resultNoFilter.size > resultWithFilter.size)
        assertTrue("With filter should still have at least 2 boundaries, got ${resultWithFilter.size}", resultWithFilter.size >= 2)

        // Verify spacing with filter
        for (i in 1 until resultWithFilter.size) {
            val gap = resultWithFilter[i].timestampMs - resultWithFilter[i - 1].timestampMs
            assertTrue(
                "Gap between adjacent boundaries should be >= ${400}ms, got ${gap}ms",
                gap >= 400
            )
        }
    }

    // === Test: single point metric returns empty list ===

    @Test
    fun `single point metric returns empty list`() {
        val series = mapOf(
            "spine_angle" to listOf(TimeseriesPoint(timestampMs = 1000L, value = 90.0))
        )
        val result = PeriodDetector.detect(series, activeRange = null)
        assertTrue(result.isEmpty())
    }

    // === Test: two point metric returns empty list ===

    @Test
    fun `two point metric returns empty list`() {
        val series = mapOf(
            "spine_angle" to listOf(
                TimeseriesPoint(timestampMs = 0L, value = 80.0),
                TimeseriesPoint(timestampMs = 1000L, value = 100.0)
            )
        )
        val result = PeriodDetector.detect(series, activeRange = null)
        assertTrue(result.isEmpty())
    }

    // === Test: all empty metrics returns empty list ===

    @Test
    fun `all empty metrics returns empty list`() {
        val series: Map<String, List<TimeseriesPoint>> = mapOf(
            "spine_angle" to emptyList(),
            "left_knee_angle" to emptyList()
        )
        val result = PeriodDetector.detect(series, activeRange = null)
        assertTrue(result.isEmpty())
    }

    // === Test: active range excludes all points returns empty ===

    @Test
    fun `active range that excludes all points returns empty`() {
        val points = (0..50).map { i ->
            TimeseriesPoint(
                timestampMs = 5000L + i * defaultIntervalMs,
                value = 90.0 + sin(i * 0.2) * 20.0
            )
        }
        val series = mapOf("spine_angle" to points)

        // Active range is entirely before the data
        val activeRange = ActiveRange(startTimestampMs = 0L, endTimestampMs = 3000L)
        val result = PeriodDetector.detect(series, activeRange)
        assertTrue(result.isEmpty())
    }

    // === Test: sine signal produces correct boundary types ===

    @Test
    fun `sine signal starts with peak`() {
        // sin starts at 0, goes up to a peak, then down — first detectable boundary is a peak
        val totalDurationMs = 5000L
        val points = generateSignal(totalDurationMs) { tMs ->
            90.0 + sin(2.0 * PI * tMs / 1000.0) * 20.0
        }
        val series = mapOf("spine_angle" to points)

        val result = PeriodDetector.detect(
            series,
            activeRange = null,
            PeriodDetectorConfig(smoothWindowSize = 5, minPeakDistanceMs = 200)
        )

        assertTrue("Expected non-empty result", result.isNotEmpty())

        // First boundary should be a peak (sine starts at 0 and goes up)
        assertEquals(
            "First boundary should be a peak for sine signal",
            PeriodBoundaryType.PEAK,
            result.first().type
        )
    }

    // === Test: result is sorted by timestamp ===

    @Test
    fun `result is sorted by timestamp`() {
        val totalDurationMs = 5000L
        val points = generateSignal(totalDurationMs) { tMs ->
            90.0 + sin(2.0 * PI * tMs / 1000.0) * 20.0
        }
        val series = mapOf("spine_angle" to points)

        val result = PeriodDetector.detect(
            series,
            activeRange = null,
            PeriodDetectorConfig(smoothWindowSize = 5, minPeakDistanceMs = 200)
        )

        for (i in 1 until result.size) {
            assertTrue(
                "Result should be sorted, but ${result[i - 1].timestampMs} > ${result[i].timestampMs}",
                result[i - 1].timestampMs <= result[i].timestampMs
            )
        }
    }

    // === Test: selectBestMetric with single entry ===

    @Test
    fun `selectBestMetric with single entry returns that entry`() {
        val series = mapOf(
            "spine_angle" to listOf(
                TimeseriesPoint(0L, 90.0),
                TimeseriesPoint(1000L, 100.0)
            )
        )
        val result = PeriodDetector.selectBestMetric(series)
        assertEquals("spine_angle", result)
    }

    // === Test: selectBestMetric with empty map returns null ===

    @Test
    fun `selectBestMetric with empty map returns null`() {
        val result = PeriodDetector.selectBestMetric(emptyMap())
        assertEquals(null, result)
    }

    // === Test: smooth with windowSize 1 returns same list ===

    @Test
    fun `smooth with windowSize 1 returns same list`() {
        val points = listOf(
            TimeseriesPoint(0L, 80.0),
            TimeseriesPoint(1000L, 100.0),
            TimeseriesPoint(2000L, 90.0)
        )
        val result = PeriodDetector.smooth(points, 1)
        assertEquals(points.size, result.size)
        for (i in points.indices) {
            assertEquals(points[i].timestampMs, result[i].timestampMs)
            assertEquals(points[i].value, result[i].value, 0.001)
        }
    }

    // === Test: findPeaksAndTroughs with <3 points returns empty ===

    @Test
    fun `findPeaksAndTroughs with less than three points returns empty`() {
        val points = listOf(
            TimeseriesPoint(0L, 80.0),
            TimeseriesPoint(1000L, 100.0)
        )
        val result = PeriodDetector.findPeaksAndTroughs(points)
        assertTrue(result.isEmpty())
    }

    // === Test: filterCloseBoundaries with empty list ===

    @Test
    fun `filterCloseBoundaries with empty list returns empty`() {
        val result = PeriodDetector.filterCloseBoundaries(emptyList(), emptyList(), 300)
        assertTrue(result.isEmpty())
    }

    // === Test: filterCloseBoundaries with single boundary returns same ===

    @Test
    fun `filterCloseBoundaries with single boundary returns same`() {
        val boundaries = listOf(PeriodBoundary(1000L, PeriodBoundaryType.PEAK))
        val smoothed = listOf(TimeseriesPoint(1000L, 120.0))
        val result = PeriodDetector.filterCloseBoundaries(boundaries, smoothed, 300)
        assertEquals(1, result.size)
        assertEquals(1000L, result[0].timestampMs)
    }

    // === Test: smooth reduces noise ===

    @Test
    fun `smooth reduces amplitude of noise`() {
        // Generate a signal with a spike (noise)
        val points = mutableListOf<TimeseriesPoint>()
        for (i in 0..20) {
            val value = if (i == 10) 200.0 else 90.0
            points.add(TimeseriesPoint(i * 33L, value))
        }

        val rawMax = points.maxOf { it.value }
        val rawMin = points.minOf { it.value }
        val rawRange = rawMax - rawMin

        val smoothed = PeriodDetector.smooth(points, 5)
        val smoothedMax = smoothed.maxOf { it.value }
        val smoothedMin = smoothed.minOf { it.value }
        val smoothedRange = smoothedMax - smoothedMin

        // Smoothing should reduce the range
        assertTrue(
            "Smoothed range ($smoothedRange) should be less than raw range ($rawRange)",
            smoothedRange < rawRange
        )
    }

    // === Test: boundaries with active range on multi-metric series ===

    @Test
    fun `boundaries with active range filter correctly on multi-metric series`() {
        // Two metrics, active range excludes some of both
        val totalDurationMs = 4000L
        val metricAPoints = generateSignal(totalDurationMs) { tMs ->
            90.0 + sin(2.0 * PI * tMs / 1000.0) * 15.0
        }
        val metricBPoints = generateSignal(totalDurationMs) { tMs ->
            90.0 + cos(2.0 * PI * tMs / 1000.0) * 25.0
        }
        val series = mapOf(
            "spine_angle" to metricAPoints,
            "left_knee_angle" to metricBPoints
        )

        // Active range only in the middle
        val activeRange = ActiveRange(startTimestampMs = 1500L, endTimestampMs = 2500L)
        val result = PeriodDetector.detect(series, activeRange)

        // All returned boundaries should be within active range
        for (b in result) {
            assertTrue(
                "Boundary at ${b.timestampMs} should be >= ${activeRange.startTimestampMs}",
                b.timestampMs >= activeRange.startTimestampMs
            )
            assertTrue(
                "Boundary at ${b.timestampMs} should be <= ${activeRange.endTimestampMs}",
                b.timestampMs <= activeRange.endTimestampMs
            )
        }
    }

    // === Test: default config works ===

    @Test
    fun `default config produces reasonable results`() {
        val totalDurationMs = 5000L
        val points = generateSignal(totalDurationMs) { tMs ->
            90.0 + sin(2.0 * PI * tMs / 1000.0) * 20.0
        }
        val series = mapOf("spine_angle" to points)

        // Use default config (no explicit config parameter)
        val result = PeriodDetector.detect(series, activeRange = null)

        assertTrue("Expected non-empty result with default config", result.isNotEmpty())
    }

    // === Test: require valid smoothWindowSize ===

    @Test(expected = IllegalArgumentException::class)
    fun `smoothWindowSize must be at least 1`() {
        val series = mapOf(
            "spine_angle" to listOf(
                TimeseriesPoint(0L, 90.0),
                TimeseriesPoint(1000L, 100.0)
            )
        )
        PeriodDetector.detect(series, null, PeriodDetectorConfig(smoothWindowSize = 0))
    }

    // === Test: require valid minPeakDistanceMs ===

    @Test(expected = IllegalArgumentException::class)
    fun `minPeakDistanceMs must not be negative`() {
        val series = mapOf(
            "spine_angle" to listOf(
                TimeseriesPoint(0L, 90.0),
                TimeseriesPoint(1000L, 100.0)
            )
        )
        PeriodDetector.detect(series, null, PeriodDetectorConfig(minPeakDistanceMs = -1))
    }

    // === Test: filterCloseBoundaries keeps more extreme when close ===

    @Test
    fun `filterCloseBoundaries keeps more extreme value when close`() {
        val smoothed = listOf(
            TimeseriesPoint(1000L, 90.0),    // mean marker
            TimeseriesPoint(1050L, 130.0),   // peak 1 (more extreme: 130 - 90 = 40)
            TimeseriesPoint(1100L, 100.0),   // peak 2 (less extreme: 100 - 90 = 10)
            TimeseriesPoint(2000L, 90.0)
        )
        val boundaries = listOf(
            PeriodBoundary(1050L, PeriodBoundaryType.PEAK),
            PeriodBoundary(1100L, PeriodBoundaryType.PEAK)
        )

        // With minPeakDistanceMs = 200, the 1050 and 1100 are too close (50ms apart)
        val result = PeriodDetector.filterCloseBoundaries(boundaries, smoothed, 200)
        assertEquals(1, result.size)
        assertEquals(1050L, result[0].timestampMs) // The more extreme one should survive
    }

    // === Helper methods ===

    private fun generateSignal(
        totalDurationMs: Long,
        valueFn: (timestampMs: Long) -> Double
    ): List<TimeseriesPoint> {
        val points = mutableListOf<TimeseriesPoint>()
        var t = 0L
        while (t <= totalDurationMs) {
            points.add(TimeseriesPoint(timestampMs = t, value = valueFn(t)))
            t += defaultIntervalMs
        }
        return points
    }
}
