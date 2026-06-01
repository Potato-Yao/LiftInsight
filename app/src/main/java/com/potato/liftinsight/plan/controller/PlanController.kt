package com.potato.liftinsight.plan.controller

import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.plan.data.TrainingPlanSeedCatalog
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanState
import com.potato.liftinsight.plan.model.WorkoutSetPerformanceInput
import com.potato.liftinsight.video.NoOpVideoProcessor
import com.potato.liftinsight.video.VideoProcessor

class PlanController(
    private val trainingPlanStore: TrainingPlanStore,
    private val shouldSeedDebugPlans: Boolean = false,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val logger: AppLogger = AndroidAppLogger,
    private val videoProcessor: VideoProcessor = NoOpVideoProcessor
) {
    private val environment = PlanControllerEnvironment(
        trainingPlanStore = trainingPlanStore,
        shouldSeedDebugPlans = shouldSeedDebugPlans,
        nowProvider = nowProvider,
        logger = logger,
        videoProcessor = videoProcessor
    )

    fun emptyState(): PlanState {
        return PlanState(
            availableMotions = emptyList<AvailableMotionState>(),
            trainingPlans = emptyList(),
            currentPlanId = -1
        )
    }

    suspend fun loadState(seedCatalog: TrainingPlanSeedCatalog): PlanState =
        environment.loadState(emptyState(), seedCatalog)

    fun createPlan(state: PlanState): PlanState = environment.createPlan(state)

    suspend fun selectPlan(state: PlanState, planId: Int): PlanState =
        environment.selectPlan(state, planId)

    fun showPlanList(state: PlanState): PlanState = environment.showPlanList(state)

    fun showPlanOverview(state: PlanState): PlanState = environment.showPlanOverview(state)

    suspend fun startWorkout(state: PlanState): PlanState = environment.startWorkout(state)

    fun openCamera(state: PlanState): PlanState = environment.openCamera(state)

    fun closeCamera(state: PlanState): PlanState = environment.closeCamera(state)

    fun closeCameraWithVideo(state: PlanState, videoName: String?): PlanState =
        environment.closeCameraWithVideo(state, videoName)

    fun clearCameraVideo(state: PlanState): PlanState = environment.clearCameraVideo(state)

    suspend fun startNextWorkoutSet(state: PlanState): PlanState =
        environment.startNextWorkoutSet(state)

    suspend fun skipWorkoutSet(state: PlanState): PlanState = environment.skipWorkoutSet(state)

    suspend fun finishCurrentWorkoutSet(
        state: PlanState,
        performance: WorkoutSetPerformanceInput
    ): PlanState = environment.finishCurrentWorkoutSet(state, performance)

    suspend fun toggleWorkoutPause(state: PlanState): PlanState =
        environment.toggleWorkoutPause(state)

    fun requestWorkoutStop(state: PlanState): PlanState = environment.requestWorkoutStop(state)

    fun dismissWorkoutStop(state: PlanState): PlanState = environment.dismissWorkoutStop(state)

    suspend fun confirmWorkoutStop(state: PlanState): PlanState =
        environment.confirmWorkoutStop(state)

    fun showPlanDetail(state: PlanState, planId: Int): PlanState =
        environment.showPlanDetail(state, planId)

    fun showMotionDetail(state: PlanState, motionEntryId: Int): PlanState =
        environment.showMotionDetail(state, motionEntryId)

    suspend fun handlePlanBack(state: PlanState): PlanState = environment.handlePlanBack(state)

    suspend fun updatePlanEditorTitle(state: PlanState, newName: String): PlanState =
        environment.updatePlanEditorTitle(state, newName)

    suspend fun updatePlanEditorCyclePeriod(state: PlanState, cyclePeriod: Int?): PlanState =
        environment.updatePlanEditorCyclePeriod(state, cyclePeriod)

    fun selectPlanEditorDay(state: PlanState, dayIndex: Int): PlanState =
        environment.selectPlanEditorDay(state, dayIndex)

    suspend fun updatePlanCurrentDay(state: PlanState, planId: Int, dayIndex: Int): PlanState =
        environment.updatePlanCurrentDay(state, planId, dayIndex)

    suspend fun movePlanMotion(state: PlanState, motionEntryId: Int, direction: Int): PlanState =
        environment.movePlanMotion(state, motionEntryId, direction)

    suspend fun updateMotionSets(state: PlanState, motionEntryId: Int, sets: Int): PlanState =
        environment.updateMotionSets(state, motionEntryId, sets)

    suspend fun updateMotionRepsPerSet(
        state: PlanState,
        motionEntryId: Int,
        repsPerSet: Int
    ): PlanState = environment.updateMotionRepsPerSet(state, motionEntryId, repsPerSet)

    suspend fun updateMotionWeight(
        state: PlanState,
        motionEntryId: Int,
        weight: Double
    ): PlanState = environment.updateMotionWeight(state, motionEntryId, weight)

    fun requestPlanDeletion(state: PlanState, planId: Int): PlanState =
        environment.requestPlanDeletion(state, planId)

    fun cancelPlanDeletion(state: PlanState): PlanState = environment.cancelPlanDeletion(state)

    suspend fun confirmPlanDeletion(state: PlanState): PlanState =
        environment.confirmPlanDeletion(state)

    fun requestMotionDeletion(state: PlanState, motionEntryId: Int): PlanState =
        environment.requestMotionDeletion(state, motionEntryId)

    fun cancelMotionDeletion(state: PlanState): PlanState = environment.cancelMotionDeletion(state)

    suspend fun confirmMotionDeletion(state: PlanState): PlanState =
        environment.confirmMotionDeletion(state)

    fun openAddMotionPicker(state: PlanState): PlanState = environment.openAddMotionPicker(state)

    fun closeAddMotionPicker(state: PlanState): PlanState = environment.closeAddMotionPicker(state)

    suspend fun addMotionToPlan(state: PlanState, motion: com.potato.liftinsight.plan.model.AvailableMotionState): PlanState =
        environment.addMotionToPlan(state, motion)

    suspend fun submitPlanEditor(state: PlanState): PlanState = environment.submitPlanEditor(state)

    suspend fun refreshState(state: PlanState): PlanState = environment.refreshState(state)
}
