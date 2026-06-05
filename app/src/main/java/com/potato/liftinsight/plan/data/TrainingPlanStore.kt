package com.potato.liftinsight.plan.data

import android.content.Context
import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.plan.model.WorkoutProgressState
import com.potato.liftinsight.plan.model.WorkoutSessionState
import com.potato.liftinsight.plan.model.advancePlanDayIndex
import com.potato.liftinsight.plan.model.workoutElapsedTimeMs
import com.potato.liftinsight.plan.model.normalizePlanDayIndex
import com.potato.liftinsight.plan.model.sortPlansByLastApplied
import com.potato.liftinsight.training.data.CreateMetaHistoryRequest
import com.potato.liftinsight.training.data.CreateMotionRequest
import com.potato.liftinsight.training.data.CreateMetaPlanRequest
import com.potato.liftinsight.training.data.CreatePlanRequest
import com.potato.liftinsight.training.data.CreateHistoryRequest
import com.potato.liftinsight.training.data.HistoryEntity
import com.potato.liftinsight.training.data.HistoryRecord
import com.potato.liftinsight.training.data.ImportedVideoMetadataRecord
import com.potato.liftinsight.training.data.LiftInsightDatabase
import com.potato.liftinsight.training.data.MetaHistoryEntity
import com.potato.liftinsight.training.data.MetaHistoryRecord
import com.potato.liftinsight.training.data.MetaHistoryRow
import com.potato.liftinsight.training.data.MetaPlanRecord
import com.potato.liftinsight.training.data.MotionStore
import com.potato.liftinsight.training.data.PlanSelectionEntity
import com.potato.liftinsight.training.data.PlanStore
import com.potato.liftinsight.training.data.PlanRecord
import com.potato.liftinsight.training.data.UpdateImportedVideoMetadataRequest
import com.potato.liftinsight.training.data.WorkoutSessionEntity
import com.potato.liftinsight.training.data.WorkoutProgressEntity
import com.potato.liftinsight.training.data.toRecord
import java.util.TimeZone

