package com.potato.liftinsight.training.data

import com.potato.liftinsight.video.imported.ImportedVideoAnalysisMode
import com.potato.liftinsight.video.imported.ImportedVideoCalibration
import com.potato.liftinsight.video.imported.ImportedVideoSource

data class ImportedVideoMetadataRecord(
    val historyId: Int,
    val videoSource: ImportedVideoSource,
    val analysisMode: ImportedVideoAnalysisMode,
    val referenceLabel: String,
    val referencePixelDistance: Double?,
    val referenceDistanceMeters: Double?
) {
    val calibration: ImportedVideoCalibration?
        get() {
            val pixelDistance = referencePixelDistance
            val distanceMeters = referenceDistanceMeters

            if (pixelDistance == null || distanceMeters == null) {
                return null
            }

            return ImportedVideoCalibration(
                pixelDistance = pixelDistance,
                realDistanceMeters = distanceMeters,
                referenceLabel = referenceLabel
            )
        }
}

data class UpdateImportedVideoMetadataRequest(
    val historyId: Int,
    val videoSource: ImportedVideoSource,
    val analysisMode: ImportedVideoAnalysisMode,
    val referenceLabel: String = "",
    val referencePixelDistance: Double? = null,
    val referenceDistanceMeters: Double? = null
)
