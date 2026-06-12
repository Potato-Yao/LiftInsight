package com.potato.liftinsight.video

import com.potato.liftinsight.training.data.TimeseriesPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RdpSimplifierTest {

    @Test
    fun `empty list returns empty list`() {
        val result = RdpSimplifier.simplify(emptyList(), 1.5)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single point returns single point`() {
        val points = listOf(TimeseriesPoint(100L, 90.0))
        val result = RdpSimplifier.simplify(points, 1.5)
        assertEquals(1, result.size)
        assertEquals(100L, result[0].timestampMs)
        assertEquals(90.0, result[0].value, 0.001)
    }

    @Test
    fun `two points returns both points`() {
        val points = listOf(
            TimeseriesPoint(0L, 90.0),
            TimeseriesPoint(1000L, 100.0)
        )
        val result = RdpSimplifier.simplify(points, 1.5)
        assertEquals(2, result.size)
    }

    @Test
    fun `collinear points reduce to endpoints`() {
        val points = listOf(
            TimeseriesPoint(0L, 90.0),
            TimeseriesPoint(500L, 95.0),
            TimeseriesPoint(1000L, 100.0)
        )
        val result = RdpSimplifier.simplify(points, 0.1)
        assertEquals(2, result.size)
        assertEquals(0L, result[0].timestampMs)
        assertEquals(1000L, result[1].timestampMs)
    }

    @Test
    fun `epsilon zero preserves all points`() {
        val points = listOf(
            TimeseriesPoint(0L, 90.0),
            TimeseriesPoint(500L, 120.0),
            TimeseriesPoint(1000L, 85.0)
        )
        val result = RdpSimplifier.simplify(points, 0.0)
        assertEquals(3, result.size)
    }

    @Test
    fun `large epsilon reduces to endpoints only`() {
        val points = listOf(
            TimeseriesPoint(0L, 90.0),
            TimeseriesPoint(250L, 95.0),
            TimeseriesPoint(500L, 120.0),
            TimeseriesPoint(750L, 88.0),
            TimeseriesPoint(1000L, 100.0)
        )
        val result = RdpSimplifier.simplify(points, 1000.0)
        assertEquals(2, result.size)
        assertEquals(0L, result[0].timestampMs)
        assertEquals(1000L, result[1].timestampMs)
    }

    @Test
    fun `zigzag pattern keeps significant points`() {
        // A zigzag where the middle peak is significant
        val points = listOf(
            TimeseriesPoint(0L, 90.0),
            TimeseriesPoint(250L, 92.0),
            TimeseriesPoint(500L, 150.0),  // significant peak
            TimeseriesPoint(750L, 92.0),
            TimeseriesPoint(1000L, 90.0)
        )
        val result = RdpSimplifier.simplify(points, 5.0)
        // Should keep: 0, 500 (peak), 1000
        assertTrue(result.size >= 3)
        // Verify the peak is preserved
        assertTrue(result.any { it.timestampMs == 500L && it.value == 150.0 })
    }

    @Test
    fun `preserves endpoints regardless of epsilon`() {
        val points = (0..50).map { i ->
            TimeseriesPoint(i * 100L, 90.0 + (i % 3) * 0.1) // very small noise
        }
        val result = RdpSimplifier.simplify(points, 5.0)
        assertEquals(points.first().timestampMs, result.first().timestampMs)
        assertEquals(points.last().timestampMs, result.last().timestampMs)
    }

    @Test
    fun `result is always subset of original points`() {
        val points = listOf(
            TimeseriesPoint(0L, 90.0),
            TimeseriesPoint(100L, 95.0),
            TimeseriesPoint(200L, 110.0),
            TimeseriesPoint(300L, 105.0),
            TimeseriesPoint(400L, 92.0),
            TimeseriesPoint(500L, 90.0)
        )
        val result = RdpSimplifier.simplify(points, 3.0)
        result.forEach { rp ->
            assertTrue("Point ($rp) should exist in original", points.contains(rp))
        }
    }

    @Test
    fun `interpolateValue returns exact match`() {
        val points = listOf(
            TimeseriesPoint(0L, 90.0),
            TimeseriesPoint(1000L, 100.0)
        )
        val value = RdpSimplifier.interpolateValue(points, 0L)
        assertEquals(90.0, value!!, 0.001)
    }

    @Test
    fun `interpolateValue interpolates between points`() {
        val points = listOf(
            TimeseriesPoint(0L, 90.0),
            TimeseriesPoint(1000L, 100.0)
        )
        val value = RdpSimplifier.interpolateValue(points, 500L)
        assertEquals(95.0, value!!, 0.001)
    }

    @Test
    fun `interpolateValue clamps before range`() {
        val points = listOf(
            TimeseriesPoint(100L, 90.0),
            TimeseriesPoint(1000L, 100.0)
        )
        val value = RdpSimplifier.interpolateValue(points, 0L)
        assertEquals(90.0, value!!, 0.001)
    }

    @Test
    fun `interpolateValue clamps after range`() {
        val points = listOf(
            TimeseriesPoint(0L, 90.0),
            TimeseriesPoint(1000L, 100.0)
        )
        val value = RdpSimplifier.interpolateValue(points, 2000L)
        assertEquals(100.0, value!!, 0.001)
    }

    @Test
    fun `interpolateValue returns null for empty list`() {
        val value = RdpSimplifier.interpolateValue(emptyList(), 500L)
        assertEquals(null, value)
    }

    @Test
    fun `interpolateValue returns single value for single point`() {
        val points = listOf(TimeseriesPoint(500L, 42.0))
        val value = RdpSimplifier.interpolateValue(points, 500L)
        assertEquals(42.0, value!!, 0.001)
    }

    @Test
    fun `perpendicular distance of point on line is zero`() {
        val start = TimeseriesPoint(0L, 90.0)
        val end = TimeseriesPoint(1000L, 100.0)
        val onLine = TimeseriesPoint(500L, 95.0) // exactly on the line
        val dist = RdpSimplifier.perpendicularDistance(onLine, start, end)
        assertEquals(0.0, dist, 0.001)
    }

    @Test
    fun `perpendicular distance of point off line is positive`() {
        val start = TimeseriesPoint(0L, 90.0)
        val end = TimeseriesPoint(1000L, 100.0)
        val offLine = TimeseriesPoint(500L, 120.0) // well above the line
        val dist = RdpSimplifier.perpendicularDistance(offLine, start, end)
        assertTrue(dist > 0.0)
    }
}
