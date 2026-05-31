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

        val angles = calculateOverlayAngles(landmarks, calculateSpinePoints(landmarks))

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

        val angles = calculateOverlayAngles(landmarks, calculateSpinePoints(landmarks))

        assertNull(angles.leftLegSpineAngle)
        assertNull(angles.leftKneeAngle)
    }

    private fun landmark(
        x: Float,
        y: Float,
        visibility: Float = 1f
    ): PoseOverlayLandmark {
        return PoseOverlayLandmark(x = x, y = y, visibility = visibility)
    }
}
