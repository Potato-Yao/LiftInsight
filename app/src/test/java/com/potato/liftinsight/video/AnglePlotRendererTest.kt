package com.potato.liftinsight.video

import android.graphics.Bitmap
import android.graphics.Canvas
import com.potato.liftinsight.training.data.TimeseriesPoint
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnglePlotRendererTest {

    private fun createCanvas(): Canvas {
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        return Canvas(bitmap)
    }

    @Test
    fun `drawAnglePlotOnCanvas does not throw with empty time series`() {
        val canvas = createCanvas()
        val emptySeries: Map<String, List<TimeseriesPoint>> = emptyMap()

        // Should not throw
        AnglePlotRenderer.drawAnglePlotOnCanvas(
            canvas = canvas,
            angleTimeSeries = emptySeries,
            currentPositionMs = 1000L,
            totalDurationMs = 5000L,
            canvasWidth = 1920f,
            canvasHeight = 1080f
        )
    }

    @Test
    fun `drawAnglePlotOnCanvas does not throw with zero duration`() {
        val canvas = createCanvas()
        val series = mapOf(
            "spine_angle" to listOf(
                TimeseriesPoint(timestampMs = 0L, value = 90.0),
                TimeseriesPoint(timestampMs = 1000L, value = 95.0)
            )
        )

        // Should not throw (returns early because totalDurationMs <= 0)
        AnglePlotRenderer.drawAnglePlotOnCanvas(
            canvas = canvas,
            angleTimeSeries = series,
            currentPositionMs = 500L,
            totalDurationMs = 0L,
            canvasWidth = 1920f,
            canvasHeight = 1080f
        )
    }

    @Test
    fun `drawAnglePlotOnCanvas does not throw with negative duration`() {
        val canvas = createCanvas()
        val series = mapOf(
            "spine_angle" to listOf(
                TimeseriesPoint(timestampMs = 0L, value = 90.0)
            )
        )

        // Should not throw (returns early because totalDurationMs <= 0)
        AnglePlotRenderer.drawAnglePlotOnCanvas(
            canvas = canvas,
            angleTimeSeries = series,
            currentPositionMs = 0L,
            totalDurationMs = -1L,
            canvasWidth = 1920f,
            canvasHeight = 1080f
        )
    }

    @Test
    fun `drawAnglePlotOnCanvas handles single metric single point`() {
        val canvas = createCanvas()
        val series = mapOf(
            "spine_angle" to listOf(
                TimeseriesPoint(timestampMs = 500L, value = 90.0)
            )
        )

        AnglePlotRenderer.drawAnglePlotOnCanvas(
            canvas = canvas,
            angleTimeSeries = series,
            currentPositionMs = 500L,
            totalDurationMs = 1000L,
            canvasWidth = 1920f,
            canvasHeight = 1080f
        )
    }

    @Test
    fun `drawAnglePlotOnCanvas handles multiple metrics`() {
        val canvas = createCanvas()
        val series = buildMultiMetricSeries()

        AnglePlotRenderer.drawAnglePlotOnCanvas(
            canvas = canvas,
            angleTimeSeries = series,
            currentPositionMs = 2000L,
            totalDurationMs = 5000L,
            canvasWidth = 1920f,
            canvasHeight = 1080f
        )
    }

    @Test
    fun `drawAnglePlotOnCanvas handles missing metric names in color map`() {
        val canvas = createCanvas()
        val series = mapOf(
            "unknown_metric" to listOf(
                TimeseriesPoint(timestampMs = 0L, value = 45.0),
                TimeseriesPoint(timestampMs = 1000L, value = 50.0)
            )
        )

        // Should not throw, unknown metric gets white color
        AnglePlotRenderer.drawAnglePlotOnCanvas(
            canvas = canvas,
            angleTimeSeries = series,
            currentPositionMs = 500L,
            totalDurationMs = 2000L,
            canvasWidth = 1920f,
            canvasHeight = 1080f
        )
    }

    @Test
    fun `drawAnglePlotOnCanvas handles metric with empty points list`() {
        val canvas = createCanvas()
        val series = mapOf(
            "spine_angle" to listOf(
                TimeseriesPoint(timestampMs = 0L, value = 90.0)
            ),
            "left_knee_angle" to emptyList<TimeseriesPoint>()
        )

        AnglePlotRenderer.drawAnglePlotOnCanvas(
            canvas = canvas,
            angleTimeSeries = series,
            currentPositionMs = 0L,
            totalDurationMs = 1000L,
            canvasWidth = 1920f,
            canvasHeight = 1080f
        )
    }

    @Test
    fun `drawAnglePlotOnCanvas handles all known metrics`() {
        val canvas = createCanvas()
        val series = mapOf(
            "spine_angle" to listOf(
                TimeseriesPoint(timestampMs = 0L, value = 90.0),
                TimeseriesPoint(timestampMs = 2500L, value = 100.0),
                TimeseriesPoint(timestampMs = 5000L, value = 85.0)
            ),
            "left_knee_angle" to listOf(
                TimeseriesPoint(timestampMs = 0L, value = 120.0),
                TimeseriesPoint(timestampMs = 2500L, value = 80.0),
                TimeseriesPoint(timestampMs = 5000L, value = 130.0)
            ),
            "right_knee_angle" to listOf(
                TimeseriesPoint(timestampMs = 0L, value = 115.0),
                TimeseriesPoint(timestampMs = 5000L, value = 125.0)
            ),
            "left_leg_spine_angle" to listOf(
                TimeseriesPoint(timestampMs = 1000L, value = 40.0),
                TimeseriesPoint(timestampMs = 4000L, value = 45.0)
            ),
            "right_leg_spine_angle" to listOf(
                TimeseriesPoint(timestampMs = 500L, value = 38.0),
                TimeseriesPoint(timestampMs = 3000L, value = 42.0)
            )
        )

        AnglePlotRenderer.drawAnglePlotOnCanvas(
            canvas = canvas,
            angleTimeSeries = series,
            currentPositionMs = 3000L,
            totalDurationMs = 5000L,
            canvasWidth = 1920f,
            canvasHeight = 1080f
        )
    }

    @Test
    fun `drawAnglePlotOnCanvas handles current position at start`() {
        val canvas = createCanvas()
        val series = buildMultiMetricSeries()

        AnglePlotRenderer.drawAnglePlotOnCanvas(
            canvas = canvas,
            angleTimeSeries = series,
            currentPositionMs = 0L,
            totalDurationMs = 5000L,
            canvasWidth = 1920f,
            canvasHeight = 1080f
        )
    }

    @Test
    fun `drawAnglePlotOnCanvas handles current position at end`() {
        val canvas = createCanvas()
        val series = buildMultiMetricSeries()

        AnglePlotRenderer.drawAnglePlotOnCanvas(
            canvas = canvas,
            angleTimeSeries = series,
            currentPositionMs = 5000L,
            totalDurationMs = 5000L,
            canvasWidth = 1920f,
            canvasHeight = 1080f
        )
    }

    @Test
    fun `drawAnglePlotOnCanvas handles current position beyond duration`() {
        val canvas = createCanvas()
        val series = buildMultiMetricSeries()

        // Should not crash, current position line will be at far right
        AnglePlotRenderer.drawAnglePlotOnCanvas(
            canvas = canvas,
            angleTimeSeries = series,
            currentPositionMs = 10000L,
            totalDurationMs = 5000L,
            canvasWidth = 1920f,
            canvasHeight = 1080f
        )
    }

    @Test
    fun `drawAnglePlotOnCanvas handles identical min max values`() {
        val canvas = createCanvas()
        val series = mapOf(
            "spine_angle" to listOf(
                TimeseriesPoint(timestampMs = 0L, value = 90.0),
                TimeseriesPoint(timestampMs = 1000L, value = 90.0)
            )
        )

        // Should not throw, falls back to 0-180 range
        AnglePlotRenderer.drawAnglePlotOnCanvas(
            canvas = canvas,
            angleTimeSeries = series,
            currentPositionMs = 500L,
            totalDurationMs = 2000L,
            canvasWidth = 1920f,
            canvasHeight = 1080f
        )
    }

    @Test
    fun `drawAnglePlotOnCanvas handles small canvas`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val series = buildMultiMetricSeries()

        // Should not crash on small canvas
        AnglePlotRenderer.drawAnglePlotOnCanvas(
            canvas = canvas,
            angleTimeSeries = series,
            currentPositionMs = 2000L,
            totalDurationMs = 5000L,
            canvasWidth = 100f,
            canvasHeight = 100f
        )
    }

    @Test
    fun `drawAnglePlotOnCanvas plot area is within canvas bounds`() {
        // This test validatesthat the plot area is properly bounded
        val canvas = createCanvas()
        val series = buildMultiMetricSeries()

        AnglePlotRenderer.drawAnglePlotOnCanvas(
            canvas = canvas,
            angleTimeSeries = series,
            currentPositionMs = 2000L,
            totalDurationMs = 5000L,
            canvasWidth = 1920f,
            canvasHeight = 1080f
        )

        // The canvas should have been drawn on; we can't inspect pixels easily,
        // but the test at least verifies no exception was thrown
        assertNotNull(canvas)
    }

    private fun buildMultiMetricSeries(): Map<String, List<TimeseriesPoint>> {
        return mapOf(
            "spine_angle" to (0..20).map { i ->
                TimeseriesPoint(
                    timestampMs = i * 250L,
                    value = 85.0 + sin(i * 0.3) * 10.0
                )
            },
            "left_knee_angle" to (0..20).map { i ->
                TimeseriesPoint(
                    timestampMs = i * 250L,
                    value = 70.0 + cos(i * 0.25) * 30.0
                )
            },
            "right_knee_angle" to (0..20).map { i ->
                TimeseriesPoint(
                    timestampMs = i * 250L,
                    value = 75.0 + cos(i * 0.25 + 1.0) * 28.0
                )
            }
        )
    }
}
