package com.potato.liftinsight.video

import android.graphics.Bitmap
import android.graphics.Color
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
class BarbellDetectionServiceLineTest {
    private val logger = RecordingAppLogger()
    private val service = BarbellDetectionService(logger)

    @Test
    fun `DetectedLine data class has correct properties`() {
        val line = DetectedLine(
            x1 = 10f, y1 = 100f,
            x2 = 200f, y2 = 105f,
            centerX = 105f, centerY = 102.5f,
            angle = 1.5f,
            length = 190f,
            confidence = 0.9f
        )

        assertEquals(10f, line.x1)
        assertEquals(100f, line.y1)
        assertEquals(200f, line.x2)
        assertEquals(105f, line.y2)
        assertEquals(105f, line.centerX)
        assertEquals(102.5f, line.centerY)
        assertEquals(1.5f, line.angle)
        assertEquals(190f, line.length)
        assertEquals(0.9f, line.confidence)
    }

    @Test
    fun `BarbellDetectionResult Line variant wraps DetectedLine`() {
        val line = DetectedLine(
            x1 = 0f, y1 = 0f, x2 = 100f, y2 = 0f,
            centerX = 50f, centerY = 0f,
            angle = 0f, length = 100f, confidence = 0.8f
        )
        val result = BarbellDetectionResult.Line(line)

        assertTrue(result is BarbellDetectionResult.Line)
        val extracted = (result as BarbellDetectionResult.Line).line
        assertEquals(100f, extracted.length)
        assertEquals(0.8f, extracted.confidence)
    }

    @Test
    fun `BarbellDetectionResult Circle variant wraps DetectedCircle`() {
        val circle = DetectedCircle(x = 50f, y = 60f, radius = 30f, confidence = 0.9f)
        val result = BarbellDetectionResult.Circle(circle)

        assertTrue(result is BarbellDetectionResult.Circle)
        val extracted = (result as BarbellDetectionResult.Circle).circle
        assertEquals(50f, extracted.x)
        assertEquals(60f, extracted.y)
        assertEquals(30f, extracted.radius)
    }

    @Test
    fun `detectBarbellLine returns null when no hand landmarks provided`() {
        service.initialize()

        val bitmap = createTestBitmap(200, 200)
        val result = service.detectBarbellLine(bitmap, null)

        assertNull(result)

        bitmap.recycle()
    }

    @Test
    fun `detectBarbellLine returns null when wrists have low visibility`() {
        service.initialize()

        val bitmap = createTestBitmap(200, 200)
        val handLandmarks = mapOf(
            com.google.mlkit.vision.pose.PoseLandmark.LEFT_WRIST to PoseOverlayLandmark(
                x = 50f, y = 100f, visibility = 0.2f
            ),
            com.google.mlkit.vision.pose.PoseLandmark.RIGHT_WRIST to PoseOverlayLandmark(
                x = 150f, y = 100f, visibility = 0.1f
            )
        )

        val result = service.detectBarbellLine(bitmap, handLandmarks)

        assertNull(result)

        bitmap.recycle()
    }

    @Test
    fun `detectBarbellLine returns null when no wrists present`() {
        service.initialize()

        val bitmap = createTestBitmap(200, 200)
        val handLandmarks = mapOf(
            com.google.mlkit.vision.pose.PoseLandmark.LEFT_SHOULDER to PoseOverlayLandmark(
                x = 100f, y = 50f, visibility = 1.0f
            )
        )

        val result = service.detectBarbellLine(bitmap, handLandmarks)

        assertNull(result)

        bitmap.recycle()
    }

    @Test
    fun `detectBarbellLine with single high-visibility wrist does not crash`() {
        service.initialize()

        val bitmap = createTestBitmap(400, 300)
        val handLandmarks = mapOf(
            com.google.mlkit.vision.pose.PoseLandmark.LEFT_WRIST to PoseOverlayLandmark(
                x = 200f, y = 150f, visibility = 1.0f
            )
        )

        // Should not crash — returns null or DetectedLine
        val result = service.detectBarbellLine(bitmap, handLandmarks)
        // Even if no line is found, it should not throw
        assertTrue(result == null || result is DetectedLine)

        bitmap.recycle()
    }

    @Test
    fun `detectCirclesLoose returns empty list on solid black image`() {
        service.initialize()

        val bitmap = createTestBitmap(200, 200)
        val result = service.detectCirclesLoose(bitmap)

        assertNotNull(result)
        assertTrue(result.isEmpty())

        bitmap.recycle()
    }

    @Test
    fun `selectableLine data class has correct properties`() {
        val line = SelectableLine(
            x1 = 10f, y1 = 20f, x2 = 30f, y2 = 40f,
            isSelected = true, nearHand = true
        )

        assertEquals(10f, line.x1)
        assertEquals(20f, line.y1)
        assertEquals(30f, line.x2)
        assertEquals(40f, line.y2)
        assertTrue(line.isSelected)
        assertTrue(line.nearHand)
    }

    @Test
    fun `detectBarbellHybrid returns null on empty image with no landmarks`() {
        service.initialize()

        val bitmap = createTestBitmap(100, 100)
        val result = service.detectBarbellHybrid(bitmap, null)

        // On a solid black small image, neither line nor circle detection should find anything
        assertNull(result)

        bitmap.recycle()
    }

    @Test
    fun `detectBarbellHybrid with landmarks on empty image gracefully returns null`() {
        service.initialize()

        val bitmap = createTestBitmap(300, 300)
        val handLandmarks = mapOf(
            com.google.mlkit.vision.pose.PoseLandmark.LEFT_WRIST to PoseOverlayLandmark(
                x = 100f, y = 150f, visibility = 1.0f
            ),
            com.google.mlkit.vision.pose.PoseLandmark.RIGHT_WRIST to PoseOverlayLandmark(
                x = 200f, y = 150f, visibility = 1.0f
            )
        )

        val result = service.detectBarbellHybrid(bitmap, handLandmarks)
        // No real edges in the image, so should return null
        assertNull(result)

        bitmap.recycle()
    }

    private fun createTestBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        return bitmap
    }
}
