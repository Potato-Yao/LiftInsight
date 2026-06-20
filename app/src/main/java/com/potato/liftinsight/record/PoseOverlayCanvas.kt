package com.potato.liftinsight.record

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import com.potato.liftinsight.R
import com.potato.liftinsight.training.data.TimeseriesPoint
import com.potato.liftinsight.video.ActiveRange
import com.potato.liftinsight.video.BarbellOverlayRenderer
import com.potato.liftinsight.video.BarbellPosition
import com.potato.liftinsight.video.PeriodBoundary
import com.potato.liftinsight.video.PeriodBoundaryType
import com.potato.liftinsight.video.PoseOverlayLandmark
import com.potato.liftinsight.video.PoseOverlayRenderer
import com.potato.liftinsight.video.RdpSimplifier
import com.potato.liftinsight.video.SelectableCircle
import com.potato.liftinsight.video.SelectableLine

private const val BARBELL_TRAIL_LENGTH = 10

internal data class PoseFrameSnapshot(
    val timestampMs: Long,
    val landmarks: Map<Int, LandmarkPosition>
)

internal data class LandmarkPosition(
    val x: Float,
    val y: Float,
    val visibility: Float
)

internal data class BarbellFrameSnapshot(
    val timestampMs: Long,
    val x: Float,
    val y: Float,
    val radius: Float
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
    rdpEpsilon: Double = 1.5,
    allPoseFrames: List<PoseFrameSnapshot> = emptyList(),
    rdpSmoothSkeleton: Boolean = false,
    showBarbellTrace: Boolean = false,
    barbellFrames: List<BarbellFrameSnapshot> = emptyList(),
    selectableCircles: List<SelectableCircle> = emptyList(),
    onCircleTapped: ((Int) -> Unit)? = null,
    selectableLines: List<SelectableLine> = emptyList(),
    onLineTapped: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    activeRange: ActiveRange? = null,
    periods: List<PeriodBoundary>? = null
) {
    val hasAnyOverlay = (showSkeleton && poseFrame != null) ||
            (showAngleDisplay && currentAngles.values.any { it != null }) ||
            (showAnglePlot && angleTimeSeries.values.any { it.isNotEmpty() }) ||
            (showBarbellTrace && barbellFrames.isNotEmpty()) ||
            selectableCircles.isNotEmpty() ||
            selectableLines.isNotEmpty()

    if (!hasAnyOverlay) return

    val angleTextLines = if (showAngleDisplay) {
        val interpolatedAngles = angleTimeSeries.mapValues { (_, points) ->
            if (points.isEmpty()) return@mapValues null
            val simplified = RdpSimplifier.simplify(points, rdpEpsilon)
            RdpSimplifier.interpolateValue(simplified, currentPositionMs)
        }
        val displayAngles = currentAngles.mapValues { (key, raw) ->
            interpolatedAngles[key] ?: raw
        }
        buildAngleTextLines(displayAngles)
    } else {
        emptyList()
    }

    val effectiveModifier = if ((onCircleTapped != null && selectableCircles.isNotEmpty()) ||
        (onLineTapped != null && selectableLines.isNotEmpty())) {
        modifier.pointerInput(selectableCircles, selectableLines) {
            detectTapGestures { tapOffset ->
                val wid = size.width.toFloat()
                val hei = size.height.toFloat()

                // Calculate video display area (letterboxing)
                val videoAspect = if (videoWidth > 0 && videoHeight > 0) {
                    videoWidth.toFloat() / videoHeight.toFloat()
                } else {
                    wid / hei
                }
                val canvasAspect = wid / hei

                val videoDisplayW: Float
                val videoDisplayH: Float
                val offsetX: Float
                val offsetY: Float
                if (videoAspect > canvasAspect) {
                    videoDisplayW = wid
                    videoDisplayH = wid / videoAspect
                    offsetX = 0f
                    offsetY = (hei - videoDisplayH) / 2f
                } else {
                    videoDisplayW = hei * videoAspect
                    videoDisplayH = hei
                    offsetX = (wid - videoDisplayW) / 2f
                    offsetY = 0f
                }

                // Check line taps first (lines are more precise for barbell)
                for ((index, line) in selectableLines.withIndex()) {
                    val lx1 = offsetX + line.x1 * videoDisplayW
                    val ly1 = offsetY + line.y1 * videoDisplayH
                    val lx2 = offsetX + line.x2 * videoDisplayW
                    val ly2 = offsetY + line.y2 * videoDisplayH

                    val dist = pointToLineDistance(tapOffset.x, tapOffset.y, lx1, ly1, lx2, ly2)
                    if (dist < 20f) {
                        onLineTapped?.invoke(index)
                        return@detectTapGestures
                    }
                }

                // Then check circle taps
                for ((index, circle) in selectableCircles.withIndex()) {
                    val circleX = offsetX + circle.x * videoDisplayW
                    val circleY = offsetY + circle.y * videoDisplayH
                    val halfSize = circle.radius.coerceAtLeast(8f)

                    if (tapOffset.x in (circleX - halfSize)..(circleX + halfSize) &&
                        tapOffset.y in (circleY - halfSize)..(circleY + halfSize)
                    ) {
                        onCircleTapped?.invoke(index)
                        return@detectTapGestures
                    }
                }
            }
        }
    } else {
        modifier
    }

    Canvas(modifier = effectiveModifier) {
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
            val effectiveFrame = if (rdpSmoothSkeleton && allPoseFrames.size > 2) {
                // Apply RDP smoothing to each landmark's x and y time series independently
                val smoothedLandmarks = mutableMapOf<Int, LandmarkPosition>()
                val landmarkTypes = poseFrame.landmarks.keys

                for (landmarkType in landmarkTypes) {
                    // Build x and y time series for this landmark across all frames
                    val xPoints = mutableListOf<TimeseriesPoint>()
                    val yPoints = mutableListOf<TimeseriesPoint>()

                    for (frame in allPoseFrames) {
                        val lp = frame.landmarks[landmarkType] ?: continue
                        xPoints.add(TimeseriesPoint(frame.timestampMs, lp.x.toDouble()))
                        yPoints.add(TimeseriesPoint(frame.timestampMs, lp.y.toDouble()))
                    }

                    if (xPoints.size > 2) {
                        // Apply RDP to x and y independently
                        val simplifiedX = RdpSimplifier.simplify(xPoints, rdpEpsilon / 1000.0)
                        val simplifiedY = RdpSimplifier.simplify(yPoints, rdpEpsilon / 1000.0)

                        // Interpolate current position from simplified series
                        val smoothedX = RdpSimplifier.interpolateValue(simplifiedX, currentPositionMs)
                        val smoothedY = RdpSimplifier.interpolateValue(simplifiedY, currentPositionMs)

                        if (smoothedX != null && smoothedY != null) {
                            smoothedLandmarks[landmarkType] = LandmarkPosition(
                                x = smoothedX.toFloat(),
                                y = smoothedY.toFloat(),
                                visibility = poseFrame.landmarks[landmarkType]?.visibility ?: 0f
                            )
                        } else {
                            // Fallback to original
                            poseFrame.landmarks[landmarkType]?.let { smoothedLandmarks[landmarkType] = it }
                        }
                    } else {
                        poseFrame.landmarks[landmarkType]?.let { smoothedLandmarks[landmarkType] = it }
                    }
                }

                PoseFrameSnapshot(timestampMs = currentPositionMs, landmarks = smoothedLandmarks)
            } else {
                poseFrame
            }

            // Scale normalized coordinates to the video display area (not full canvas)
            val pixelPositions = effectiveFrame.landmarks.mapValues { (_, lp) ->
                PoseOverlayLandmark(
                    x = offsetX + lp.x * videoDisplayW,
                    y = offsetY + lp.y * videoDisplayH,
                    visibility = lp.visibility
                )
            }
            PoseOverlayRenderer.drawPoseLandmarks(canvas, pixelPositions)
        }

        if (showAngleDisplay) {
            PoseOverlayRenderer.drawAngleOverlay(canvas, angleTextLines)
        }

        if (showAnglePlot && angleTimeSeries.values.any { it.isNotEmpty() }) {
            // Compute RDP-simplified series for the plot
            val simplifiedAngleTimeSeries = angleTimeSeries.mapValues { (_, points) ->
                RdpSimplifier.simplify(points, rdpEpsilon)
            }
            drawAnglePlot(simplifiedAngleTimeSeries, currentPositionMs, totalDurationMs, activeRange, periods)
        }

        if (showBarbellTrace && barbellFrames.isNotEmpty()) {
            // Find nearest barbell frame by timestamp
            val nearestIdx = run {
                val idx = barbellFrames.binarySearchBy(currentPositionMs) { it.timestampMs }
                val insertionPoint = if (idx >= 0) idx else -(idx + 1)
                val candidates = listOfNotNull(
                    barbellFrames.getOrNull(insertionPoint - 1),
                    barbellFrames.getOrNull(insertionPoint)
                )
                if (candidates.isEmpty()) {
                    0
                } else {
                    val nearest = candidates.minByOrNull { kotlin.math.abs(it.timestampMs - currentPositionMs) }
                    barbellFrames.indexOf(nearest).coerceIn(0, barbellFrames.size - 1)
                }
            }

            // Show only the recent trail (last BARBELL_TRAIL_LENGTH frames)
            val trailStart = (nearestIdx - BARBELL_TRAIL_LENGTH + 1).coerceAtLeast(0)
            val trailPositions = barbellFrames.subList(trailStart, nearestIdx + 1).map { frame ->
                BarbellPosition(
                    x = offsetX + frame.x * videoDisplayW,
                    y = offsetY + frame.y * videoDisplayH,
                    radius = (frame.radius * videoDisplayW.coerceAtMost(videoDisplayH)).coerceIn(4f, 16f),
                    confidence = 1f
                )
            }
            BarbellOverlayRenderer.drawBarbellTraceAndPosition(canvas, trailPositions, trailPositions.lastIndex)
        }

        // Draw selectable weight plate candidates
        if (selectableCircles.isNotEmpty()) {
            // Scale normalized coordinates to the video display area
            val scaledCircles = selectableCircles.map { circle ->
                SelectableCircle(
                    x = offsetX + circle.x * videoDisplayW,
                    y = offsetY + circle.y * videoDisplayH,
                    radius = circle.radius * videoDisplayW.coerceAtMost(videoDisplayH),
                    isSelected = circle.isSelected,
                    nearHand = circle.nearHand
                )
            }
            BarbellOverlayRenderer.drawSelectableCircles(canvas, scaledCircles)
        }

        // Draw selectable barbell line candidates
        if (selectableLines.isNotEmpty()) {
            val scaledLines = selectableLines.map { line ->
                SelectableLine(
                    x1 = offsetX + line.x1 * videoDisplayW,
                    y1 = offsetY + line.y1 * videoDisplayH,
                    x2 = offsetX + line.x2 * videoDisplayW,
                    y2 = offsetY + line.y2 * videoDisplayH,
                    isSelected = line.isSelected,
                    nearHand = line.nearHand
                )
            }
            BarbellOverlayRenderer.drawSelectableLines(canvas, scaledLines)
        }
    }
}

