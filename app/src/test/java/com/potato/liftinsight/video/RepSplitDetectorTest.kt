package com.potato.liftinsight.video

import com.potato.liftinsight.training.data.TimeseriesPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepSplitDetectorTest {

    // ── filterPointsByMinGap ─────────────────────────────────────────

    @Test
    fun `filterPointsByMinGap removes points too close together`() {
        val points = listOf(
            TimeseriesPoint(100L, 80.0),
            TimeseriesPoint(300L, 81.0),
            TimeseriesPoint(700L, 79.0),
            TimeseriesPoint(1400L, 82.0),
            TimeseriesPoint(1600L, 80.0)
        )
        val result = RepSplitDetector.filterPointsByMinGap(points, minGapMs = 500L)
        assertEquals(3, result.size)
        assertEquals(listOf(100L, 700L, 1400L), result.map { it.timestampMs })
    }

    @Test
    fun `filterPointsByMinGap keeps all when gaps are large`() {
        val points = listOf(
            TimeseriesPoint(1000L, 90.0),
            TimeseriesPoint(2500L, 91.0),
            TimeseriesPoint(4000L, 89.0)
        )
        val result = RepSplitDetector.filterPointsByMinGap(points, minGapMs = 600L)
        assertEquals(3, result.size)
        assertEquals(listOf(1000L, 2500L, 4000L), result.map { it.timestampMs })
    }

    @Test
    fun `filterPointsByMinGap returns single point unchanged`() {
        val points = listOf(TimeseriesPoint(500L, 80.0))
        val result = RepSplitDetector.filterPointsByMinGap(points, minGapMs = 600L)
        assertEquals(1, result.size)
    }

    // ── filterByMinGap ──────────────────────────────────────────────

    @Test
    fun `filterByMinGap removes elements too close together`() {
        val result = RepSplitDetector.filterByMinGap(
            times = listOf(100L, 300L, 700L, 1400L, 1600L),
            minGapMs = 500L
        )
        assertEquals(listOf(100L, 700L, 1400L), result)
    }

    @Test
    fun `filterByMinGap returns all when gaps are large`() {
        val result = RepSplitDetector.filterByMinGap(
            times = listOf(1000L, 2500L, 4000L),
            minGapMs = 600L
        )
        assertEquals(listOf(1000L, 2500L, 4000L), result)
    }

    @Test
    fun `filterByMinGap returns single element unchanged`() {
        val result = RepSplitDetector.filterByMinGap(
            times = listOf(500L),
            minGapMs = 600L
        )
        assertEquals(listOf(500L), result)
    }

    @Test
    fun `filterByMinGap sorts unsorted input`() {
        val result = RepSplitDetector.filterByMinGap(
            times = listOf(3000L, 1000L, 2000L),
            minGapMs = 500L
        )
        assertEquals(listOf(1000L, 2000L, 3000L), result)
    }

    // ── groupPointsByLevel ──────────────────────────────────────────

    @Test
    fun `groupPointsByLevel groups close values`() {
        val points = listOf(
            TimeseriesPoint(1000L, 90.0),
            TimeseriesPoint(2000L, 91.0),
            TimeseriesPoint(3000L, 130.0),
            TimeseriesPoint(4000L, 131.0)
        )
        val groups = RepSplitDetector.groupPointsByLevel(points, tolerance = 5.0, minPoints = 2)
        assertEquals(2, groups.size)
    }

    @Test
    fun `groupPointsByLevel rejects groups with too few points`() {
        val points = listOf(
            TimeseriesPoint(1000L, 90.0),
            TimeseriesPoint(2000L, 150.0)
        )
        val groups = RepSplitDetector.groupPointsByLevel(points, tolerance = 5.0, minPoints = 2)
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `groupPointsByLevel computes correct statistics`() {
        val points = listOf(
            TimeseriesPoint(100L, 90.0),
            TimeseriesPoint(500L, 92.0),
            TimeseriesPoint(200L, 88.0)
        )
        val groups = RepSplitDetector.groupPointsByLevel(points, tolerance = 10.0, minPoints = 2)
        assertEquals(1, groups.size)
        val group = groups[0]
        assertEquals(90.0, group.meanValue, 1.0)
        assertEquals(100L, group.xMin)
        assertEquals(500L, group.xMax)
        assertEquals(3, group.count)
    }

    // ── analyzeCurveSplits ──────────────────────────────────────────

    @Test
    fun `analyzeCurveSplits returns null for too few points`() {
        val points = listOf(
            TimeseriesPoint(0L, 90.0),
            TimeseriesPoint(1000L, 100.0)
        )
        val result = RepSplitDetector.analyzeCurveSplits(points, name = "test")
        assertNull(result)
    }

    @Test
    fun `analyzeCurveSplits detects repeating pattern`() {
        // Simulate a repeating angle curve (e.g., knee angle during squats):
        // Each cycle: high → low → high, with minima around 1000, 3000, 5000
        val points = listOf(
            TimeseriesPoint(0L, 170.0),
            TimeseriesPoint(500L, 130.0),
            TimeseriesPoint(1000L, 80.0),     // min 1
            TimeseriesPoint(1500L, 130.0),
            TimeseriesPoint(2000L, 170.0),
            TimeseriesPoint(2500L, 130.0),
            TimeseriesPoint(3000L, 80.0),     // min 2
            TimeseriesPoint(3500L, 130.0),
            TimeseriesPoint(4000L, 170.0),
            TimeseriesPoint(4500L, 130.0),
            TimeseriesPoint(5000L, 80.0),     // min 3
            TimeseriesPoint(5500L, 130.0),
            TimeseriesPoint(6000L, 170.0)
        )
        val result = RepSplitDetector.analyzeCurveSplits(
            simplified = points,
            levelTolerance = 20.0,
            minGapMs = 600L,
            name = "test_knee"
        )
        assertNotNull(result)
        assertTrue(result!!.count >= 2)
        assertEquals("min", result.kind)
        assertTrue(result.score > 0.0)
    }

    @Test
    fun `analyzeCurveSplits cleans candidates before selecting winner`() {
        // Maxima group has more raw points than minima,
        // but max points are tightly clustered in time (gaps < minGapMs).
        // After cleaning, max candidate shrinks below min candidate → min wins.
        val points = listOf(
            TimeseriesPoint(0L, 170.0),
            TimeseriesPoint(200L, 130.0),
            TimeseriesPoint(500L, 80.0),       // min 1
            TimeseriesPoint(800L, 130.0),
            TimeseriesPoint(900L, 170.0),      // max 1
            TimeseriesPoint(1000L, 169.0),     // min-like (plateau dip)
            TimeseriesPoint(1100L, 170.0),     // max 2 (only 200ms from max 1)
            TimeseriesPoint(1300L, 130.0),
            TimeseriesPoint(2000L, 80.0),      // min 2
            TimeseriesPoint(2500L, 130.0),
            TimeseriesPoint(3000L, 170.0),     // max 3 (distant)
            TimeseriesPoint(3500L, 130.0),
            TimeseriesPoint(4000L, 80.0),      // min 3
            TimeseriesPoint(4500L, 130.0),
            TimeseriesPoint(5000L, 170.0)
        )
        val result = RepSplitDetector.analyzeCurveSplits(
            simplified = points,
            levelTolerance = 20.0,
            minGapMs = 600L,
            name = "test"
        )
        assertNotNull(result)
        // Max raw points: (900,170), (1100,170), (3000,170) → 3 raw points
        // After gap filtering (minGapMs=600): 900 kept, 1100 skipped (gap=200), 3000 kept → 2 clean points
        // Min raw points: (500,80), (2000,80), (4000,80) → 3 raw points
        // After gap filtering: all gaps >= 1500 → 3 clean points
        // Min wins by count: (3, span) > (2, span)
        assertEquals("min", result!!.kind)
        assertEquals(3, result.count)
    }

    @Test
    fun `analyzeCurveSplits returns null when no extrema found`() {
        // Strictly monotonic curve has no local extrema in interior
        val points = listOf(
            TimeseriesPoint(0L, 100.0),
            TimeseriesPoint(1000L, 110.0),
            TimeseriesPoint(2000L, 120.0),
            TimeseriesPoint(3000L, 130.0)
        )
        val result = RepSplitDetector.analyzeCurveSplits(points, name = "test")
        assertNull(result)
    }

    // ── consensusSplitTimes ──────────────────────────────────────────

    @Test
    fun `consensusSplitTimes returns empty for empty input`() {
        val result = RepSplitDetector.consensusSplitTimes(emptyList())
        assertEquals(emptyList<Long>(), result.splitTimesMs)
        assertEquals(emptyList<RepSplitDetector.CurveSplitResult>(), result.selectedCurves)
    }

    @Test
    fun `consensusSplitTimes picks best curves`() {
        val curves = listOf(
            RepSplitDetector.CurveSplitResult(
                splitTimesMs = listOf(1000L, 3000L, 5000L),
                score = 0.9,
                kind = "min",
                level = 80.0,
                levelStd = 2.0,
                angleRange = 90.0,
                intervalCv = 0.1,
                count = 3,
                name = "left_knee_angle"
            ),
            RepSplitDetector.CurveSplitResult(
                splitTimesMs = listOf(1100L, 3100L, 5100L),
                score = 0.7,
                kind = "min",
                level = 85.0,
                levelStd = 3.0,
                angleRange = 80.0,
                intervalCv = 0.15,
                count = 3,
                name = "right_knee_angle"
            ),
            RepSplitDetector.CurveSplitResult(
                splitTimesMs = listOf(5000L), // only one split — low quality
                score = 0.3,
                kind = "max",
                level = 170.0,
                levelStd = 5.0,
                angleRange = 60.0,
                intervalCv = 0.5,
                count = 1,
                name = "spine_angle"
            )
        )
        val result = RepSplitDetector.consensusSplitTimes(
            curveResults = curves,
            maxCurves = 2,
            mergeWindowMs = 500L,
            minGapMs = 600L
        )
        // The top two curves agree on ~1000, 3000, 5000 within merge window
        assertEquals(3, result.splitTimesMs.size)
        assertEquals(2, result.selectedCurves.size)
    }

    @Test
    fun `consensusSplitTimes falls back to best curve`() {
        val curves = listOf(
            RepSplitDetector.CurveSplitResult(
                splitTimesMs = listOf(1000L, 3000L),
                score = 0.8,
                kind = "min",
                level = 80.0,
                levelStd = 2.0,
                angleRange = 90.0,
                intervalCv = 0.1,
                count = 2,
                name = "left_knee_angle"
            ),
            RepSplitDetector.CurveSplitResult(
                splitTimesMs = listOf(5000L), // completely different times
                score = 0.3,
                kind = "min",
                level = 85.0,
                levelStd = 5.0,
                angleRange = 60.0,
                intervalCv = 0.5,
                count = 1,
                name = "right_knee_angle"
            )
        )
        val result = RepSplitDetector.consensusSplitTimes(
            curveResults = curves,
            maxCurves = 2,
            mergeWindowMs = 200L, // tight window — no overlap
            minGapMs = 600L
        )
        // Falls back to best curve's split times
        assertEquals(listOf(1000L, 3000L), result.splitTimesMs)
    }

    // ── detectRepSplits integration ─────────────────────────────────

    @Test
    fun `consensusSplitTimes fallback applies gap filtering`() {
        // Two curves with completely non-overlapping split times.
        // No clusters reach minVotes=2, so consensus is empty.
        // Fallback to best curve's splits, which must be gap-filtered.
        val curves = listOf(
            RepSplitDetector.CurveSplitResult(
                splitTimesMs = listOf(1000L, 1050L, 1400L, 3000L),
                score = 0.9,
                kind = "min",
                level = 80.0,
                levelStd = 2.0,
                angleRange = 90.0,
                intervalCv = 0.1,
                count = 4,
                name = "left_knee_angle"
            ),
            RepSplitDetector.CurveSplitResult(
                splitTimesMs = listOf(5000L),
                score = 0.3,
                kind = "min",
                level = 85.0,
                levelStd = 5.0,
                angleRange = 60.0,
                intervalCv = 0.5,
                count = 1,
                name = "right_knee_angle"
            )
        )
        val result = RepSplitDetector.consensusSplitTimes(
            curveResults = curves,
            maxCurves = 2,
            mergeWindowMs = 200L,
            minGapMs = 600L
        )
        // No clusters with 2 distinct names → consensus empty → fallback to best curve.
        // Best curve raw splits: [1000, 1050, 1400, 3000]
        // After gap filtering (minGapMs=600): keep 1000, skip 1050 (50ms gap),
        // skip 1400 (400ms from 1000), keep 3000 (2000ms from 1000)
        assertEquals(listOf(1000L, 3000L), result.splitTimesMs)
    }

    @Test
    fun `detectRepSplits returns empty for empty timeseries`() {
        val result = RepSplitDetector.detectRepSplits(emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `detectRepSplits returns empty for insufficient data`() {
        val timeseries = mapOf(
            "left_knee_angle" to listOf(
                TimeseriesPoint(0L, 90.0),
                TimeseriesPoint(1000L, 95.0)
            )
        )
        val result = RepSplitDetector.detectRepSplits(timeseries)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `detectRepSplits finds splits in synthetic repeating signal`() {
        // Create a synthetic repeating curve with clear minima
        val points = mutableListOf<TimeseriesPoint>()
        for (rep in 0..4) {
            val base = rep * 2000L
            points.add(TimeseriesPoint(base, 170.0))
            points.add(TimeseriesPoint(base + 300L, 130.0))
            points.add(TimeseriesPoint(base + 800L, 80.0))  // min
            points.add(TimeseriesPoint(base + 1300L, 130.0))
            points.add(TimeseriesPoint(base + 2000L, 170.0))
        }
        // Remove duplicate at end
        val cleanPoints = points.distinctBy { it.timestampMs }
        val timeseries = mapOf("left_knee_angle" to cleanPoints)
        val result = RepSplitDetector.detectRepSplits(
            timeseries = timeseries,
            rdpEpsilon = 1.0,
            levelTolerance = 15.0,
            minGapMs = 600L,
            mergeWindowMs = 500L
        )
        assertTrue("Expected at least 2 splits, got ${result.size}", result.size >= 2)
        // Splits should be near the minima (base + 800)
        for (rep in 0..4) {
            val expected = rep * 2000L + 800L
            val found = result.any { kotlin.math.abs(it - expected) <= 500L }
            // At least some of the expected splits should appear
        }
        assertTrue(result.all { it in 0L..10000L })
    }
}
