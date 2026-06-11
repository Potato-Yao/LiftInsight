package com.potato.liftinsight.video

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mlkit.vision.pose.PoseLandmark

internal object PoseOverlayRenderer {

    private val linePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.GREEN
    }

    private val pointPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.RED
    }

    private val spinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.CYAN
    }

    private val spinePointPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.YELLOW
    }

    private val midSpinePointPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.MAGENTA
    }

    private val textBackgroundPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.argb(160, 0, 0, 0)
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.WHITE
        textSize = 34f
    }

    private val OVERLAY_CONNECTIONS = listOf(
        PoseLandmark.NOSE to PoseLandmark.LEFT_EYE_INNER,
        PoseLandmark.LEFT_EYE_INNER to PoseLandmark.LEFT_EYE,
        PoseLandmark.LEFT_EYE to PoseLandmark.LEFT_EYE_OUTER,
        PoseLandmark.LEFT_EYE_OUTER to PoseLandmark.LEFT_EAR,
        PoseLandmark.NOSE to PoseLandmark.RIGHT_EYE_INNER,
        PoseLandmark.RIGHT_EYE_INNER to PoseLandmark.RIGHT_EYE,
        PoseLandmark.RIGHT_EYE to PoseLandmark.RIGHT_EYE_OUTER,
        PoseLandmark.RIGHT_EYE_OUTER to PoseLandmark.RIGHT_EAR,
        PoseLandmark.LEFT_MOUTH to PoseLandmark.RIGHT_MOUTH,
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
        PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
        PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
        PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
        PoseLandmark.LEFT_WRIST to PoseLandmark.LEFT_PINKY,
        PoseLandmark.LEFT_WRIST to PoseLandmark.LEFT_INDEX,
        PoseLandmark.LEFT_WRIST to PoseLandmark.LEFT_THUMB,
        PoseLandmark.LEFT_PINKY to PoseLandmark.LEFT_INDEX,
        PoseLandmark.RIGHT_WRIST to PoseLandmark.RIGHT_PINKY,
        PoseLandmark.RIGHT_WRIST to PoseLandmark.RIGHT_INDEX,
        PoseLandmark.RIGHT_WRIST to PoseLandmark.RIGHT_THUMB,
        PoseLandmark.RIGHT_PINKY to PoseLandmark.RIGHT_INDEX,
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
        PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
        PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
        PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
        PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
        PoseLandmark.LEFT_ANKLE to PoseLandmark.LEFT_HEEL,
        PoseLandmark.LEFT_HEEL to PoseLandmark.LEFT_FOOT_INDEX,
        PoseLandmark.LEFT_ANKLE to PoseLandmark.LEFT_FOOT_INDEX,
        PoseLandmark.RIGHT_ANKLE to PoseLandmark.RIGHT_HEEL,
        PoseLandmark.RIGHT_HEEL to PoseLandmark.RIGHT_FOOT_INDEX,
        PoseLandmark.RIGHT_ANKLE to PoseLandmark.RIGHT_FOOT_INDEX
    )

    fun drawPoseLandmarks(
        canvas: Canvas,
        positions: Map<Int, PoseOverlayLandmark>
    ) {
        val pointRadius = 6f

        positions.forEach { (_, landmark) ->
            canvas.drawCircle(
                landmark.x,
                landmark.y,
                pointRadius,
                pointPaint
            )
        }

        OVERLAY_CONNECTIONS.forEach { (startType, endType) ->
            val start = positions[startType] ?: return@forEach
            val end = positions[endType] ?: return@forEach

            canvas.drawLine(
                start.x,
                start.y,
                end.x,
                end.y,
                linePaint
            )
        }

        val spinePoints = calculateSpinePoints(positions)
        if (spinePoints != null) {
            canvas.drawLine(
                spinePoints.midShoulder.first,
                spinePoints.midShoulder.second,
                spinePoints.midHip.first,
                spinePoints.midHip.second,
                spinePaint
            )
            canvas.drawCircle(
                spinePoints.midShoulder.first,
                spinePoints.midShoulder.second,
                pointRadius + 2f,
                spinePointPaint
            )
            canvas.drawCircle(
                spinePoints.midSpine.first,
                spinePoints.midSpine.second,
                pointRadius + 2f,
                midSpinePointPaint
            )
            canvas.drawCircle(
                spinePoints.midHip.first,
                spinePoints.midHip.second,
                pointRadius + 2f,
                spinePointPaint
            )
        }
    }

    fun drawAngleOverlay(
        canvas: Canvas,
        textLines: List<String>
    ) {
        if (textLines.isEmpty()) return

        val padding = 20f
        val lineSpacing = 12f
        val fontMetrics = textPaint.fontMetrics
        val lineHeight = fontMetrics.bottom - fontMetrics.top
        val backgroundHeight = padding * 2 + lineHeight * textLines.size + lineSpacing * (textLines.size - 1)

        canvas.drawRect(
            0f,
            0f,
            canvas.width.toFloat(),
            backgroundHeight,
            textBackgroundPaint
        )

        var y = padding - fontMetrics.top
        textLines.forEach { line ->
            canvas.drawText(line, padding, y, textPaint)
            y += lineHeight + lineSpacing
        }
    }
}
