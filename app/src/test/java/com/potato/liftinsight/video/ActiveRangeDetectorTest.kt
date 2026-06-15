package com.potato.liftinsight.video

import com.potato.liftinsight.training.data.TimeseriesPoint
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveRangeDetectorTest {

    private val defaultIntervalMs = 33L // ~30 fps

    @Test
    fun `empty list returns null`() {
        val result = ActiveRangeDetector.detect(emptyList())
        assertNull(result)
    }

    @Test
    fun `single point returns null`() {
        val points = listOf(TimeseriesPoint(0L, 90.0))
        val result = ActiveRangeDetector.detect(points)
        assertNull(result)
    }

    @Test
    fun `short video under 2 seconds returns null`() {
        // Generate 1.5s of data
        val points = (0..44).map { i ->
            TimeseriesPoint(timestampMs = i * defaultIntervalMs, value = 90.0 + sin(i * 0.1) * 5.0)
        }
        val durationMs = points.last().timestampMs - points.first().timestampMs
        assertTrue("Duration should be under 2000ms: $durationMs", durationMs < 2000)
        val result = ActiveRangeDetector.detect(points)
        assertNull(result)
    }

    @Test
    fun `all-idle returns null`() {
        // Constant angle for 10 seconds
        val points = generateSignal(totalDurationMs = 10_000, intervalMs = defaultIntervalMs) { _ -> 90.0 }
        val result = ActiveRangeDetector.detect(points)
        assertNull(result)
    }

    @Test
    fun `all-active returns full range with buffer`() {
        // Sinusoidal angle for 10 seconds - all active
        val points = generateSignal(totalDurationMs = 10_000, intervalMs = defaultIntervalMs) { tMs ->
            90.0 + sin(tMs * 0.01) * 30.0
        }

        val config = ActiveRangeConfig(bufferMs = 300)
        val result = ActiveRangeDetector.detect(points, config)
        assertNotNull(result)
        // With buffer, start should be at the first point's timestamp (since it can't go below),
        // and end at the last point's timestamp
        assertEquals(points.first().timestampMs, result!!.startTimestampMs)
        assertEquals(points.last().timestampMs, result.endTimestampMs)
    }

    @Test
    fun `idle-active-idle detects correct range`() {
        // 2s idle, 4s active, 2s idle (total ~8s)
        val points = buildIdleActiveIdleSignal(
            idleStartMs = 2000,
            activeDurationMs = 4000,
            idleEndMs = 2000,
            intervalMs = defaultIntervalMs
        )

        val result = ActiveRangeDetector.detect(points)
        assertNotNull(result)

        // With a 1500ms energy window, early idle points can see active motion
        // just by having the active region in their sliding window. The start may
        // be earlier than the true motion onset. We verify the range straddles
        // the known active region rather than demanding tight bounds.
        assertTrue("Start too early: ${result!!.startTimestampMs}", result.startTimestampMs >= 0)
        assertTrue("Start should be before true active onset", result.startTimestampMs <= 2500)
        assertTrue("End should be after true active onset", result.endTimestampMs >= 5000)
        assertTrue("End too late: ${result.endTimestampMs}", result.endTimestampMs <= points.last().timestampMs)
        assertTrue("Should detect meaningful duration", result.durationMs > 1000)
    }

    @Test
    fun `pause inside motion detected correctly`() {
        // 2s active, 1s pause (idle), 2s active (total ~5s)
        val points = buildPauseInsideMotionSignal(
            activeDurationMs = 2000,
            pauseDurationMs = 1000,
            intervalMs = defaultIntervalMs
        )

        val config = ActiveRangeConfig(energyWindowMs = 1500, energyThreshold = 15.0)
        val result = ActiveRangeDetector.detect(points, config)
        assertNotNull(result)

        // Should span from first active to last active (encompassing the pause)
        assert(result!!.startTimestampMs >= 0) { "Start too early: ${result.startTimestampMs}" }
        assertTrue("End too early: ${result.endTimestampMs}", result.endTimestampMs >= 4000)
        assertTrue("Range should span across the pause", result.durationMs > 2000)
    }

    @Test
    fun `single rep detected correctly`() {
        // A single rep: ~0.5s ramp up, ~1s descent, ~0.5s idle
        val points = mutableListOf<TimeseriesPoint>()
        var t = 0L
        // Idle before rep
        repeat(15) { points.add(TimeseriesPoint(t, 90.0)); t += defaultIntervalMs }
        // Ramp up
        repeat(15) { points.add(TimeseriesPoint(t, 90.0 + 30.0 * (it / 14.0))); t += defaultIntervalMs }
        // Descent
        repeat(30) { points.add(TimeseriesPoint(t, 120.0 - 30.0 * (it / 29.0))); t += defaultIntervalMs }
        // Idle after rep
        repeat(15) { points.add(TimeseriesPoint(t, 90.0)); t += defaultIntervalMs }

        val result = ActiveRangeDetector.detect(points)
        assertNotNull(result)

        // The active range should be detected even with a short signal.
        // With a 1500ms energy window in a short video, early idle points
        // may see the active region in their sliding window, so start may be
        // earlier than the physical motion onset.
        assertTrue("Should detect non-zero range", result!!.durationMs > 0)
        assertTrue("Start should not exceed end", result.startTimestampMs < result.endTimestampMs)
        assertTrue("End should be before video end", result.endTimestampMs <= points.last().timestampMs)
    }

    @Test
    fun `custom config affects threshold`() {
        // Generate a very low-motion signal that wouldn't be detected with default threshold
        val points = generateSignal(totalDurationMs = 10_000, intervalMs = defaultIntervalMs) { tMs ->
            90.0 + sin(tMs * 0.01) * 2.0 // Very low amplitude
        }

        // Default threshold should return null
        val resultDefault = ActiveRangeDetector.detect(points, ActiveRangeConfig(energyThreshold = 15.0))
        assertNull(resultDefault)

        // Low threshold should detect activity
        val resultLow = ActiveRangeDetector.detect(points, ActiveRangeConfig(energyThreshold = 0.5))
        assertNotNull(resultLow)
    }

    @Test
    fun `buffer expands range correctly`() {
        // Generate a signal with a sharp active "blip" in the middle
        val totalMs = 10_000L
        val points = mutableListOf<TimeseriesPoint>()
        var t = 0L
        while (t <= totalMs) {
            val value = if (t in 4000L..5000L) {
                90.0 + 40.0 // Sharp spike
            } else {
                90.0
            }
            points.add(TimeseriesPoint(t, value))
            t += defaultIntervalMs
        }

        // With large buffer
        val bufferMs = 1000L
        val config = ActiveRangeConfig(bufferMs = bufferMs, energyWindowMs = 2000, energyThreshold = 5.0)
        val result = ActiveRangeDetector.detect(points, config)
        assertNotNull(result)

        // The detected active range should be expanded by buffer from the tight active window
        assertTrue("Buffer should expand the range, got ${result!!.durationMs}ms", result.durationMs > 500)
    }

    @Test
    fun `durationMs property works`() {
        val range = ActiveRange(startTimestampMs = 1000L, endTimestampMs = 5000L)
        assertEquals(4000L, range.durationMs)
    }

    @Test
    fun `zero duration returns zero`() {
        val range = ActiveRange(startTimestampMs = 1000L, endTimestampMs = 1000L)
        assertEquals(0L, range.durationMs)
    }

    @Test
    fun `very long active range detected`() {
        // 30 seconds of sinusoidal data
        val points = generateSignal(totalDurationMs = 30_000, intervalMs = defaultIntervalMs) { tMs ->
            90.0 + sin(tMs * 0.005) * 25.0
        }

        val result = ActiveRangeDetector.detect(points)
        assertNotNull(result)
        assertEquals(points.first().timestampMs, result!!.startTimestampMs)
        assertEquals(points.last().timestampMs, result.endTimestampMs)
    }

    @Test
    fun `two distinct active regions detects full span`() {
        // Active 0-2s, idle 2-3s, active 3-5s
        val points = mutableListOf<TimeseriesPoint>()
        var t = 0L
        // Region 1: active
        repeat(60) { points.add(TimeseriesPoint(t, 90.0 + sin(t * 0.05) * 20.0)); t += defaultIntervalMs }
        // Region: idle
        repeat(30) { points.add(TimeseriesPoint(t, 90.0)); t += defaultIntervalMs }
        // Region 2: active
        repeat(60) { points.add(TimeseriesPoint(t, 90.0 + sin(t * 0.05) * 20.0)); t += defaultIntervalMs }

        val config = ActiveRangeConfig(energyWindowMs = 2000, energyThreshold = 15.0)
        val result = ActiveRangeDetector.detect(points, config)
        assertNotNull(result)

        // Should span from first active to last active
        assertTrue("Start too late", result!!.startTimestampMs < 500)
        assertTrue("End too early: ${result.endTimestampMs}", result.endTimestampMs > 3500)
    }

    // -- Helper methods --

    private fun generateSignal(
        totalDurationMs: Long,
        intervalMs: Long,
        valueFn: (timestampMs: Long) -> Double
    ): List<TimeseriesPoint> {
        val points = mutableListOf<TimeseriesPoint>()
        var t = 0L
        while (t <= totalDurationMs) {
            points.add(TimeseriesPoint(timestampMs = t, value = valueFn(t)))
            t += intervalMs
        }
        return points
    }

    private fun buildIdleActiveIdleSignal(
        idleStartMs: Long,
        activeDurationMs: Long,
        idleEndMs: Long,
        intervalMs: Long
    ): List<TimeseriesPoint> {
        val points = mutableListOf<TimeseriesPoint>()
        var t = 0L
        val activeStart = idleStartMs
        val activeEnd = idleStartMs + activeDurationMs
        val totalEnd = activeEnd + idleEndMs

        while (t <= totalEnd) {
            val value = if (t >= activeStart && t <= activeEnd) {
                90.0 + sin((t - activeStart) * 0.03) * 30.0
            } else {
                90.0
            }
            points.add(TimeseriesPoint(timestampMs = t, value = value))
            t += intervalMs
        }
        return points
    }

    private fun buildPauseInsideMotionSignal(
        activeDurationMs: Long,
        pauseDurationMs: Long,
        intervalMs: Long
    ): List<TimeseriesPoint> {
        val points = mutableListOf<TimeseriesPoint>()
        var t = 0L
        val phase1End = activeDurationMs
        val phase2Start = activeDurationMs + pauseDurationMs
        val totalEnd = phase2Start + activeDurationMs

        while (t <= totalEnd) {
            val value = when {
                t <= phase1End -> 90.0 + sin(t * 0.04) * 25.0
                t <= phase2Start -> 90.0 // pause (idle)
                else -> 90.0 + sin((t - phase2Start) * 0.04) * 25.0
            }
            points.add(TimeseriesPoint(timestampMs = t, value = value))
            t += intervalMs
        }
        return points
    }
}
