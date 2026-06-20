package com.potato.liftinsight.plan.controller

import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.plan.data.TrainingPlanSeedCatalog
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanState
import com.potato.liftinsight.plan.model.WorkoutSetPerformanceInput
import com.potato.liftinsight.plan.model.WorkoutSessionFeeling
import com.potato.liftinsight.video.NoOpVideoProcessor
import com.potato.liftinsight.video.VideoProcessor

class PlanController(
    internal val trainingPlanStore: TrainingPlanStore,
    internal val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val logger: AppLogger = AndroidAppLogger,
    internal val videoProcessor: VideoProcessor = NoOpVideoProcessor
) {
    fun emptyState(): PlanState {
        return PlanState(
            availableMotions = emptyList<AvailableMotionState>(),
            trainingPlans = emptyList(),
            currentPlanId = -1
        )
    }

    suspend fun loadState(seedCatalog: TrainingPlanSeedCatalog): PlanState =
        loadStateImpl(emptyState(), seedCatalog)

    fun createPlan(state: PlanState): PlanState = createPlanImpl(state)

    suspend fun selectPlan(state: PlanState, planId: Int): PlanState =
        selectPlanImpl(state, planId)

    fun showPlanList(state: PlanState): PlanState = showPlanListImpl(state)

    fun showPlanOverview(state: PlanState): PlanState = showPlanOverviewImpl(state)

    suspend fun startWorkout(state: PlanState): PlanState = startWorkoutImpl(state)

    fun openCamera(state: PlanState): PlanState = openCameraImpl(state)

    fun closeCamera(state: PlanState): PlanState = closeCameraImpl(state)

    fun closeCameraWithVideo(state: PlanState, videoName: String?): PlanState =
        closeCameraWithVideoImpl(state, videoName)

    fun clearCameraVideo(state: PlanState): PlanState = clearCameraVideoImpl(state)

    suspend fun startNextWorkoutSet(state: PlanState): PlanState =
        startNextWorkoutSetImpl(state)

    suspend fun skipWorkoutSet(state: PlanState): PlanState = skipWorkoutSetImpl(state)

    suspend fun finishCurrentWorkoutSet(
        state: PlanState,
        performance: WorkoutSetPerformanceInput
    ): PlanState = finishCurrentWorkoutSetImpl(state, performance)

    suspend fun toggleWorkoutPause(state: PlanState): PlanState =
        toggleWorkoutPauseImpl(state)

    fun requestWorkoutStop(state: PlanState): PlanState = requestWorkoutStopImpl(state)

    fun dismissWorkoutStop(state: PlanState): PlanState = dismissWorkoutStopImpl(state)

    suspend fun confirmWorkoutStop(state: PlanState): PlanState =
        confirmWorkoutStopImpl(state)

    suspend fun submitWorkoutFeeling(
        state: PlanState,
        feeling: WorkoutSessionFeeling
    ): PlanState = submitWorkoutFeelingImpl(state, feeling)

    fun showPlanDetail(state: PlanState, planId: Int): PlanState =
        showPlanDetailImpl(state, planId)

    fun showMotionDetail(state: PlanState, motionEntryId: Int): PlanState =
        showMotionDetailImpl(state, motionEntryId)

    suspend fun handlePlanBack(state: PlanState): PlanState = handlePlanBackImpl(state)

    suspend fun updatePlanEditorTitle(state: PlanState, newName: String): PlanState =
        updatePlanEditorTitleImpl(state, newName)

    suspend fun updatePlanEditorCyclePeriod(state: PlanState, cyclePeriod: Int?): PlanState =
        updatePlanEditorCyclePeriodImpl(state, cyclePeriod)

    fun selectPlanEditorDay(state: PlanState, dayIndex: Int): PlanState =
        selectPlanEditorDayImpl(state, dayIndex)

    suspend fun updatePlanCurrentDay(state: PlanState, planId: Int, dayIndex: Int): PlanState =
        updatePlanCurrentDayImpl(state, planId, dayIndex)

    suspend fun movePlanMotion(state: PlanState, motionEntryId: Int, direction: Int): PlanState =
        movePlanMotionImpl(state, motionEntryId, direction)

    suspend fun updateMotionSets(state: PlanState, motionEntryId: Int, sets: Int): PlanState =
        updateMotionSetsImpl(state, motionEntryId, sets)

    suspend fun updateMotionRepsPerSet(
        state: PlanState,
        motionEntryId: Int,
        repsPerSet: Int
    ): PlanState = updateMotionRepsPerSetImpl(state, motionEntryId, repsPerSet)

    suspend fun updateMotionWeight(
        state: PlanState,
        motionEntryId: Int,
        weight: Double
    ): PlanState = updateMotionWeightImpl(state, motionEntryId, weight)

    fun requestPlanDeletion(state: PlanState, planId: Int): PlanState =
        requestPlanDeletionImpl(state, planId)

    fun cancelPlanDeletion(state: PlanState): PlanState = cancelPlanDeletionImpl(state)

    suspend fun confirmPlanDeletion(state: PlanState): PlanState =
        confirmPlanDeletionImpl(state)

    fun requestMotionDeletion(state: PlanState, motionEntryId: Int): PlanState =
        requestMotionDeletionImpl(state, motionEntryId)

    fun cancelMotionDeletion(state: PlanState): PlanState = cancelMotionDeletionImpl(state)

    suspend fun confirmMotionDeletion(state: PlanState): PlanState =
        confirmMotionDeletionImpl(state)

    fun openAddMotionPicker(state: PlanState): PlanState = openAddMotionPickerImpl(state)

    fun closeAddMotionPicker(state: PlanState): PlanState = closeAddMotionPickerImpl(state)

    suspend fun addMotionToPlan(state: PlanState, motion: AvailableMotionState): PlanState =
        addMotionToPlanImpl(state, motion)

    suspend fun submitPlanEditor(state: PlanState): PlanState = submitPlanEditorImpl(state)

    suspend fun refreshState(state: PlanState): PlanState = refreshStateImpl(state)

    fun openWorkoutMotionPicker(state: PlanState): PlanState = openWorkoutMotionPickerImpl(state)

    fun closeWorkoutMotionPicker(state: PlanState): PlanState = closeWorkoutMotionPickerImpl(state)

    suspend fun insertMotionIntoWorkout(state: PlanState, motion: AvailableMotionState): PlanState =
        insertMotionIntoWorkoutImpl(state, motion)

    internal fun logDebug(message: String) {
        logger.debug(TAG, message)
    }

    internal fun logWarn(message: String) {
        logger.warn(TAG, message)
    }

    companion object {
        private const val TAG = "PlanController"
    }
}
