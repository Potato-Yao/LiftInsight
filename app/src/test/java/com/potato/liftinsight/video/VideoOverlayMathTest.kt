package com.potato.liftinsight.video

import com.google.mlkit.vision.pose.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoOverlayMathTest {
    @Test
    fun calculateSpinePoints_returnsMidShoulderMidSpineAndMidHip() {
        val landmarks = mapOf(
            PoseLandmark.LEFT_SHOULDER to landmark(2f, 2f),
            PoseLandmark.RIGHT_SHOULDER to landmark(6f, 2f),
            PoseLandmark.LEFT_HIP to landmark(2f, 10f),
            PoseLandmark.RIGHT_HIP to landmark(6f, 10f)
        )

        val spinePoints = calculateSpinePoints(landmarks)

        assertEquals(4f, spinePoints?.midShoulder?.first)
        assertEquals(2f, spinePoints?.midShoulder?.second)
        assertEquals(4f, spinePoints?.midSpine?.first)
        assertEquals(6f, spinePoints?.midSpine?.second)
        assertEquals(4f, spinePoints?.midHip?.first)
        assertEquals(10f, spinePoints?.midHip?.second)
    }

    @Test
    fun calculateOverlayAngles_matchesPortedSpineLegAndKneeMath() {
        val landmarks = mapOf(
            PoseLandmark.LEFT_SHOULDER to landmark(2f, 2f),
            PoseLandmark.RIGHT_SHOULDER to landmark(6f, 2f),
            PoseLandmark.LEFT_HIP to landmark(2f, 10f),
            PoseLandmark.RIGHT_HIP to landmark(6f, 10f),
            PoseLandmark.LEFT_KNEE to landmark(2f, 12f),
            PoseLandmark.RIGHT_KNEE to landmark(6f, 12f),
            PoseLandmark.LEFT_ANKLE to landmark(4f, 12f),
            PoseLandmark.RIGHT_ANKLE to landmark(8f, 12f)
        )

        val angles = calculateOverlayAngles(
            landmarks = landmarks,
            spinePoints = calculateSpinePoints(landmarks),
            frameWidth = 100f,
            frameHeight = 100f
        )

        assertEquals(0.0, angles.spineAngle ?: Double.NaN, 0.01)
        assertEquals(135.0, angles.leftLegSpineAngle ?: Double.NaN, 0.01)
        assertEquals(135.0, angles.rightLegSpineAngle ?: Double.NaN, 0.01)
        assertEquals(90.0, angles.leftKneeAngle ?: Double.NaN, 0.01)
        assertEquals(90.0, angles.rightKneeAngle ?: Double.NaN, 0.01)
    }

    @Test
    fun calculateOverlayAngles_skipsAnglesWhenVisibilityIsTooLow() {
        val landmarks = mapOf(
            PoseLandmark.LEFT_SHOULDER to landmark(2f, 2f),
            PoseLandmark.RIGHT_SHOULDER to landmark(6f, 2f),
            PoseLandmark.LEFT_HIP to landmark(2f, 10f),
            PoseLandmark.RIGHT_HIP to landmark(6f, 10f),
            PoseLandmark.LEFT_KNEE to landmark(2f, 12f, visibility = 0.2f),
            PoseLandmark.LEFT_ANKLE to landmark(4f, 12f)
        )

        val angles = calculateOverlayAngles(
            landmarks = landmarks,
            spinePoints = calculateSpinePoints(landmarks),
            frameWidth = 100f,
            frameHeight = 100f
        )

        assertNull(angles.leftLegSpineAngle)
        assertNull(angles.leftKneeAngle)
    }

    @Test
    fun calculateOverlayAngles_scalesVectorsByFrameDimensions() {
        val landmarks = mapOf(
            PoseLandmark.LEFT_SHOULDER to landmark(0.45f, 0.2f),
            PoseLandmark.RIGHT_SHOULDER to landmark(0.55f, 0.2f),
            PoseLandmark.LEFT_HIP to landmark(0.48f, 0.5f),
            PoseLandmark.RIGHT_HIP to landmark(0.52f, 0.5f),
            PoseLandmark.LEFT_KNEE to landmark(0.56f, 0.68f),
            PoseLandmark.LEFT_ANKLE to landmark(0.6f, 0.88f)
        )

        val squareAngles = calculateOverlayAngles(
            landmarks = landmarks,
            spinePoints = calculateSpinePoints(landmarks),
            frameWidth = 100f,
            frameHeight = 100f
        )
        val wideAngles = calculateOverlayAngles(
            landmarks = landmarks,
            spinePoints = calculateSpinePoints(landmarks),
            frameWidth = 200f,
            frameHeight = 100f
        )

        val squareAngle = squareAngles.leftKneeAngle ?: Double.NaN
        val wideAngle = wideAngles.leftKneeAngle ?: Double.NaN

        assertEquals(true, squareAngle.isFinite())
        assertEquals(true, wideAngle.isFinite())
        assertEquals(false, kotlin.math.abs(wideAngle - squareAngle) < 0.001)
    }

    private fun landmark(
        x: Float,
        y: Float,
        visibility: Float = 1f
    ): PoseOverlayLandmark {
        return PoseOverlayLandmark(x = x, y = y, visibility = visibility)
    }
}
