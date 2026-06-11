package com.potato.liftinsight.training.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.potato.liftinsight.video.imported.ImportedVideoAnalysisMode
import com.potato.liftinsight.video.imported.ImportedVideoSource

@Entity(
    tableName = "motion",
    indices = [Index(value = ["name"], unique = true)]
)
data class MotionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val type: String = MotionType.BARBELL.name
)

@Entity(tableName = "plan")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    @ColumnInfo(name = "cycle_period")
    val cyclePeriod: Int,
    @ColumnInfo(name = "current_index")
    val currentIndex: Int = 0,
    @ColumnInfo(name = "last_applied_at")
    val lastAppliedAt: Long = 0L
)

@Entity(
    tableName = "plan_selection",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["current_plan_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["current_plan_id"])]
)
data class PlanSelectionEntity(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "current_plan_id")
    val currentPlanId: Int? = null,
    @ColumnInfo(name = "current_day_epoch")
    val currentDayEpoch: Long? = null
)

@Entity(tableName = "workout_session")
data class WorkoutSessionEntity(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "is_workout_going")
    val isWorkoutGoing: Boolean = false,
    @ColumnInfo(name = "is_paused")
    val isPaused: Boolean = false,
    @ColumnInfo(name = "started_at")
    val startedAt: Long = 0L,
    @ColumnInfo(name = "last_resumed_at")
    val lastResumedAt: Long = 0L,
    @ColumnInfo(name = "elapsed_before_pause_ms")
    val elapsedBeforePauseMs: Long = 0L
)

@Entity(tableName = "workout_progress")
data class WorkoutProgressEntity(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "plan_id")
    val planId: Int,
    @ColumnInfo(name = "plan_day_index")
    val planDayIndex: Int,
    @ColumnInfo(name = "next_set_index")
    val nextSetIndex: Int = 0,
    @ColumnInfo(name = "active_set_index")
    val activeSetIndex: Int? = null,
    @ColumnInfo(name = "total_set_count")
    val totalSetCount: Int,
    @ColumnInfo(name = "break_ends_at")
    val breakEndsAt: Long = 0L,
    @ColumnInfo(name = "is_finished")
    val isFinished: Boolean = false,
    @ColumnInfo(name = "completed_elapsed_time_ms")
    val completedElapsedTimeMs: Long = 0L,
    @ColumnInfo(name = "active_history_id")
    val activeHistoryId: Int? = null,
    @ColumnInfo(name = "workout_intensity")
    val workoutIntensity: Int? = null
)

@Entity(
    tableName = "metaplan",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MotionEntity::class,
            parentColumns = ["id"],
            childColumns = ["motion_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["plan_id"]),
        Index(value = ["motion_id"]),
        Index(value = ["plan_id", "day_index", "order_index"], unique = true)
    ]
)
data class MetaPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "plan_id")
    val planId: Int,
    @ColumnInfo(name = "motion_id")
    val motionId: Int,
    @ColumnInfo(name = "day_index")
    val dayIndex: Int,
    val sets: Int,
    val reps: Int,
    val intensity: Double,
    val weight: Double,
    @ColumnInfo(name = "order_index")
    val orderIndex: Int
)

@Entity(
    tableName = "history",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["plan_id"])]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "plan_id")
    val planId: Int,
    @ColumnInfo(name = "start_time")
    val startTime: Long,
    @ColumnInfo(name = "end_time")
    val endTime: Long,
    val intensity: Int = 0,
    @ColumnInfo(name = "day_index")
    val dayIndex: Int = 0
)

