package com.potato.liftinsight.video.imported

import kotlin.math.acos
import kotlin.math.sqrt

enum class ImportedVideoSource {
    LOCAL_FILE,
    CAMERA_CAPTURE
}

enum class ImportedVideoAnalysisMode {
    ESTIMATED,
    REFERENCE_CALIBRATED
}

data class ImportedVideoCalibration(
    val pixelDistance: Double,
    val realDistanceMeters: Double,
    val referenceLabel: String = ""
) {
    val metersPerPixel: Double
        get() {
            if (pixelDistance <= 0.0 || realDistanceMeters <= 0.0) {
                return 0.0
            }

            return realDistanceMeters / pixelDistance
        }
}

data class VideoPoint2D(
    val x: Double,
    val y: Double
)

data class VideoPoint3D(
    val x: Double,
    val y: Double,
    val z: Double
)

object ImportedVideoGeometry {
    fun pixelDistance(first: VideoPoint2D, second: VideoPoint2D): Double {
        val dx = second.x - first.x
        val dy = second.y - first.y

        return sqrt((dx * dx) + (dy * dy))
    }

    fun calibratedDistanceMeters(
        first: VideoPoint2D,
        second: VideoPoint2D,
        calibration: ImportedVideoCalibration
    ): Double? {
        val metersPerPixel = calibration.metersPerPixel

        if (metersPerPixel <= 0.0) {
            return null
        }

        return pixelDistance(first, second) * metersPerPixel
    }

    fun angleDegrees(
        first: VideoPoint3D,
        center: VideoPoint3D,
        third: VideoPoint3D
    ): Double? {
        val firstVectorX = first.x - center.x
        val firstVectorY = first.y - center.y
        val firstVectorZ = first.z - center.z
        val thirdVectorX = third.x - center.x
        val thirdVectorY = third.y - center.y
        val thirdVectorZ = third.z - center.z

        val firstMagnitude = sqrt(
            (firstVectorX * firstVectorX) +
                (firstVectorY * firstVectorY) +
                (firstVectorZ * firstVectorZ)
        )
        val thirdMagnitude = sqrt(
            (thirdVectorX * thirdVectorX) +
                (thirdVectorY * thirdVectorY) +
                (thirdVectorZ * thirdVectorZ)
        )

        if (firstMagnitude == 0.0 || thirdMagnitude == 0.0) {
            return null
        }

        val dot =
            (firstVectorX * thirdVectorX) +
                (firstVectorY * thirdVectorY) +
                (firstVectorZ * thirdVectorZ)
        val cosine = (dot / (firstMagnitude * thirdMagnitude)).coerceIn(-1.0, 1.0)

        return Math.toDegrees(acos(cosine))
    }
}
