package com.potato.liftinsight.training.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

data class AnalysisSettings(
    @ColumnInfo(name = "pose_detection") val poseDetection: Boolean,
    @ColumnInfo(name = "angle_display") val angleDisplay: Boolean,
    @ColumnInfo(name = "angle_plot") val anglePlot: Boolean,
    @ColumnInfo(name = "barbell_detection") val barbellDetection: Boolean,
    @ColumnInfo(name = "power_calculation") val powerCalculation: Boolean,
    @ColumnInfo(name = "rdp_epsilon") val rdpEpsilon: Double,
    @ColumnInfo(name = "rdp_smooth_skeleton") val rdpSmoothSkeleton: Boolean
)

@Dao
abstract class PlanDao {
    @Insert
    protected abstract fun insertPlanEntity(plan: PlanEntity): Long

    @Insert
    protected abstract fun insertMetaPlanEntities(metaPlans: List<MetaPlanEntity>)

    @Update
    protected abstract fun updatePlanEntity(plan: PlanEntity): Int

    @Query("DELETE FROM `plan` WHERE id = :planId")
    abstract fun deletePlan(planId: Int): Int

    @Query("DELETE FROM metaplan WHERE plan_id = :planId")
    protected abstract fun deleteMetaPlansForPlan(planId: Int): Int

    @Query("SELECT * FROM `plan` WHERE id = :planId")
    protected abstract fun getPlanEntity(planId: Int): PlanEntity?

    @Query("SELECT * FROM `plan` ORDER BY name COLLATE NOCASE ASC, id ASC")
    protected abstract fun getPlanEntities(): List<PlanEntity>

