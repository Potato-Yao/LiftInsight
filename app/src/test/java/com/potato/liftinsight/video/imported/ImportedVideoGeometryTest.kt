package com.potato.liftinsight.video.imported

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportedVideoGeometryTest {
    @Test
    fun calibratedDistanceMeters_usesReferenceScale() {
        val calibration = ImportedVideoCalibration(
            pixelDistance = 200.0,
            realDistanceMeters = 0.5,
            referenceLabel = "Plate"
        )

        val measuredDistance = ImportedVideoGeometry.calibratedDistanceMeters(
            first = VideoPoint2D(10.0, 10.0),
            second = VideoPoint2D(110.0, 10.0),
            calibration = calibration
        )

        assertEquals(0.25, measuredDistance ?: Double.NaN, 0.0001)
    }

    @Test
    fun calibratedDistanceMeters_returnsNullWhenCalibrationIsInvalid() {
        val calibration = ImportedVideoCalibration(
            pixelDistance = 0.0,
            realDistanceMeters = 0.5
        )

        val measuredDistance = ImportedVideoGeometry.calibratedDistanceMeters(
            first = VideoPoint2D(10.0, 10.0),
            second = VideoPoint2D(110.0, 10.0),
            calibration = calibration
        )

        assertNull(measuredDistance)
    }

    @Test
    fun angleDegrees_returnsExpectedJointAngle() {
        val angle = ImportedVideoGeometry.angleDegrees(
            first = VideoPoint3D(0.0, 1.0, 0.0),
            center = VideoPoint3D(0.0, 0.0, 0.0),
            third = VideoPoint3D(1.0, 0.0, 0.0)
        )

        assertEquals(90.0, angle ?: Double.NaN, 0.001)
    }
}