class TrainingPlanStore private constructor(
    private val motionStore: MotionStore,
    private val planStore: PlanStore,
    private val database: LiftInsightDatabase,
    private val logger: AppLogger
) {
    fun getAvailableMotions(): List<AvailableMotionState> {
        return motionStore.getMotions().map { motion ->
            AvailableMotionState(
                id = motion.id,
                title = motion.name
            )
        }
    }

    fun ensureAvailableMotions(motions: List<AvailableMotionState>) {
        val existingMotionsByName = motionStore.getMotions().associateBy { motion -> motion.name }

        motions.forEach { motion ->
            val existingMotion = existingMotionsByName[motion.title]

            if (existingMotion == null) {
                motionStore.createMotion(
                    CreateMotionRequest(
                        name = motion.title
                    )
                )
                return@forEach
            }
        }
    }

    fun getTrainingPlans(): List<TrainingPlanState> {
        return sortPlansByLastApplied(
            planStore.getPlans().map { plan ->
                plan.toState()
            }
        )
    }

    fun getTrainingPlan(planId: Int): TrainingPlanState? {
        return planStore.getPlan(planId)?.toState()
    }

    fun getCurrentPlanId(): Int {
        val currentPlanId = database.planDao().getPlanSelection()?.currentPlanId ?: -1

        logTrace("getCurrentPlanId result: currentPlanId=$currentPlanId")

        return currentPlanId
    }

    fun setCurrentPlan(
        planId: Int,
        selectedAt: Long? = null
    ): Boolean {
        logTrace("setCurrentPlan start: planId=$planId, selectedAt=$selectedAt")

        if (planStore.getPlan(planId) == null) {
            logTrace("setCurrentPlan result: planId=$planId, updated=false")
            return false
        }

        database.planDao().upsertPlanSelection(
            PlanSelectionEntity(
                currentPlanId = planId,
                currentDayEpoch = selectedAt?.let { timestamp -> localEpochDay(timestamp) }
            )
        )

        logger.info(TAG, "Updated current training plan selection: planId=$planId")
        logTrace("setCurrentPlan result: planId=$planId, updated=true")

        return true
    }

    fun advanceCurrentPlanDayIfNeeded(now: Long): Boolean {
        logTrace("advanceCurrentPlanDayIfNeeded start: now=$now")

        val planSelection = database.planDao().getPlanSelection()
        val currentPlanId = planSelection?.currentPlanId

        if (currentPlanId == null) {
            logTrace("advanceCurrentPlanDayIfNeeded result: advanced=false, reason=noCurrentPlan")
            return false
        }

        val currentPlan = getTrainingPlan(currentPlanId)

        if (currentPlan == null) {
            logTrace("advanceCurrentPlanDayIfNeeded result: advanced=false, reason=missingPlan, planId=$currentPlanId")
            return false
        }

        val todayEpoch = localEpochDay(now)
        val storedDayEpoch = planSelection.currentDayEpoch

        if (storedDayEpoch == todayEpoch) {
            logTrace(
                "advanceCurrentPlanDayIfNeeded result: advanced=false, reason=alreadyHandled, planId=$currentPlanId, currentDayEpoch=$todayEpoch"
            )
            return false
        }

        if (storedDayEpoch == null || storedDayEpoch > todayEpoch) {
            database.planDao().upsertPlanSelection(
                planSelection.copy(currentDayEpoch = todayEpoch)
            )

            logger.info(
                TAG,
                "Stored current training plan day marker: planId=$currentPlanId, currentDayEpoch=$todayEpoch"
            )
            logTrace(
                "advanceCurrentPlanDayIfNeeded result: advanced=false, reason=storedMarkerOnly, planId=$currentPlanId, currentDayEpoch=$todayEpoch"
            )

            return false
        }

        val dayOffset = todayEpoch - storedDayEpoch
        val advancedIndex = advancePlanDayIndex(
            currentIndex = currentPlan.currentIndex,
            cyclePeriod = currentPlan.cyclePeriod,
            dayOffset = dayOffset
        )
        val planUpdated = if (advancedIndex == currentPlan.currentIndex) {
            true
        } else {
            updateTrainingPlan(
                currentPlan.copy(currentIndex = advancedIndex)
            )
        }

        if (!planUpdated) {
            logTrace(
                "advanceCurrentPlanDayIfNeeded result: advanced=false, reason=updateFailed, planId=$currentPlanId, targetDayIndex=$advancedIndex"
            )
            return false
        }

        database.planDao().upsertPlanSelection(
            planSelection.copy(currentDayEpoch = todayEpoch)
        )

        logger.info(
            TAG,
            "Advanced current training plan day: planId=$currentPlanId, previousDayIndex=${currentPlan.currentIndex}, currentDayIndex=$advancedIndex, elapsedDays=$dayOffset"
        )
        logTrace(
            "advanceCurrentPlanDayIfNeeded result: advanced=true, planId=$currentPlanId, previousDayIndex=${currentPlan.currentIndex}, currentDayIndex=$advancedIndex, elapsedDays=$dayOffset"
        )

        return true
    }

    fun clearCurrentPlan() {
        logTrace("clearCurrentPlan start")

        database.planDao().clearPlanSelection()

        logger.info(TAG, "Cleared current training plan selection")
        logTrace("clearCurrentPlan result: cleared=true")
    }

    fun getWorkoutSession(): WorkoutSessionState {
        val workoutSession = database.planDao().getWorkoutSession()?.toState() ?: WorkoutSessionState()

        logTrace(
            "getWorkoutSession result: isWorkoutGoing=${workoutSession.isWorkoutGoing}, isPaused=${workoutSession.isPaused}, elapsedBeforePauseMs=${workoutSession.elapsedBeforePauseMs}"
        )

        return workoutSession
    }

    fun getWorkoutProgress(): WorkoutProgressState? {
        val workoutProgress = database.planDao().getWorkoutProgress()?.toState()

        logTrace(
            "getWorkoutProgress result: planId=${workoutProgress?.planId ?: -1}, dayIndex=${workoutProgress?.dayIndex ?: -1}, nextSetIndex=${workoutProgress?.nextSetIndex ?: -1}, isFinished=${workoutProgress?.isFinished ?: false}"
        )

        return workoutProgress
    }

    fun saveWorkoutProgress(progress: WorkoutProgressState) {
        logTrace(
            "saveWorkoutProgress start: planId=${progress.planId}, dayIndex=${progress.dayIndex}, nextSetIndex=${progress.nextSetIndex}, activeSetIndex=${progress.activeSetIndex}, totalSetCount=${progress.totalSetCount}, isFinished=${progress.isFinished}"
        )

        database.planDao().upsertWorkoutProgress(progress.toEntity())

        logger.info(TAG, "Saved workout progress")
        logTrace("saveWorkoutProgress result: saved=true")
    }

    fun clearWorkoutProgress() {
        logTrace("clearWorkoutProgress start")

        database.planDao().clearWorkoutProgress()

        logger.info(TAG, "Cleared workout progress")
        logTrace("clearWorkoutProgress result: cleared=true")
    }

    fun startWorkout(startedAt: Long) {
        logTrace("startWorkout start: startedAt=$startedAt")

        database.planDao().upsertWorkoutSession(
            WorkoutSessionEntity(
                isWorkoutGoing = true,
                isPaused = false,
                startedAt = startedAt.coerceAtLeast(0L),
                lastResumedAt = startedAt.coerceAtLeast(0L),
                elapsedBeforePauseMs = 0L
            )
        )

        logger.info(TAG, "Started workout session")
        logTrace("startWorkout result: started=true")
    }

    fun pauseWorkout(pausedAt: Long) {
        logTrace("pauseWorkout start: pausedAt=$pausedAt")

        val currentSession = getWorkoutSession()
        val updatedElapsedTimeMs = workoutElapsedTimeMs(currentSession, pausedAt.coerceAtLeast(0L))

        database.planDao().upsertWorkoutSession(
            WorkoutSessionEntity(
                isWorkoutGoing = currentSession.isWorkoutGoing,
                isPaused = true,
                startedAt = currentSession.startedAt,
                lastResumedAt = 0L,
                elapsedBeforePauseMs = updatedElapsedTimeMs
            )
        )

        logger.info(TAG, "Paused workout session")
        logTrace("pauseWorkout result: paused=true, elapsedBeforePauseMs=$updatedElapsedTimeMs")
    }

    fun resumeWorkout(resumedAt: Long) {
        logTrace("resumeWorkout start: resumedAt=$resumedAt")

        val currentSession = getWorkoutSession()
        val normalizedResumedAt = resumedAt.coerceAtLeast(0L)
        val startedAt = if (currentSession.startedAt > 0L) {
            currentSession.startedAt
        } else {
            normalizedResumedAt
        }

        database.planDao().upsertWorkoutSession(
            WorkoutSessionEntity(
                isWorkoutGoing = true,
                isPaused = false,
                startedAt = startedAt,
                lastResumedAt = normalizedResumedAt,
                elapsedBeforePauseMs = currentSession.elapsedBeforePauseMs.coerceAtLeast(0L)
            )
        )

        logger.info(TAG, "Resumed workout session")
        logTrace("resumeWorkout result: resumed=true")
    }

    fun stopWorkout() {
        logTrace("stopWorkout start")

        database.planDao().upsertWorkoutSession(WorkoutSessionEntity())

        logger.info(TAG, "Stopped workout session")
        logTrace("stopWorkout result: stopped=true")
    }

    fun insertMetaHistory(request: CreateMetaHistoryRequest): Long {
        logTrace(
            "insertMetaHistory start: rep=${request.rep}, rpe=${request.rpe}, weight=${request.weight}, motionId=${request.motionId}, date=${request.date}"
        )

        val insertedId = database.planDao().insertMetaHistory(request.toEntity())

        logger.info(TAG, "Inserted metahistory record: id=$insertedId")
        logTrace("insertMetaHistory result: insertedId=$insertedId")

        return insertedId
    }

    fun getMetaHistoryRecords(): List<MetaHistoryRecord> {
        logTrace("getMetaHistoryRecords start")

        val rows = database.planDao().getMetaHistoryWithMotions()
        val records = rows.map { row -> row.toRecord() }

        logTrace("getMetaHistoryRecords result: count=${records.size}")

        return records
    }

    fun getMetaHistoryRecordsByHistoryId(historyId: Int): List<MetaHistoryRecord> {
        logTrace("getMetaHistoryRecordsByHistoryId start: historyId=$historyId")

        val rows = database.planDao().getMetaHistoryRowsByHistoryId(historyId)
        val records = rows.map { row -> row.toRecord() }

        logTrace("getMetaHistoryRecordsByHistoryId result: count=${records.size}")

        return records
    }

    fun getHistoryRecords(): List<HistoryRecord> {
        logTrace("getHistoryRecords start")

        val rows = database.historyDao().getHistoryRows()
        val records = rows.map { row -> row.toRecord() }

        logTrace("getHistoryRecords result: count=${records.size}")

        return records
    }

    fun createHistoryRecord(request: CreateHistoryRequest): Int {
        logTrace(
            "createHistoryRecord start: planId=${request.planId}, startTime=${request.startTime}, endTime=${request.endTime}, dayIndex=${request.dayIndex}"
        )

        val historyId = database.historyDao().insertHistory(
            HistoryEntity(
                planId = request.planId,
                startTime = request.startTime,
                endTime = request.endTime,
                intensity = request.intensity,
                dayIndex = request.dayIndex
            )
        ).toInt()

        logger.info(TAG, "Created history record: id=$historyId")
        logTrace("createHistoryRecord result: historyId=$historyId")

        return historyId
    }

    fun updateHistoryEndTime(historyId: Int, endTime: Long) {
        logTrace("updateHistoryEndTime start: historyId=$historyId, endTime=$endTime")

        database.historyDao().updateHistoryEndTime(historyId, endTime)

        logger.info(TAG, "Updated history end time: historyId=$historyId, endTime=$endTime")
        logTrace("updateHistoryEndTime result: updated=true")
    }

    fun updateMetaHistoryVideoName(historyId: Int, videoName: String?): Boolean {
        logTrace("updateMetaHistoryVideoName start: historyId=$historyId, videoName=$videoName")

        val updatedRows = database.planDao().updateMetaHistoryVideoName(historyId, videoName)

        logTrace("updateMetaHistoryVideoName result: updatedRows=$updatedRows")

        return updatedRows > 0
    }

    fun updateImportedVideoMetadata(request: UpdateImportedVideoMetadataRequest): Boolean {
        logTrace(
            "updateImportedVideoMetadata start: historyId=${request.historyId}, source=${request.videoSource}, mode=${request.analysisMode}"
        )

        val updatedRows = database.planDao().updateImportedVideoMetadata(
            historyId = request.historyId,
            videoSource = request.videoSource.name,
            analysisMode = request.analysisMode.name,
            referenceLabel = request.referenceLabel.trim(),
            referencePixelDistance = request.referencePixelDistance,
            referenceDistanceMeters = request.referenceDistanceMeters
        )

        logTrace("updateImportedVideoMetadata result: updatedRows=$updatedRows")

        return updatedRows > 0
    }

    fun getImportedVideoMetadata(historyId: Int): ImportedVideoMetadataRecord? {
        val record = getMetaHistoryRecords().firstOrNull { history -> history.id == historyId } ?: return null

        return ImportedVideoMetadataRecord(
            historyId = record.id,
            videoSource = record.videoSource,
            analysisMode = record.importedVideoAnalysisMode,
            referenceLabel = record.importedReferenceLabel,
            referencePixelDistance = record.importedReferencePixelDistance,
            referenceDistanceMeters = record.importedReferenceDistanceMeters
        )
    }

    fun updateMetaHistoryDetails(historyId: Int, weight: Double, rep: Int, rpe: Int): Boolean {
        logTrace(
            "updateMetaHistoryDetails start: historyId=$historyId, weight=$weight, rep=$rep, rpe=$rpe"
        )

        val updatedRows = database.planDao().updateMetaHistoryDetails(
            historyId = historyId,
            weight = weight,
            rep = rep,
            rpe = rpe
        )

        logTrace("updateMetaHistoryDetails result: updatedRows=$updatedRows")

        return updatedRows > 0
    }

    fun createTrainingPlan(
        plan: TrainingPlanState
    ): Int {
        return planStore.createPlan(
            CreatePlanRequest(
                name = plan.name,
                cyclePeriod = plan.cyclePeriod,
                currentIndex = normalizePlanDayIndex(
                    dayIndex = plan.currentIndex,
                    cyclePeriod = plan.cyclePeriod
                ),
                lastAppliedAt = plan.lastAppliedAt,
                metaPlans = plan.motions.map { motion ->
                    CreateMetaPlanRequest(
                        motionId = motion.motionId,
                        dayIndex = normalizePlanDayIndex(
                            dayIndex = motion.dayIndex,
                            cyclePeriod = plan.cyclePeriod
                        ),
                        sets = motion.sets,
                        reps = motion.repsPerSet,
                        intensity = motion.intensity,
                        weight = motion.weight,
                        orderIndex = motion.orderIndex
                    )
                }
            )
        )
    }

    fun updateTrainingPlan(plan: TrainingPlanState): Boolean {
        return planStore.updatePlan(plan.toRecord())
    }

    fun deleteTrainingPlan(planId: Int): Boolean {
        return planStore.deletePlan(planId)
    }

    fun softDeleteMetaHistory(historyId: Int): Boolean {
        logTrace("softDeleteMetaHistory start: historyId=$historyId")

        val result = database.planDao().softDeleteMetaHistory(historyId)

        logTrace("softDeleteMetaHistory result: historyId=$historyId, deleted=$result")

        return result
    }

    fun softDeleteMetaHistoryByIds(historyIds: List<Int>): Int {
        logTrace("softDeleteMetaHistoryByIds start: historyIds=$historyIds")

        val count = database.planDao().softDeleteMetaHistoryByIds(historyIds)

        logTrace("softDeleteMetaHistoryByIds result: count=$count")

        return count
    }

    fun getBinRecords(): List<MetaHistoryRecord> {
        logTrace("getBinRecords start")

        val rows = database.planDao().getMetaHistoryBinRows()
        val records = rows.map { row -> row.toRecord() }

        logTrace("getBinRecords result: count=${records.size}")

        return records
    }

    fun permanentlyDeleteBinRecord(binId: Int): Boolean {
        logTrace("permanentlyDeleteBinRecord start: binId=$binId")

        val deleted = database.planDao().deleteMetaHistoryBinById(binId) > 0

        logTrace("permanentlyDeleteBinRecord result: binId=$binId, deleted=$deleted")

        return deleted
    }

    fun permanentlyDeleteBinRecords(binIds: List<Int>): Int {
        logTrace("permanentlyDeleteBinRecords start: binIds=$binIds")

        val count = database.planDao().deleteMetaHistoryBinByIds(binIds)

        logTrace("permanentlyDeleteBinRecords result: count=$count")

        return count
    }

    fun revertBinRecord(binId: Int): Boolean {
        logTrace("revertBinRecord start: binId=$binId")

        val result = database.planDao().revertBinRecord(binId)

        logTrace("revertBinRecord result: binId=$binId, reverted=$result")

        return result
    }

    fun revertBinRecords(binIds: List<Int>): Int {
        logTrace("revertBinRecords start: binIds=$binIds")

        val count = database.planDao().revertBinRecords(binIds)

        logTrace("revertBinRecords result: count=$count")

        return count
    }

    fun seedPlansIfEmpty(
        plans: List<TrainingPlanState>,
        currentPlanId: Int
    ) {
        if (plans.isEmpty() || planStore.getPlans().isNotEmpty()) {
            return
        }

        val availableMotionsByTitle = getAvailableMotions().associateBy { motion -> motion.title }
        val createdPlanIds = mutableMapOf<Int, Int>()

        plans.forEach { plan ->
            val createdPlanId = createTrainingPlan(
                plan = plan.copy(
                    id = 0,
                    motions = emptyList()
                )
            )
            createdPlanIds[plan.id] = createdPlanId

            val seededPlan = plan.copy(
                id = createdPlanId,
                motions = plan.motions.mapNotNull { motion ->
                    val storedMotion = availableMotionsByTitle[motion.title] ?: return@mapNotNull null

                    motion.copy(
                        entryId = 0,
                        motionId = storedMotion.id
                    )
                }
            )
            updateTrainingPlan(seededPlan)
        }

        val resolvedCurrentPlanId = createdPlanIds[currentPlanId] ?: return
        setCurrentPlan(resolvedCurrentPlanId)
    }

    companion object {
        private const val TAG = "TrainingPlanStore"
        private const val MILLIS_PER_DAY = 86_400_000L

        fun from(context: Context): TrainingPlanStore {
            return fromDatabase(LiftInsightDatabase.from(context))
        }

        internal fun fromDatabase(
            database: LiftInsightDatabase,
            logger: AppLogger = AndroidAppLogger
        ): TrainingPlanStore {
            return TrainingPlanStore(
                motionStore = MotionStore.fromDatabase(database, logger),
                planStore = PlanStore.fromDatabase(database, logger),
                database = database,
                logger = logger
            )
        }
    }

    private fun logTrace(message: String) {
        logger.trace(TAG, message)
    }

    private fun localEpochDay(timestamp: Long): Long {
        val timeZoneOffsetMs = TimeZone.getDefault().getOffset(timestamp).toLong()

        return Math.floorDiv(timestamp + timeZoneOffsetMs, MILLIS_PER_DAY)
    }
}