@Entity(
    tableName = "metahistory",
    foreignKeys = [
        ForeignKey(
            entity = MotionEntity::class,
            parentColumns = ["id"],
            childColumns = ["motion_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = HistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["history_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["motion_id"]),
        Index(value = ["history_id"])
    ]
)
data class MetaHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String,
    val rep: Int,
    val rpe: Int,
    val weight: Double,
    @ColumnInfo(name = "motion_id")
    val motionId: Int,
    @ColumnInfo(name = "video_name")
    val videoName: String? = null,
    @ColumnInfo(name = "video_source")
    val videoSource: String = ImportedVideoSource.CAMERA_CAPTURE.name,
    @ColumnInfo(name = "imported_video_analysis_mode")
    val importedVideoAnalysisMode: String = ImportedVideoAnalysisMode.ESTIMATED.name,
    @ColumnInfo(name = "imported_reference_label")
    val importedReferenceLabel: String = "",
    @ColumnInfo(name = "imported_reference_pixel_distance")
    val importedReferencePixelDistance: Double? = null,
    @ColumnInfo(name = "imported_reference_distance_meters")
    val importedReferenceDistanceMeters: Double? = null,
    @ColumnInfo(name = "pose_detection", defaultValue = "0")
    val poseDetection: Boolean = false,
    @ColumnInfo(name = "angle_display", defaultValue = "0")
    val angleDisplay: Boolean = false,
    @ColumnInfo(name = "angle_plot", defaultValue = "0")
    val anglePlot: Boolean = false,
    @ColumnInfo(name = "barbell_detection", defaultValue = "0")
    val barbellDetection: Boolean = false,
    @ColumnInfo(name = "power_calculation", defaultValue = "0")
    val powerCalculation: Boolean = false,
    @ColumnInfo(name = "marked", defaultValue = "0")
    val marked: Boolean = false,
    @ColumnInfo(name = "history_id")
    val historyId: Int? = null
)

@Entity(
    tableName = "video_process_state",
    indices = [Index(value = ["video_name"], unique = true)]
)
data class VideoProcessStateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "video_name")
    val videoName: String,
    val state: String,
    val progress: Int,
    @ColumnInfo(name = "processed_video_name")
    val processedVideoName: String? = null
)

data class MetaPlanRow(
    val id: Int,
    @ColumnInfo(name = "plan_id")
    val planId: Int,
    @ColumnInfo(name = "motion_id")
    val motionId: Int,
    @ColumnInfo(name = "motion_name")
    val motionName: String,
    @ColumnInfo(name = "day_index")
    val dayIndex: Int,
    val sets: Int,
    val reps: Int,
    val intensity: Double,
    val weight: Double,
    @ColumnInfo(name = "order_index")
    val orderIndex: Int
)

data class MetaHistoryRow(
    val id: Int,
    val date: String,
    val rep: Int,
    val rpe: Int,
    val weight: Double,
    @ColumnInfo(name = "motion_id")
    val motionId: Int,
    @ColumnInfo(name = "motion_name")
    val motionName: String,
    @ColumnInfo(name = "video_name")
    val videoName: String? = null,
    @ColumnInfo(name = "video_source")
    val videoSource: String,
    @ColumnInfo(name = "imported_video_analysis_mode")
    val importedVideoAnalysisMode: String,
    @ColumnInfo(name = "imported_reference_label")
    val importedReferenceLabel: String,
    @ColumnInfo(name = "imported_reference_pixel_distance")
    val importedReferencePixelDistance: Double?,
    @ColumnInfo(name = "imported_reference_distance_meters")
    val importedReferenceDistanceMeters: Double?,
    @ColumnInfo(name = "pose_detection")
    val poseDetection: Boolean = false,
    @ColumnInfo(name = "angle_display")
    val angleDisplay: Boolean = false,
    @ColumnInfo(name = "angle_plot")
    val anglePlot: Boolean = false,
    @ColumnInfo(name = "barbell_detection")
    val barbellDetection: Boolean = false,
    @ColumnInfo(name = "power_calculation")
    val powerCalculation: Boolean = false,
    @ColumnInfo(name = "marked")
    val marked: Boolean = false,
    @ColumnInfo(name = "history_id")
    val historyId: Int? = null
)

data class HistoryRow(
    val id: Int,
    @ColumnInfo(name = "plan_id")
    val planId: Int,
    @ColumnInfo(name = "plan_name")
    val planName: String,
    @ColumnInfo(name = "start_time")
    val startTime: Long,
    @ColumnInfo(name = "end_time")
    val endTime: Long,
    val intensity: Int,
    @ColumnInfo(name = "day_index")
    val dayIndex: Int = 0
)

