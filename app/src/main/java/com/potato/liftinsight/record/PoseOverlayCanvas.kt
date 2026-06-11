package com.potato.liftinsight.record

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.potato.liftinsight.training.data.TimeseriesPoint
import com.potato.liftinsight.video.PoseOverlayLandmark
import com.potato.liftinsight.video.PoseOverlayRenderer

internal data class PoseFrameSnapshot(
    val timestampMs: Long,
    val landmarks: Map<Int, LandmarkPosition>
)

internal data class LandmarkPosition(
    val x: Float,
    val y: Float,
    val visibility: Float
)

@Composable
internal fun PoseOverlayCanvas(
    poseFrame: PoseFrameSnapshot?,
    currentAngles: Map<String, Double?>,
    angleTimeSeries: Map<String, List<TimeseriesPoint>>,
    currentPositionMs: Long,
    totalDurationMs: Long,
    videoWidth: Int,
    videoHeight: Int,
    showSkeleton: Boolean,
    showAngleDisplay: Boolean,
    showAnglePlot: Boolean,
    modifier: Modifier = Modifier
) {
    val hasAnyOverlay = (showSkeleton && poseFrame != null) ||
            (showAngleDisplay && currentAngles.values.any { it != null }) ||
            (showAnglePlot && angleTimeSeries.values.any { it.isNotEmpty() })

    if (!hasAnyOverlay) return

    Canvas(modifier = modifier) {
        val canvas = drawContext.canvas.nativeCanvas
        val w = size.width
        val h = size.height

        // Calculate actual video display area (letterboxing)
        val videoAspect = if (videoWidth > 0 && videoHeight > 0) {
            videoWidth.toFloat() / videoHeight.toFloat()
        } else {
            w / h  // fallback: assume canvas matches video
        }
        val canvasAspect = w / h

        val (videoDisplayW, videoDisplayH, offsetX, offsetY) = if (videoAspect > canvasAspect) {
            // Video is wider than canvas → pillarboxing (bars on sides)
            // Video fills width, height is reduced
            val displayH = w / videoAspect
            val offY = (h - displayH) / 2f
            listOf(w, displayH, 0f, offY)
        } else {
            // Video is taller than canvas → letterboxing (bars on top/bottom)
            // Video fills height, width is reduced
            val displayW = h * videoAspect
            val offX = (w - displayW) / 2f
            listOf(displayW, h, offX, 0f)
        }

        if (showSkeleton && poseFrame != null) {
            // Scale normalized coordinates to the video display area (not full canvas)
            val pixelPositions = poseFrame.landmarks.mapValues { (_, lp) ->
                PoseOverlayLandmark(
                    x = offsetX + lp.x * videoDisplayW,
                    y = offsetY + lp.y * videoDisplayH,
                    visibility = lp.visibility
                )
            }
            PoseOverlayRenderer.drawPoseLandmarks(canvas, pixelPositions)
        }

        if (showAngleDisplay) {
            val lines = buildAngleTextLines(currentAngles)
            PoseOverlayRenderer.drawAngleOverlay(canvas, lines)
        }

        if (showAnglePlot && angleTimeSeries.values.any { it.isNotEmpty() }) {
            drawAnglePlot(angleTimeSeries, currentPositionMs, totalDurationMs)
        }
    }
}

private fun buildAngleTextLines(angles: Map<String, Double?>): List<String> {
    val lines = mutableListOf<String>()
    angles["spine_angle"]?.let { lines += "Spine: %.1f\u00B0".format(it) }
    angles["left_leg_spine_angle"]?.let { lines += "L Leg-Spine: %.1f\u00B0".format(it) }
    angles["right_leg_spine_angle"]?.let { lines += "R Leg-Spine: %.1f\u00B0".format(it) }
    angles["left_knee_angle"]?.let { lines += "L Knee: %.1f\u00B0".format(it) }
    angles["right_knee_angle"]?.let { lines += "R Knee: %.1f\u00B0".format(it) }
    return lines
}

private fun DrawScope.drawAnglePlot(
    angleTimeSeries: Map<String, List<TimeseriesPoint>>,
    currentPositionMs: Long,
    totalDurationMs: Long
) {
    if (totalDurationMs <= 0L) return

    // Define plot area (bottom-right corner, compact)
    val plotPadding = 16f
    val plotWidth = size.width * 0.45f
    val plotHeight = size.height * 0.3f
    val plotLeft = size.width - plotWidth - plotPadding
    val plotTop = size.height - plotHeight - plotPadding

    // Draw semi-transparent background
    drawRect(
        color = Color.Black.copy(alpha = 0.6f),
        topLeft = Offset(plotLeft, plotTop),
        size = ComposeSize(plotWidth, plotHeight)
    )

    // Draw border
    drawRect(
        color = Color.White.copy(alpha = 0.3f),
        topLeft = Offset(plotLeft, plotTop),
        size = ComposeSize(plotWidth, plotHeight),
        style = Stroke(width = 1f)
    )

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

    // Colors for each metric line
    val lineColors = mapOf(
        "spine_angle" to Color(0xFF00BCD4),           // Cyan
        "left_knee_angle" to Color(0xFFFF5722),       // Deep orange
        "right_knee_angle" to Color(0xFFFF9800),      // Orange
        "left_leg_spine_angle" to Color(0xFF4CAF50),  // Green
        "right_leg_spine_angle" to Color(0xFF8BC34A)  // Light green
    )

    // Draw each angle line
    angleTimeSeries.forEach { (metricName, points) ->
        if (points.isEmpty()) return@forEach
        val color = lineColors[metricName] ?: Color.White

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

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )
    }

    // Draw current position line
    val currentX = plotLeft + (currentPositionMs.toFloat() / totalDurationMs) * plotWidth
    drawLine(
        color = Color.White,
        start = Offset(currentX, plotTop),
        end = Offset(currentX, plotTop + plotHeight),
        strokeWidth = 2f
    )

    // Draw min/max labels
    val labelPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 16f
        isAntiAlias = true
        alpha = 180
    }
    drawContext.canvas.nativeCanvas.drawText(
        "%.0f°".format(paddedMax),
        plotLeft + 4f,
        plotTop + 14f,
        labelPaint
    )
    drawContext.canvas.nativeCanvas.drawText(
        "%.0f°".format(paddedMin),
        plotLeft + 4f,
        plotTop + plotHeight - 4f,
        labelPaint
    )
}

internal fun parseLandmarksJson(json: String): Map<Int, LandmarkPosition> {
    if (json.isBlank()) return emptyMap()
    return try {
        val array = org.json.JSONArray(json)
        val result = mutableMapOf<Int, LandmarkPosition>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val type = obj.getInt("t")
            result[type] = LandmarkPosition(
                x = obj.getDouble("x").toFloat(),
                y = obj.getDouble("y").toFloat(),
                visibility = obj.getDouble("v").toFloat()
            )
        }
        result
    } catch (_: Exception) {
        emptyMap()
    }
}