private fun PlanRecord.toState(): TrainingPlanState {
    val sortedMetaPlans = metaPlans.sortedBy { metaPlan -> metaPlan.orderIndex }

    return TrainingPlanState(
        id = id,
        name = name,
        lastAppliedAt = lastAppliedAt,
        cyclePeriod = cyclePeriod,
        currentIndex = normalizePlanDayIndex(
            dayIndex = currentIndex,
            cyclePeriod = cyclePeriod
        ),
        motions = sortedMetaPlans
            .map { metaPlan ->
                PlanMotionState(
                    entryId = metaPlan.id,
                    motionId = metaPlan.motionId,
                    title = metaPlan.motionName,
                    dayIndex = metaPlan.dayIndex,
                    sets = metaPlan.sets,
                    repsPerSet = metaPlan.reps,
                    intensity = metaPlan.intensity,
                    weight = metaPlan.weight,
                    orderIndex = metaPlan.orderIndex
                )
            }
    )
}

private fun TrainingPlanState.toRecord(): PlanRecord {
    return PlanRecord(
        id = id,
        name = name,
        cyclePeriod = cyclePeriod,
        currentIndex = normalizePlanDayIndex(
            dayIndex = currentIndex,
            cyclePeriod = cyclePeriod
        ),
        lastAppliedAt = lastAppliedAt,
        metaPlans = motions.map { motion ->
            MetaPlanRecord(
                id = motion.entryId,
                motionId = motion.motionId,
                motionName = motion.title,
                dayIndex = normalizePlanDayIndex(
                    dayIndex = motion.dayIndex,
                    cyclePeriod = cyclePeriod
                ),
                sets = motion.sets,
                reps = motion.repsPerSet,
                intensity = motion.intensity,
                weight = motion.weight,
                orderIndex = motion.orderIndex
            )
        }
    )
}