    @Query("SELECT COUNT(*) FROM metaplan WHERE plan_id = :planId")
    abstract fun countMetaPlansForPlan(planId: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertPlanSelection(selection: PlanSelectionEntity)

    @Query("SELECT * FROM plan_selection WHERE id = 1")
    abstract fun getPlanSelection(): PlanSelectionEntity?

    @Query("DELETE FROM plan_selection")
    abstract fun clearPlanSelection()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertWorkoutSession(session: WorkoutSessionEntity)

    @Query("SELECT * FROM workout_session WHERE id = 1")
    abstract fun getWorkoutSession(): WorkoutSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertWorkoutProgress(progress: WorkoutProgressEntity)

    @Query("SELECT * FROM workout_progress WHERE id = 1")
    abstract fun getWorkoutProgress(): WorkoutProgressEntity?

    @Query("DELETE FROM workout_progress")
    abstract fun clearWorkoutProgress()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertVideoProcessState(state: VideoProcessStateEntity)

    @Query(
        "SELECT * FROM video_process_state WHERE video_name = :videoName ORDER BY id DESC LIMIT 1"
    )
    abstract fun getVideoProcessState(videoName: String): VideoProcessStateEntity?

    @Query("DELETE FROM video_process_state WHERE video_name = :videoName")
    abstract fun deleteVideoProcessState(videoName: String)

    @Query(
        "UPDATE video_process_state SET state = :state, progress = :progress WHERE video_name = :videoName"
    )
    abstract fun updateVideoProcessProgress(videoName: String, state: String, progress: Int): Int

    @Insert
    abstract fun insertMetaHistory(metaHistory: MetaHistoryEntity): Long

    @Query(
        """
        UPDATE metahistory
        SET weight = :weight, rep = :rep, rpe = :rpe
        WHERE id = :historyId
        """
    )
    abstract fun updateMetaHistoryDetails(historyId: Int, weight: Double, rep: Int, rpe: Int): Int

    @Query("UPDATE metahistory SET marked = :marked WHERE id = :id")
    abstract fun updateMetaHistoryMarked(id: Int, marked: Boolean): Int

    @Query("UPDATE metahistory SET video_name = :videoName WHERE id = :historyId")
    abstract fun updateMetaHistoryVideoName(historyId: Int, videoName: String?): Int

    @Query("SELECT id FROM metahistory WHERE video_name = :videoName LIMIT 1")
    abstract fun getMetaHistoryIdByVideoName(videoName: String): Int?

    @Query(
        """
        UPDATE metahistory SET 
            pose_detection = :poseDetection,
            angle_display = :angleDisplay,
            angle_plot = :anglePlot,
            barbell_detection = :barbellDetection,
            power_calculation = :powerCalculation
        WHERE id = :recordId
        """
    )
    abstract fun updateAnalysisVideoState(
        recordId: Int,
        poseDetection: Boolean,
        angleDisplay: Boolean,
        anglePlot: Boolean,
        barbellDetection: Boolean,
        powerCalculation: Boolean
    )

    @Query("SELECT pose_detection, angle_display, angle_plot, barbell_detection, power_calculation, rdp_epsilon, rdp_smooth_skeleton FROM metahistory WHERE id = :id")
    abstract fun getAnalysisSettings(id: Int): AnalysisSettings?

    @Query(
        """
        UPDATE metahistory SET
            pose_detection = :poseDetection,
            angle_display = :angleDisplay,
            angle_plot = :anglePlot,
            barbell_detection = :barbellDetection,
            power_calculation = :powerCalculation,
            rdp_epsilon = :rdpEpsilon,
            rdp_smooth_skeleton = :rdpSmoothSkeleton
        WHERE id = :recordId
        """
    )
    abstract fun updateAnalysisSettings(
        recordId: Int,
        poseDetection: Boolean,
        angleDisplay: Boolean,
        anglePlot: Boolean,
        barbellDetection: Boolean,
        powerCalculation: Boolean,
        rdpEpsilon: Double,
        rdpSmoothSkeleton: Boolean
    )

    @Transaction
    open fun upsertVideoExportState(state: VideoExportStateEntity) {
        deleteVideoExportState(state.videoName)
        insertVideoExportState(state)
    }

    @Insert
    protected abstract fun insertVideoExportState(state: VideoExportStateEntity)

    @Query("SELECT * FROM video_export_state WHERE video_name = :videoName ORDER BY id DESC LIMIT 1")
    abstract fun getVideoExportState(videoName: String): VideoExportStateEntity?

    @Query("DELETE FROM video_export_state WHERE video_name = :videoName")
    abstract fun deleteVideoExportState(videoName: String)

    @Query("UPDATE video_export_state SET state = :state, progress = :progress WHERE video_name = :videoName")
    abstract fun updateVideoExportProgress(videoName: String, state: String, progress: Int)

    @Query(
        """
        UPDATE metahistory
        SET video_source = :videoSource,
            imported_video_analysis_mode = :analysisMode,
            imported_reference_label = :referenceLabel,
            imported_reference_pixel_distance = :referencePixelDistance,
            imported_reference_distance_meters = :referenceDistanceMeters
        WHERE id = :historyId
        """
    )
    abstract fun updateImportedVideoMetadata(
        historyId: Int,
        videoSource: String,
        analysisMode: String,
        referenceLabel: String,
        referencePixelDistance: Double?,
        referenceDistanceMeters: Double?
    ): Int

    @Query(
        """
        SELECT
            metahistory.id,
            metahistory.date,
            metahistory.rep,
            metahistory.rpe,
            metahistory.weight,
            metahistory.motion_id,
            motion.name AS motion_name,
            metahistory.video_name,
            metahistory.video_source,
            metahistory.imported_video_analysis_mode,
            metahistory.imported_reference_label,
            metahistory.imported_reference_pixel_distance,
            metahistory.imported_reference_distance_meters,
            metahistory.history_id,
            metahistory.pose_detection,
            metahistory.angle_display,
            metahistory.angle_plot,
            metahistory.barbell_detection,
            metahistory.power_calculation,
            metahistory.marked,
            metahistory.rdp_epsilon,
            metahistory.rdp_smooth_skeleton,
            metahistory.video_edited
        FROM metahistory
        INNER JOIN motion ON motion.id = metahistory.motion_id
        ORDER BY metahistory.date DESC, metahistory.id DESC
        """
    )
    abstract fun getMetaHistoryWithMotions(): List<MetaHistoryRow>

    @Query(
        """
        SELECT
            metahistory.id,
            metahistory.date,
            metahistory.rep,
            metahistory.rpe,
            metahistory.weight,
            metahistory.motion_id,
            motion.name AS motion_name,
            metahistory.video_name,
            metahistory.video_source,
            metahistory.imported_video_analysis_mode,
            metahistory.imported_reference_label,
            metahistory.imported_reference_pixel_distance,
            metahistory.imported_reference_distance_meters,
            metahistory.history_id,
            metahistory.pose_detection,
            metahistory.angle_display,
            metahistory.angle_plot,
            metahistory.barbell_detection,
            metahistory.power_calculation,
            metahistory.marked,
            metahistory.rdp_epsilon,
            metahistory.rdp_smooth_skeleton,
            metahistory.video_edited
        FROM metahistory
        INNER JOIN motion ON motion.id = metahistory.motion_id
        WHERE metahistory.history_id = :historyId
        ORDER BY metahistory.date DESC, metahistory.id DESC
        """
    )
    abstract fun getMetaHistoryRowsByHistoryId(historyId: Int): List<MetaHistoryRow>

    @Query(
        """
        SELECT
            metaplan.id,
            metaplan.plan_id,
            metaplan.motion_id,
            motion.name AS motion_name,
            metaplan.day_index,
            metaplan.sets,
            metaplan.reps,
            metaplan.intensity,
            metaplan.weight,
            metaplan.order_index
        FROM metaplan
        INNER JOIN motion ON motion.id = metaplan.motion_id
        WHERE metaplan.plan_id = :planId
        ORDER BY metaplan.day_index ASC, metaplan.order_index ASC, metaplan.id ASC
        """
    )
    protected abstract fun getMetaPlanRowsForPlan(planId: Int): List<MetaPlanRow>

    @Query(
        """
        SELECT
            metaplan.id,
            metaplan.plan_id,
            metaplan.motion_id,
            motion.name AS motion_name,
            metaplan.day_index,
            metaplan.sets,
            metaplan.reps,
            metaplan.intensity,
            metaplan.weight,
            metaplan.order_index
        FROM metaplan
        INNER JOIN motion ON motion.id = metaplan.motion_id
        WHERE metaplan.plan_id IN (:planIds)
        ORDER BY metaplan.plan_id ASC, metaplan.day_index ASC, metaplan.order_index ASC, metaplan.id ASC
        """
    )
    protected abstract fun getMetaPlanRowsForPlans(planIds: List<Int>): List<MetaPlanRow>

    @Transaction
    open fun createPlan(
        plan: PlanEntity,
        metaPlans: List<MetaPlanEntity>
    ): Int {
        val planId = insertPlanEntity(plan).toInt()

        if (metaPlans.isNotEmpty()) {
            insertMetaPlanEntities(metaPlans.map { metaPlan ->
                metaPlan.copy(planId = planId)
            })
        }

        return planId
    }

    @Transaction
    open fun updatePlan(
        plan: PlanEntity,
        metaPlans: List<MetaPlanEntity>
    ): Boolean {
        val updatedRows = updatePlanEntity(plan)

        if (updatedRows == 0) {
            return false
        }

        deleteMetaPlansForPlan(plan.id)

        if (metaPlans.isNotEmpty()) {
            insertMetaPlanEntities(metaPlans.map { metaPlan ->
                metaPlan.copy(planId = plan.id)
            })
        }

        return true
    }

    @Transaction
    open fun getPlanRecord(planId: Int): PlanRecord? {
        val plan = getPlanEntity(planId) ?: return null
        val metaPlans = getMetaPlanRowsForPlan(planId)

        return plan.toRecord(metaPlans)
    }

    @Transaction
    open fun getPlanRecords(): List<PlanRecord> {
        val plans = getPlanEntities()

        if (plans.isEmpty()) {
            return emptyList()
        }

        val metaPlansByPlanId = getMetaPlanRowsForPlans(plans.map { plan -> plan.id })
            .groupBy { metaPlan -> metaPlan.planId }

        return plans.map { plan ->
            plan.toRecord(metaPlansByPlanId[plan.id].orEmpty())
        }
    }

    @Insert
    abstract fun insertMetaHistoryBin(entity: MetaHistoryBinEntity): Long

    @Query("DELETE FROM metahistory WHERE id = :id")
    abstract fun deleteMetaHistoryById(id: Int): Int

    @Insert
    protected abstract fun insertTimeseriesBin(entities: List<MetahistoryTimeseriesBinEntity>)

    @Query(
        """
        SELECT timestamp_ms, metric_name, value
        FROM metahistory_timeseries_bin
        WHERE original_metahistory_id = :originalMetahistoryId
        """
    )
    protected abstract fun getTimeseriesBinEntries(originalMetahistoryId: Int): List<BinTimeseriesEntry>

    @Query("DELETE FROM metahistory_timeseries_bin WHERE original_metahistory_id = :originalMetahistoryId")
    protected abstract fun deleteTimeseriesBinByOriginalMetaHistoryId(originalMetahistoryId: Int): Int

    @Query("SELECT * FROM metahistory_timeseries WHERE metahistory_id = :metahistoryId")
    protected abstract fun getTimeseriesEntities(metahistoryId: Int): List<MetahistoryTimeseriesEntity>

    @Insert
    protected abstract fun insertTimeseriesEntities(entities: List<MetahistoryTimeseriesEntity>)

    @Query(
        """
        SELECT
            id,
            date,
            rep,
            rpe,
            weight,
            motion_id,
            motion_name,
            video_name,
            video_source,
            imported_video_analysis_mode,
            imported_reference_label,
            imported_reference_pixel_distance,
            imported_reference_distance_meters,
            history_id,
            pose_detection,
            angle_display,
            angle_plot,
            barbell_detection,
            power_calculation,
            marked,
            rdp_epsilon,
            rdp_smooth_skeleton,
            video_edited
        FROM metahistory_bin
        ORDER BY date DESC, id DESC
        """
    )
    abstract fun getMetaHistoryBinRows(): List<MetaHistoryBinRow>

    @Query("DELETE FROM metahistory_bin WHERE id = :id")
    abstract fun deleteMetaHistoryBinById(id: Int): Int

    @Query("DELETE FROM metahistory_bin WHERE id IN (:ids)")
    abstract fun deleteMetaHistoryBinByIds(ids: List<Int>): Int

    @Query(
        """
        SELECT
            metahistory.id,
            metahistory.date,
            metahistory.rep,
            metahistory.rpe,
            metahistory.weight,
            metahistory.motion_id,
            motion.name AS motion_name,
            metahistory.video_name,
            metahistory.video_source,
            metahistory.imported_video_analysis_mode,
            metahistory.imported_reference_label,
            metahistory.imported_reference_pixel_distance,
            metahistory.imported_reference_distance_meters,
            metahistory.history_id,
            metahistory.pose_detection,
            metahistory.angle_display,
            metahistory.angle_plot,
            metahistory.barbell_detection,
            metahistory.power_calculation,
            metahistory.marked,
            metahistory.rdp_epsilon,
            metahistory.rdp_smooth_skeleton,
            metahistory.video_edited
        FROM metahistory
        INNER JOIN motion ON motion.id = metahistory.motion_id
        WHERE metahistory.id = :id
        """
    )
    protected abstract fun getMetaHistoryRowById(id: Int): MetaHistoryRow?

    @Query(
        """
        SELECT
            id,
            date,
            rep,
            rpe,
            weight,
            motion_id,
            motion_name,
            video_name,
            video_source,
            imported_video_analysis_mode,
            imported_reference_label,
            imported_reference_pixel_distance,
            imported_reference_distance_meters,
            history_id,
            pose_detection,
            angle_display,
            angle_plot,
            barbell_detection,
            power_calculation,
            marked,
            rdp_epsilon,
            rdp_smooth_skeleton,
            video_edited
        FROM metahistory_bin
        WHERE id = :id
        """
    )
    protected abstract fun getMetaHistoryBinRowById(id: Int): MetaHistoryBinRow?

    @Transaction
    open fun softDeleteMetaHistory(historyId: Int): Boolean {
        val row = getMetaHistoryRowById(historyId) ?: return false

        val binEntity = MetaHistoryBinEntity(
            date = row.date,
            rep = row.rep,
            rpe = row.rpe,
            weight = row.weight,
            motionId = row.motionId,
            motionName = row.motionName,
            videoName = row.videoName,
            videoSource = row.videoSource,
            importedVideoAnalysisMode = row.importedVideoAnalysisMode,
            importedReferenceLabel = row.importedReferenceLabel,
            importedReferencePixelDistance = row.importedReferencePixelDistance,
            importedReferenceDistanceMeters = row.importedReferenceDistanceMeters,
            poseDetection = row.poseDetection,
            angleDisplay = row.angleDisplay,
            anglePlot = row.anglePlot,
            barbellDetection = row.barbellDetection,
            powerCalculation = row.powerCalculation,
            marked = row.marked,
            rdpEpsilon = row.rdpEpsilon,
            rdpSmoothSkeleton = row.rdpSmoothSkeleton,
            videoEdited = row.videoEdited,
            historyId = row.historyId
        )

        val binId = insertMetaHistoryBin(binEntity).toInt()

        // Preserve timeseries data before CASCADE delete removes it
        val timeseriesRows = getTimeseriesEntities(historyId)
        if (timeseriesRows.isNotEmpty()) {
            val binTimeseries = timeseriesRows.map { entity ->
                MetahistoryTimeseriesBinEntity(
                    originalMetahistoryId = binId,
                    timestampMs = entity.timestampMs,
                    metricName = entity.metricName,
                    value = entity.value
                )
            }
            insertTimeseriesBin(binTimeseries)
        }

        deleteMetaHistoryById(historyId)

        return true
    }

    @Transaction
    open fun softDeleteMetaHistoryByIds(historyIds: List<Int>): Int {
        var count = 0

        for (id in historyIds) {
            if (softDeleteMetaHistory(id)) {
                count++
            }
        }

        return count
    }

    @Transaction
    open fun revertBinRecord(binId: Int): Boolean {
        val row = getMetaHistoryBinRowById(binId) ?: return false

        val historyEntity = MetaHistoryEntity(
            date = row.date,
            rep = row.rep,
            rpe = row.rpe,
            weight = row.weight,
            motionId = row.motionId,
            videoName = row.videoName,
            videoSource = row.videoSource,
            importedVideoAnalysisMode = row.importedVideoAnalysisMode,
            importedReferenceLabel = row.importedReferenceLabel,
            importedReferencePixelDistance = row.importedReferencePixelDistance,
            importedReferenceDistanceMeters = row.importedReferenceDistanceMeters,
            poseDetection = row.poseDetection,
            angleDisplay = row.angleDisplay,
            anglePlot = row.anglePlot,
            barbellDetection = row.barbellDetection,
            powerCalculation = row.powerCalculation,
            marked = row.marked,
            rdpEpsilon = row.rdpEpsilon,
            rdpSmoothSkeleton = row.rdpSmoothSkeleton,
            videoEdited = row.videoEdited,
            historyId = row.historyId
        )

        val newMetaHistoryId = insertMetaHistory(historyEntity).toInt()

        // Restore timeseries data from bin
        val binTimeseries = getTimeseriesBinEntries(binId)
        if (binTimeseries.isNotEmpty()) {
            val restoredTimeseries = binTimeseries.map { entry ->
                MetahistoryTimeseriesEntity(
                    metahistoryId = newMetaHistoryId,
                    timestampMs = entry.timestampMs,
                    metricName = entry.metricName,
                    value = entry.value
                )
            }
            insertTimeseriesEntities(restoredTimeseries)
            deleteTimeseriesBinByOriginalMetaHistoryId(binId)
        }

        deleteMetaHistoryBinById(binId)

        return true
    }

    @Transaction
    open fun revertBinRecords(binIds: List<Int>): Int {
        var count = 0

        for (id in binIds) {
            if (revertBinRecord(id)) {
                count++
            }
        }

        return count
    }

    @Query("UPDATE metahistory SET video_edited = 1 WHERE id = :recordId")
    abstract fun markVideoEdited(recordId: Int): Int

    @Query("UPDATE metahistory SET video_name = NULL, pose_detection = 0, angle_display = 0, angle_plot = 0, barbell_detection = 0, power_calculation = 0, video_edited = 0 WHERE id = :recordId")
    abstract fun clearVideoAndResetFlags(recordId: Int): Int

    @Query(
        """
        SELECT
            metahistory.id,
            metahistory.date,
            metahistory.rep,
            metahistory.rpe,
            metahistory.weight,
            metahistory.motion_id,
            motion.name AS motion_name,
            metahistory.video_name,
            metahistory.video_source,
            metahistory.imported_video_analysis_mode,
            metahistory.imported_reference_label,
            metahistory.imported_reference_pixel_distance,
            metahistory.imported_reference_distance_meters,
            metahistory.history_id,
            metahistory.pose_detection,
            metahistory.angle_display,
            metahistory.angle_plot,
            metahistory.barbell_detection,
            metahistory.power_calculation,
            metahistory.marked,
            metahistory.rdp_epsilon,
            metahistory.rdp_smooth_skeleton,
            metahistory.video_edited
        FROM metahistory
        INNER JOIN motion ON motion.id = metahistory.motion_id
        WHERE metahistory.video_name IS NOT NULL
            AND metahistory.video_name != ''
            AND metahistory.marked = 0
            AND metahistory.video_edited = 0
            AND metahistory.pose_detection = 0
            AND metahistory.angle_display = 0
            AND metahistory.angle_plot = 0
            AND metahistory.barbell_detection = 0
            AND metahistory.power_calculation = 0
        """
    )
    abstract fun getRecordsWithRawVideoNotMarked(): List<MetaHistoryRow>
}