@Entity(tableName = "metahistory_bin")
data class MetaHistoryBinEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String,
    val rep: Int,
    val rpe: Int,
    val weight: Double,
    @ColumnInfo(name = "motion_id")
    val motionId: Int,
    @ColumnInfo(name = "motion_name")
    val motionName: String,
    @ColumnInfo(name = "video_name")
    val videoName: String? = null,
    @ColumnInfo(name = "video_source")
    val videoSource: String = ImportedVideoSource.CAMERA_CAPTURE.name,
    @ColumnInfo(name = "imported_video_analysis_mode")
    val importedVideoAnalysisMode: String = ImportedVideoAnalysisMode.ESTIMATED.name,
    @ColumnInfo(name = "imported_reference_label")
    val importedReferenceLabel: String = "",
    @ColumnInfo(name = "imported_reference_pixel_distance")
    val importedReferencePixelDistance: Double? = null,
    @ColumnInfo(name = "imported_reference_distance_meters")
    val importedReferenceDistanceMeters: Double? = null,
    @ColumnInfo(name = "pose_detection", defaultValue = "0")
    val poseDetection: Boolean = false,
    @ColumnInfo(name = "angle_display", defaultValue = "0")
    val angleDisplay: Boolean = false,
    @ColumnInfo(name = "angle_plot", defaultValue = "0")
    val anglePlot: Boolean = false,
    @ColumnInfo(name = "barbell_detection", defaultValue = "0")
    val barbellDetection: Boolean = false,
    @ColumnInfo(name = "power_calculation", defaultValue = "0")
    val powerCalculation: Boolean = false,
    @ColumnInfo(name = "marked", defaultValue = "0")
    val marked: Boolean = false,
    @ColumnInfo(name = "history_id")
    val historyId: Int? = null
)

data class MetaHistoryBinRow(
    val id: Int,
    val date: String,
    val rep: Int,
    val rpe: Int,
    val weight: Double,
    @ColumnInfo(name = "motion_id")
    val motionId: Int,
    @ColumnInfo(name = "motion_name")
    val motionName: String,
    @ColumnInfo(name = "video_name")
    val videoName: String? = null,
    @ColumnInfo(name = "video_source")
    val videoSource: String,
    @ColumnInfo(name = "imported_video_analysis_mode")
    val importedVideoAnalysisMode: String,
    @ColumnInfo(name = "imported_reference_label")
    val importedReferenceLabel: String,
    @ColumnInfo(name = "imported_reference_pixel_distance")
    val importedReferencePixelDistance: Double?,
    @ColumnInfo(name = "imported_reference_distance_meters")
    val importedReferenceDistanceMeters: Double?,
    @ColumnInfo(name = "pose_detection")
    val poseDetection: Boolean = false,
    @ColumnInfo(name = "angle_display")
    val angleDisplay: Boolean = false,
    @ColumnInfo(name = "angle_plot")
    val anglePlot: Boolean = false,
    @ColumnInfo(name = "barbell_detection")
    val barbellDetection: Boolean = false,
    @ColumnInfo(name = "power_calculation")
    val powerCalculation: Boolean = false,
    @ColumnInfo(name = "marked")
    val marked: Boolean = false,
    @ColumnInfo(name = "history_id")
    val historyId: Int? = null
)

@Entity(
    tableName = "metahistory_timeseries",
    foreignKeys = [
        ForeignKey(
            entity = MetaHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["metahistory_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["metahistory_id", "metric_name", "timestamp_ms"])
    ]
)
data class MetahistoryTimeseriesEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "metahistory_id")
    val metahistoryId: Int,
    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,
    @ColumnInfo(name = "metric_name")
    val metricName: String,
    val value: Double
)

@Entity(tableName = "metahistory_timeseries_bin")
data class MetahistoryTimeseriesBinEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "original_metahistory_id")
    val originalMetahistoryId: Int,
    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,
    @ColumnInfo(name = "metric_name")
    val metricName: String,
    val value: Double
)

@Entity(
    tableName = "metahistory_pose_frame",
    foreignKeys = [
        ForeignKey(
            entity = MetaHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["metahistory_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["metahistory_id", "timestamp_ms"])
    ]
)
data class PoseFrameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "metahistory_id")
    val metahistoryId: Int,
    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,
    @ColumnInfo(name = "landmarks_json")
    val landmarksJson: String
)

internal fun MotionEntity.toRecord(): MotionRecord {
    return MotionRecord(
        id = id,
        name = name,
        type = try { MotionType.valueOf(type) } catch (_: IllegalArgumentException) { MotionType.BARBELL }
    )
}

internal fun MotionRecord.toEntity(): MotionEntity {
    return MotionEntity(
        id = id,
        name = name,
        type = type.name
    )
}

internal fun PlanEntity.toRecord(metaPlans: List<MetaPlanRow>): PlanRecord {
    return PlanRecord(
        id = id,
        name = name,
        cyclePeriod = cyclePeriod,
        currentIndex = currentIndex,
        lastAppliedAt = lastAppliedAt,
        metaPlans = metaPlans.map { metaPlan -> metaPlan.toRecord() }
    )
}

