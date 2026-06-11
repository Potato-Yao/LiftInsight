package com.potato.liftinsight.video

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.potato.liftinsight.training.data.TimeseriesPoint

object AnglePlotRenderer {

    private val lineColors = mapOf(
        "spine_angle" to 0xFF00BCD4.toInt(),           // Cyan
        "left_knee_angle" to 0xFFFF5722.toInt(),       // Deep orange
        "right_knee_angle" to 0xFFFF9800.toInt(),      // Orange
        "left_leg_spine_angle" to 0xFF4CAF50.toInt(),  // Green
        "right_leg_spine_angle" to 0xFF8BC34A.toInt()  // Light green
    )

    private val backgroundPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.argb(153, 0, 0, 0) // alpha 0.6
    }

    private val borderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.argb(76, 255, 255, 255) // alpha 0.3
    }

    private val linePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
    }

    private val currentLinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.WHITE
    }

    private val labelPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = 16f
        alpha = 180
    }

    fun drawAnglePlotOnCanvas(
        canvas: Canvas,
        angleTimeSeries: Map<String, List<TimeseriesPoint>>,
        currentPositionMs: Long,
        totalDurationMs: Long,
        canvasWidth: Float,
        canvasHeight: Float
    ) {
        if (totalDurationMs <= 0L) return

        // Define plot area (bottom-right corner, compact)
        val plotPadding = 16f
        val plotWidth = canvasWidth * 0.45f
        val plotHeight = canvasHeight * 0.3f
        val plotLeft = canvasWidth - plotWidth - plotPadding
        val plotTop = canvasHeight - plotHeight - plotPadding

        // Draw semi-transparent background
        canvas.drawRect(plotLeft, plotTop, plotLeft + plotWidth, plotTop + plotHeight, backgroundPaint)

        // Draw border
        canvas.drawRect(plotLeft, plotTop, plotLeft + plotWidth, plotTop + plotHeight, borderPaint)

        // Find global min/max for normalization
        var globalMin = Float.MAX_VALUE
        var globalMax = Float.MIN_VALUE
        angleTimeSeries.values.forEach { points ->
            points.forEach { pt ->
                val v = pt.value.toFloat()
                if (v < globalMin) globalMin = v
                if (v > globalMax) globalMax = v
            }
        }
        if (globalMin >= globalMax) {
            globalMin = 0f
            globalMax = 180f
        }
        val range = globalMax - globalMin
        val paddedMin = globalMin - range * 0.1f
        val paddedMax = globalMax + range * 0.1f
        val paddedRange = paddedMax - paddedMin

        // Draw each angle line
        angleTimeSeries.forEach { (metricName, points) ->
            if (points.isEmpty()) return@forEach

            val color = lineColors[metricName] ?: Color.WHITE
            linePaint.color = color

            val path = Path()
            var first = true
            points.forEach { pt ->
                val x = plotLeft + (pt.timestampMs.toFloat() / totalDurationMs) * plotWidth
                val normalizedY = (pt.value.toFloat() - paddedMin) / paddedRange
                val y = plotTop + plotHeight - normalizedY * plotHeight

                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
            }
            canvas.drawPath(path, linePaint)
        }

        // Draw current position line
        val currentX = plotLeft + (currentPositionMs.toFloat() / totalDurationMs) * plotWidth
        canvas.drawLine(currentX, plotTop, currentX, plotTop + plotHeight, currentLinePaint)

        // Draw min/max labels
        canvas.drawText(
            "%.0f°".format(paddedMax),
            plotLeft + 4f,
            plotTop + 14f,
            labelPaint
        )
        canvas.drawText(
            "%.0f°".format(paddedMin),
            plotLeft + 4f,
            plotTop + plotHeight - 4f,
            labelPaint
        )
    }
}
