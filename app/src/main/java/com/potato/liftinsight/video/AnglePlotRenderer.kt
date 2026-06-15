package com.potato.liftinsight.video

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
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

    private val peakMarkerPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0xFFFFD54F.toInt() // Amber
        pathEffect = DashPathEffect(floatArrayOf(8f, 4f), 0f)
    }

    private val troughMarkerPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0xFF80DEEA.toInt() // Teal
        pathEffect = DashPathEffect(floatArrayOf(8f, 4f), 0f)
    }

    fun drawAnglePlotOnCanvas(
        canvas: Canvas,
        angleTimeSeries: Map<String, List<TimeseriesPoint>>,
        currentPositionMs: Long,
        totalDurationMs: Long,
        canvasWidth: Float,
        canvasHeight: Float,
        rdpEpsilon: Double = 1.5,
        activeRange: ActiveRange? = null,
        periods: List<PeriodBoundary>? = null
    ) {
        if (totalDurationMs <= 0L) return

        // Apply RDP simplification for each metric
        val simplifiedSeries = angleTimeSeries.mapValues { (_, points) ->
            RdpSimplifier.simplify(points, rdpEpsilon)
        }

        // Determine effective time range for X-axis scaling
        val effectiveStartMs: Long
        val effectiveEndMs: Long
        if (activeRange != null) {
            effectiveStartMs = activeRange.startTimestampMs
            effectiveEndMs = activeRange.endTimestampMs
        } else {
            effectiveStartMs = 0L
            effectiveEndMs = totalDurationMs
        }
        val effectiveDurationMs = effectiveEndMs - effectiveStartMs
        if (effectiveDurationMs <= 0L) return

        // Map a timestamp to an X coordinate based on the effective range
        fun timestampToX(tsMs: Long): Float {
            val fraction = ((tsMs - effectiveStartMs).toFloat() / effectiveDurationMs).coerceIn(0f, 1f)
            val plotWidth = canvasWidth * 0.45f
            val plotLeft = canvasWidth - plotWidth - 16f
            return plotLeft + fraction * plotWidth
        }

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

        // Find global min/max for normalization (use raw data for scale)
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

        // Draw each angle line (using simplified series)
        simplifiedSeries.forEach { (metricName, points) ->
            if (points.isEmpty()) return@forEach

            val color = lineColors[metricName] ?: Color.WHITE
            linePaint.color = color

            val path = Path()
            var first = true
            points.forEach { pt ->
                val x = timestampToX(pt.timestampMs)
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

        // Draw period boundary markers (vertical dashed lines)
        if (periods != null && periods.isNotEmpty()) {
            val plotLeftVal = plotLeft
            val plotRightVal = plotLeft + plotWidth
            for (period in periods) {
                val x = timestampToX(period.timestampMs)
                // Only draw if within plot bounds
                if (x >= plotLeftVal && x <= plotRightVal) {
                    val paint = when (period.type) {
                        PeriodBoundaryType.PEAK -> peakMarkerPaint
                        PeriodBoundaryType.TROUGH -> troughMarkerPaint
                    }
                    canvas.drawLine(x, plotTop, x, plotTop + plotHeight, paint)
                }
            }
        }

        // Draw current position line
        val currentX = timestampToX(currentPositionMs)
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