internal fun PlanRecord.toEntity(): PlanEntity {
    return PlanEntity(
        id = id,
        name = name,
        cyclePeriod = cyclePeriod,
        currentIndex = currentIndex,
        lastAppliedAt = lastAppliedAt
    )
}

internal fun MetaPlanRow.toRecord(): MetaPlanRecord {
    return MetaPlanRecord(
        id = id,
        motionId = motionId,
        motionName = motionName,
        dayIndex = dayIndex,
        sets = sets,
        reps = reps,
        intensity = intensity,
        weight = weight,
        orderIndex = orderIndex
    )
}

internal fun MetaHistoryRow.toRecord(): MetaHistoryRecord {
    val resolvedVideoSource = try {
        ImportedVideoSource.valueOf(videoSource)
    } catch (_: IllegalArgumentException) {
        ImportedVideoSource.CAMERA_CAPTURE
    }
    val resolvedAnalysisMode = try {
        ImportedVideoAnalysisMode.valueOf(importedVideoAnalysisMode)
    } catch (_: IllegalArgumentException) {
        ImportedVideoAnalysisMode.ESTIMATED
    }

    return MetaHistoryRecord(
        id = id,
        date = date,
        rep = rep,
        rpe = rpe,
        weight = weight,
        motionId = motionId,
        motionName = motionName,
        videoName = videoName,
        videoSource = resolvedVideoSource,
        importedVideoAnalysisMode = resolvedAnalysisMode,
        importedReferenceLabel = importedReferenceLabel,
        importedReferencePixelDistance = importedReferencePixelDistance,
        importedReferenceDistanceMeters = importedReferenceDistanceMeters,
        poseDetection = poseDetection,
        angleDisplay = angleDisplay,
        anglePlot = anglePlot,
        barbellDetection = barbellDetection,
        powerCalculation = powerCalculation,
        marked = marked,
        historyId = historyId
    )
}

internal fun MetaHistoryBinRow.toRecord(): MetaHistoryRecord {
    val resolvedVideoSource = try {
        ImportedVideoSource.valueOf(videoSource)
    } catch (_: IllegalArgumentException) {
        ImportedVideoSource.CAMERA_CAPTURE
    }
    val resolvedAnalysisMode = try {
        ImportedVideoAnalysisMode.valueOf(importedVideoAnalysisMode)
    } catch (_: IllegalArgumentException) {
        ImportedVideoAnalysisMode.ESTIMATED
    }

    return MetaHistoryRecord(
        id = id,
        date = date,
        rep = rep,
        rpe = rpe,
        weight = weight,
        motionId = motionId,
        motionName = motionName,
        videoName = videoName,
        videoSource = resolvedVideoSource,
        importedVideoAnalysisMode = resolvedAnalysisMode,
        importedReferenceLabel = importedReferenceLabel,
        importedReferencePixelDistance = importedReferencePixelDistance,
        importedReferenceDistanceMeters = importedReferenceDistanceMeters,
        poseDetection = poseDetection,
        angleDisplay = angleDisplay,
        anglePlot = anglePlot,
        barbellDetection = barbellDetection,
        powerCalculation = powerCalculation,
        marked = marked,
        historyId = historyId
    )
}

internal fun HistoryRow.toRecord(): HistoryRecord {
    return HistoryRecord(
        id = id,
        planId = planId,
        planName = planName,
        startTime = startTime,
        endTime = endTime,
        intensity = intensity,
        dayIndex = dayIndex
    )
}

internal fun HistoryRecord.toEntity(): HistoryEntity {
    return HistoryEntity(
        id = id,
        planId = planId,
        startTime = startTime,
        endTime = endTime,
        intensity = intensity,
        dayIndex = dayIndex
    )
}

@Entity(
    tableName = "video_export_state",
    indices = [Index(value = ["video_name"], unique = true)]
)
data class VideoExportStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "video_name") val videoName: String,
    @ColumnInfo(name = "rendered_items") val renderedItems: String,
    val state: String,
    val progress: Int,
    @ColumnInfo(name = "exported_file_name") val exportedFileName: String? = null
)

@Entity(tableName = "body_metric")
data class BodyMetricEntity(
    @PrimaryKey
    val id: Int,
    val value: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