@Composable
private fun buildAngleTextLines(angles: Map<String, Double?>): List<String> {
    val lines = mutableListOf<String>()
    angles["spine_angle"]?.let { lines += stringResource(R.string.training_video_overlay_spine_angle, it) }
    angles["left_leg_spine_angle"]?.let { lines += stringResource(R.string.training_video_overlay_left_leg_spine_angle, it) }
    angles["right_leg_spine_angle"]?.let { lines += stringResource(R.string.training_video_overlay_right_leg_spine_angle, it) }
    angles["left_knee_angle"]?.let { lines += stringResource(R.string.training_video_overlay_left_knee_angle, it) }
    angles["right_knee_angle"]?.let { lines += stringResource(R.string.training_video_overlay_right_knee_angle, it) }
    return lines
}

private fun DrawScope.drawAnglePlot(
    angleTimeSeries: Map<String, List<TimeseriesPoint>>,
    currentPositionMs: Long,
    totalDurationMs: Long,
    activeRange: ActiveRange? = null,
    periods: List<PeriodBoundary>? = null
) {
    if (totalDurationMs <= 0L) return

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
        val plotWidth = size.width * 0.45f
        val plotLeft = size.width - plotWidth - 16f
        return plotLeft + fraction * plotWidth
    }

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

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )
    }

    // Draw period boundary markers (vertical dashed lines)
    if (periods != null && periods.isNotEmpty()) {
        val plotLeftPx = plotLeft
        val plotRightPx = plotLeft + plotWidth
        for (period in periods) {
            val x = timestampToX(period.timestampMs)
            // Only draw if within plot bounds
            if (x >= plotLeftPx && x <= plotRightPx) {
                val color = when (period.type) {
                    PeriodBoundaryType.PEAK -> Color(0xFFFFD54F) // Amber
                    PeriodBoundaryType.TROUGH -> Color(0xFF80DEEA) // Teal
                }
                drawLine(
                    color = color,
                    start = Offset(x, plotTop),
                    end = Offset(x, plotTop + plotHeight),
                    strokeWidth = 1f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
                )
            }
        }
    }

    // Draw current position line
    val currentX = timestampToX(currentPositionMs)
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

/**
 * Compute the perpendicular distance from a point (px, py) to a line segment (x1,y1)-(x2,y2).
 */
private fun pointToLineDistance(
    px: Float, py: Float,
    x1: Float, y1: Float,
    x2: Float, y2: Float
): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    val lengthSq = dx * dx + dy * dy
    if (lengthSq == 0f) {
        // Segment is a point
        return kotlin.math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1))
    }

    // Project point onto line, clamp to segment
    var t = ((px - x1) * dx + (py - y1) * dy) / lengthSq
    t = t.coerceIn(0f, 1f)

    val projX = x1 + t * dx
    val projY = y1 + t * dy
    return kotlin.math.sqrt((px - projX) * (px - projX) + (py - projY) * (py - projY))
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
