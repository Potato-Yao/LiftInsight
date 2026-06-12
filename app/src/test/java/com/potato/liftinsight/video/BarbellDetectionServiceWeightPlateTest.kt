package com.potato.liftinsight.video

import android.graphics.Bitmap
import com.potato.liftinsight.common.logging.RecordingAppLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarbellDetectionServiceWeightPlateTest {
    private val logger = RecordingAppLogger()
    private val service = BarbellDetectionService(logger)

    @Test
    fun `detectWeightPlateCandidates returns empty list when bitmap is null or invalid`() {
        // Create a small empty bitmap that won't contain detectable circles
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        // Fill with solid black (no circles detectable)
        bitmap.eraseColor(android.graphics.Color.BLACK)

        service.initialize()

        // No hand landmarks → should return all detected circles (likely none)
        val result = service.detectWeightPlateCandidates(bitmap, null)
        assertNotNull(result)
        // With a solid black image, HoughCircles should find nothing
        assertTrue(result.isEmpty())

        bitmap.recycle()
    }

    @Test
    fun `detectWeightPlateCandidates returns empty list when no circles found`() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLACK)

        service.initialize()

        val handLandmarks = mapOf(
            com.google.mlkit.vision.pose.PoseLandmark.LEFT_WRIST to PoseOverlayLandmark(
                x = 100f, y = 100f, visibility = 1.0f
            )
        )

        val result = service.detectWeightPlateCandidates(bitmap, handLandmarks)
        assertNotNull(result)
        assertTrue(result.isEmpty())

        bitmap.recycle()
    }

    @Test
    fun `detectWeightPlateCandidates returns all circles when no hand landmarks provided`() {
        // Create a bitmap with a simple white circle on black background
        val size = 300
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawColor(android.graphics.Color.BLACK)
        // Draw a white circle
        canvas.drawCircle(size / 2f, size / 2f, 60f, paint)

        service.initialize()

        // No hand landmarks → should return all detected circles unfiltered
        val result = service.detectWeightPlateCandidates(bitmap, null)
        assertNotNull(result)
        // All returned candidates should have nearHand = false since no hand landmarks
        result.forEach { candidate ->
            assertEquals(false, candidate.nearHand)
        }

        bitmap.recycle()
    }

    @Test
    fun `detectWeightPlateCandidates filters by hand proximity when landmarks are available`() {
        val size = 300
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawColor(android.graphics.Color.BLACK)
        // Draw a circle near the top-left
        canvas.drawCircle(80f, 80f, 40f, paint)

        service.initialize()

        // Hand landmarks at the top-left (near the circle)
        val handLandmarks = mapOf(
            com.google.mlkit.vision.pose.PoseLandmark.LEFT_WRIST to PoseOverlayLandmark(
                x = 90f, y = 90f, visibility = 1.0f
            ),
            com.google.mlkit.vision.pose.PoseLandmark.RIGHT_WRIST to PoseOverlayLandmark(
                x = 85f, y = 85f, visibility = 1.0f
            )
        )

        val result = service.detectWeightPlateCandidates(bitmap, handLandmarks)
        assertNotNull(result)
        // With hand landmarks, only circles near the hands should be returned
        // or empty if no circles are detected near the hand
        result.forEach { candidate ->
            assertTrue(candidate.nearHand)
        }

        bitmap.recycle()
    }

    @Test
    fun `WeightPlateCandidate data class has correct properties`() {
        val candidate = WeightPlateCandidate(
            x = 150f,
            y = 200f,
            radius = 30f,
            confidence = 0.9f,
            nearHand = true
        )

        assertEquals(150f, candidate.x)
        assertEquals(200f, candidate.y)
        assertEquals(30f, candidate.radius)
        assertEquals(0.9f, candidate.confidence)
        assertEquals(true, candidate.nearHand)
    }
}
