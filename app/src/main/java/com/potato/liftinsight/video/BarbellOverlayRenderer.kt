package com.potato.liftinsight.video

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

internal data class BarbellPosition(
    val x: Float,
    val y: Float,
    val radius: Float,
    val confidence: Float
)

internal object BarbellOverlayRenderer {

    private val barbellPointPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.argb(230, 255, 109, 0) // Orange
    }

    private val barbellCenterPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val tracePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun drawBarbellPosition(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float
    ) {
        val outerRadius = radius.coerceIn(4f, 16f)
        canvas.drawCircle(x, y, outerRadius, barbellPointPaint)
        canvas.drawCircle(x, y, outerRadius * 0.4f, barbellCenterPaint)
    }

    fun drawBarbellTrace(
        canvas: Canvas,
        positions: List<BarbellPosition>,
        currentIndex: Int
    ) {
        if (positions.isEmpty() || currentIndex < 0) return

        val endIndex = currentIndex.coerceAtMost(positions.size - 1)
        val startIndex = 0

        for (i in startIndex until endIndex) {
            val alpha = computeAlpha(i, startIndex, endIndex)
            tracePaint.color = Color.argb(alpha, 255, 109, 0)
            canvas.drawLine(
                positions[i].x,
                positions[i].y,
                positions[i + 1].x,
                positions[i + 1].y,
                tracePaint
            )
        }
    }

    fun drawBarbellTraceAndPosition(
        canvas: Canvas,
        positions: List<BarbellPosition>,
        currentIndex: Int
    ) {
        if (positions.isEmpty() || currentIndex < 0) return

        drawBarbellTrace(canvas, positions, currentIndex)

        val current = positions[currentIndex.coerceAtMost(positions.size - 1)]
        drawBarbellPosition(canvas, current.x, current.y, current.radius)
    }

    private fun computeAlpha(index: Int, startIndex: Int, endIndex: Int): Int {
        if (endIndex <= startIndex) return 230
        val fraction = (index - startIndex).toFloat() / (endIndex - startIndex).toFloat()
        val minAlpha = 50
        val maxAlpha = 230
        return (minAlpha + fraction * (maxAlpha - minAlpha)).toInt().coerceIn(minAlpha, maxAlpha)
    }
}
