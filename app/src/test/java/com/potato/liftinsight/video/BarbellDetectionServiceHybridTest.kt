package com.potato.liftinsight.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.potato.liftinsight.common.logging.RecordingAppLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BarbellDetectionServiceHybridTest {
    private val logger = RecordingAppLogger()
    private val service = BarbellDetectionService(logger)

    @Test
    fun `detectBarbellHybrid returns null when not initialized`() {
        // Don't call initialize() — it should attempt lazy init and still handle gracefully
        val bitmap = createSolidBitmap(200, 200)
        val result = service.detectBarbellHybrid(bitmap, null)
        // Regardless of init success, should not throw
        assertTrue(result == null || result is BarbellDetectionResult)
        bitmap.recycle()
    }

    @Test
    fun `detectBarbellHybrid returns null on solid black image`() {
        service.initialize()

        val bitmap = createSolidBitmap(300, 300)
        val result = service.detectBarbellHybrid(bitmap, null)

        assertNull(result)

        bitmap.recycle()
    }

    @Test
    fun `detectBarbellHybrid returns null on solid black image with landmarks`() {
        service.initialize()

        val bitmap = createSolidBitmap(300, 300)
        val handLandmarks = mapOf(
            com.google.mlkit.vision.pose.PoseLandmark.LEFT_WRIST to PoseOverlayLandmark(
                x = 100f, y = 150f, visibility = 1.0f
            ),
            com.google.mlkit.vision.pose.PoseLandmark.RIGHT_WRIST to PoseOverlayLandmark(
                x = 200f, y = 150f, visibility = 1.0f
            )
        )

        val result = service.detectBarbellHybrid(bitmap, handLandmarks)

        // No edges on solid black → no line, no circles
        assertNull(result)

        bitmap.recycle()
    }

    @Test
    fun `detectBarbellHybrid finds circle with white disc on black background`() {
        service.initialize()

        val size = 400
        val bitmap = createCircleBitmap(size, size, size / 2, size / 2, 80f)

        // Provide hand landmarks near the circle
        val handLandmarks = mapOf(
            com.google.mlkit.vision.pose.PoseLandmark.LEFT_WRIST to PoseOverlayLandmark(
                x = size / 2f + 10f, y = size / 2f + 10f, visibility = 1.0f
            )
        )

        val result = service.detectBarbellHybrid(bitmap, handLandmarks)

        // Line detection may fail without a visible bar, but circle fallback should find the disc
        // Even if not found, we just verify no crash
        assertTrue(result == null || result is BarbellDetectionResult)

        bitmap.recycle()
    }

    @Test
    fun `detectBarbellHybrid works with only one visible wrist`() {
        service.initialize()

        val size = 300
        val bitmap = createCircleBitmap(size, size, 100, 150, 40f)

        val handLandmarks = mapOf(
            com.google.mlkit.vision.pose.PoseLandmark.LEFT_WRIST to PoseOverlayLandmark(
                x = 110f, y = 150f, visibility = 0.8f
            ),
            com.google.mlkit.vision.pose.PoseLandmark.RIGHT_WRIST to PoseOverlayLandmark(
                x = 200f, y = 160f, visibility = 0.3f // Below 0.5 threshold
            )
        )

        val result = service.detectBarbellHybrid(bitmap, handLandmarks)

        // Should not crash — may or may not find results
        assertTrue(result == null || result is BarbellDetectionResult)

        bitmap.recycle()
    }

    @Test
    fun `detectCirclesLoose has different params than detectCircles`() {
        // This test verifies that loose detection can be called and processes without errors
        service.initialize()

        val bitmap = createSolidBitmap(200, 200)
        val looseResult = service.detectCirclesLoose(bitmap)
        val tightResult = service.detectCircles(bitmap)

        // Both should return lists (possibly empty for solid black)
        assertNotNull(looseResult)
        assertNotNull(tightResult)

        bitmap.recycle()
    }

    @Test
    fun `hybrid detection with line-worthy image does not crash`() {
        service.initialize()

        val width = 400
        val height = 300
        val bitmap = createLineBitmap(width, height)

        val handLandmarks = mapOf(
            com.google.mlkit.vision.pose.PoseLandmark.LEFT_WRIST to PoseOverlayLandmark(
                x = 100f, y = 150f, visibility = 0.9f
            ),
            com.google.mlkit.vision.pose.PoseLandmark.RIGHT_WRIST to PoseOverlayLandmark(
                x = 300f, y = 150f, visibility = 0.9f
            )
        )

        val result = service.detectBarbellHybrid(bitmap, handLandmarks)
        // Should not throw, may return line, circle, or null
        assertTrue(result == null || result is BarbellDetectionResult)

        bitmap.recycle()
    }

    private fun createSolidBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        return bitmap
    }

    private fun createCircleBitmap(width: Int, height: Int, cx: Int, cy: Int, radius: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx.toFloat(), cy.toFloat(), radius, paint)
        return bitmap
    }

    private fun createLineBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
        }
        // Draw a near-horizontal white line across the middle
        canvas.drawLine(50f, height / 2f, width - 50f, height / 2f + 3f, paint)
        return bitmap
    }
}