private fun WorkoutSessionEntity.toState(): WorkoutSessionState {
    return WorkoutSessionState(
        isWorkoutGoing = isWorkoutGoing,
        isPaused = isPaused,
        startedAt = startedAt,
        lastResumedAt = lastResumedAt,
        elapsedBeforePauseMs = elapsedBeforePauseMs
    )
}

private fun WorkoutProgressEntity.toState(): WorkoutProgressState {
    return WorkoutProgressState(
        planId = planId,
        dayIndex = planDayIndex,
        nextSetIndex = nextSetIndex,
        activeSetIndex = activeSetIndex,
        totalSetCount = totalSetCount,
        breakEndsAt = breakEndsAt,
        isFinished = isFinished,
        completedElapsedTimeMs = completedElapsedTimeMs,
        activeHistoryId = activeHistoryId
    )
}

private fun WorkoutProgressState.toEntity(): WorkoutProgressEntity {
    return WorkoutProgressEntity(
        planId = planId,
        planDayIndex = dayIndex,
        nextSetIndex = nextSetIndex,
        activeSetIndex = activeSetIndex,
        totalSetCount = totalSetCount,
        breakEndsAt = breakEndsAt,
        isFinished = isFinished,
        completedElapsedTimeMs = completedElapsedTimeMs,
        activeHistoryId = activeHistoryId
    )
}

private fun CreateMetaHistoryRequest.toEntity(): MetaHistoryEntity {
    return MetaHistoryEntity(
        date = date,
        rep = rep,
        rpe = rpe,
        weight = weight,
        motionId = motionId,
        videoName = videoName,
        videoSource = videoSource.name,
        importedVideoAnalysisMode = importedVideoAnalysisMode.name,
        importedReferenceLabel = importedReferenceLabel,
        importedReferencePixelDistance = importedReferencePixelDistance,
        importedReferenceDistanceMeters = importedReferenceDistanceMeters,
        historyId = historyId
    )
}
