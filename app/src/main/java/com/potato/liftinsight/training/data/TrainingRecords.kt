package com.potato.liftinsight.training.data

import com.potato.liftinsight.video.imported.ImportedVideoAnalysisMode
import com.potato.liftinsight.video.imported.ImportedVideoSource

enum class MotionType(val displayName: String) {
    MACHINE_COMPOUND("Machine Compound"),
    FREE_WEIGHT_COMPOUND("Free-Weight Compound"),
    BARBELL("Barbell"),
    STATIC("Static"),
    SELF_WEIGHT("Self-Weight")
}

data class MotionRecord(
    val id: Int,
    val name: String,
    val type: MotionType
)

data class CreateMotionRequest(
    val name: String? = null,
    val type: MotionType = MotionType.BARBELL
)

data class MetaPlanRecord(
    val id: Int,
    val motionId: Int,
    val motionName: String,
    val dayIndex: Int = 1,
    val sets: Int,
    val reps: Int,
    val intensity: Double,
    val weight: Double,
    val orderIndex: Int
)

data class CreateMetaPlanRequest(
    val motionId: Int,
    val dayIndex: Int = 1,
    val sets: Int,
    val reps: Int,
    val intensity: Double,
    val weight: Double,
    val orderIndex: Int
)

data class PlanRecord(
    val id: Int,
    val name: String,
    val cyclePeriod: Int,
    val currentIndex: Int = 1,
    val lastAppliedAt: Long = 0L,
    val metaPlans: List<MetaPlanRecord> = emptyList()
)

data class CreatePlanRequest(
    val name: String? = null,
    val cyclePeriod: Int,
    val currentIndex: Int = 1,
    val lastAppliedAt: Long = 0L,
    val metaPlans: List<CreateMetaPlanRequest> = emptyList()
)

data class CreateMetaHistoryRequest(
    val date: String,
    val rep: Int,
    val rpe: Int,
    val weight: Double,
    val motionId: Int,
    val videoName: String? = null,
    val videoSource: ImportedVideoSource = ImportedVideoSource.CAMERA_CAPTURE,
    val importedVideoAnalysisMode: ImportedVideoAnalysisMode = ImportedVideoAnalysisMode.ESTIMATED,
    val importedReferenceLabel: String = "",
    val importedReferencePixelDistance: Double? = null,
    val importedReferenceDistanceMeters: Double? = null,
    val historyId: Int? = null
)

enum class VideoProcessState {
    NOT_STARTED,
    PROCESSING,
    DONE,
    ERROR
}

data class MetaHistoryRecord(
    val id: Int,
    val date: String,
    val rep: Int,
    val rpe: Int,
    val weight: Double,
    val motionId: Int,
    val motionName: String,
    val videoName: String? = null,
    val videoSource: ImportedVideoSource = ImportedVideoSource.CAMERA_CAPTURE,
    val importedVideoAnalysisMode: ImportedVideoAnalysisMode = ImportedVideoAnalysisMode.ESTIMATED,
    val importedReferenceLabel: String = "",
    val importedReferencePixelDistance: Double? = null,
    val importedReferenceDistanceMeters: Double? = null,
    val poseDetection: Boolean = false,
    val angleDisplay: Boolean = false,
    val anglePlot: Boolean = false,
    val barbellDetection: Boolean = false,
    val powerCalculation: Boolean = false,
    val marked: Boolean = false,
    val historyId: Int? = null
)

data class HistoryRecord(
    val id: Int,
    val planId: Int,
    val planName: String,
    val startTime: Long,
    val endTime: Long,
    val intensity: Int,
    val dayIndex: Int = 0
)

data class CreateHistoryRequest(
    val planId: Int,
    val startTime: Long,
    val endTime: Long,
    val intensity: Int = 0,
    val dayIndex: Int = 0
)

