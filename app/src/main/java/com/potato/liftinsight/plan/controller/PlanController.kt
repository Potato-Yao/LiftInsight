package com.potato.liftinsight.plan.controller

import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.plan.data.TrainingPlanSeedCatalog
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.PlanState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.plan.model.WorkoutProgressState
import com.potato.liftinsight.plan.model.WorkoutSessionState
import com.potato.liftinsight.plan.model.WorkoutSetPerformanceInput
import com.potato.liftinsight.plan.model.completedWorkoutSetCount
import com.potato.liftinsight.plan.model.createWorkoutProgressState
import com.potato.liftinsight.plan.model.normalizedPlanCurrentIndex
import com.potato.liftinsight.plan.model.sanitizeWorkoutProgressState
import com.potato.liftinsight.plan.model.sortPlansByLastApplied
import com.potato.liftinsight.plan.model.todaysPlanMotions
import com.potato.liftinsight.plan.model.trainingPlan
import com.potato.liftinsight.plan.model.workoutElapsedTimeMs
import com.potato.liftinsight.plan.model.workoutSetTargetsForDay
import com.potato.liftinsight.plan.route.MotionDeleteTarget
import com.potato.liftinsight.plan.route.PlanEditorState
import com.potato.liftinsight.plan.route.PlanRoute
import com.potato.liftinsight.plan.model.finishWorkoutSet as finishWorkoutSetProgress
import com.potato.liftinsight.plan.model.skipWorkoutSet as skipWorkoutSetProgress
import com.potato.liftinsight.plan.model.startWorkoutSet as startWorkoutSetProgress
import com.potato.liftinsight.plan.model.updatePlanCurrentIndex as applyPlanCurrentIndexUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlanController(
    private val trainingPlanStore: TrainingPlanStore,
    private val shouldSeedDebugPlans: Boolean = false,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val logger: AppLogger = AndroidAppLogger
) {
    fun emptyState(): PlanState {
        return PlanState(
            availableMotions = emptyList(),
            trainingPlans = emptyList(),
            currentPlanId = -1
        )
    }

    suspend fun loadState(seedCatalog: TrainingPlanSeedCatalog): PlanState {
        logDebug(
            "Loading plan state: availableMotionSeeds=${seedCatalog.availableMotions.size}, debugPlanSeedEnabled=$shouldSeedDebugPlans"
        )

        return withContext(Dispatchers.IO) {
            trainingPlanStore.ensureAvailableMotions(seedCatalog.availableMotions)

            if (shouldSeedDebugPlans) {
                trainingPlanStore.seedPlansIfEmpty(
                    plans = seedCatalog.debugPlans,
                    currentPlanId = seedCatalog.debugCurrentPlanId
                )
            }

            val availableMotions = trainingPlanStore.getAvailableMotions()
            val now = nowProvider()
            var trainingPlans = trainingPlanStore.getTrainingPlans()
            var currentPlanId = resolveCurrentPlanId(trainingPlans, now)

            trainingPlanStore.advanceCurrentPlanDayIfNeeded(now)

            trainingPlans = trainingPlanStore.getTrainingPlans()
            currentPlanId = resolveCurrentPlanId(trainingPlans, now)
            val workoutSession = trainingPlanStore.getWorkoutSession()
            val workoutProgress = trainingPlanStore.getWorkoutProgress()

            buildLoadedState(
                state = emptyState(),
                availableMotions = availableMotions,
                trainingPlans = trainingPlans,
                currentPlanId = currentPlanId,
                workoutProgress = workoutProgress,
                workoutSession = workoutSession,
                requestedRoute = PlanRoute.Overview,
                planIdPendingDelete = null,
                motionPendingDelete = null,
                planEditor = null,
                workoutStopPendingConfirmation = false
            ).also { loadedState ->
                logDebug(
                    "Loaded plan state: motions=${loadedState.availableMotions.size}, plans=${loadedState.trainingPlans.size}, currentPlanId=${loadedState.currentPlanId}"
                )
            }
        }
    }

    fun createPlan(state: PlanState): PlanState {
        logDebug("Opening new plan editor")

        return state.copy(
            planRoute = PlanRoute.Editor,
            planIdPendingDelete = null,
            motionPendingDelete = null,
            planEditor = PlanEditorState()
        )
    }

    suspend fun selectPlan(
        state: PlanState,
        planId: Int
    ): PlanState {
        logDebug("Selecting training plan: planId=$planId")

        val updateSucceeded = withContext(Dispatchers.IO) {
            val selectedPlan = trainingPlanStore.getTrainingPlan(planId) ?: run {
                logWarn("Cannot select training plan because it was not found: planId=$planId")
                return@withContext false
            }

            val planSaved = trainingPlanStore.updateTrainingPlan(
                selectedPlan.copy(lastAppliedAt = nowProvider())
            )
            if (!planSaved) {
                logWarn("Failed to update selected training plan timestamp: planId=$planId")
                return@withContext false
            }

            trainingPlanStore.setCurrentPlan(planId, selectedAt = nowProvider())
        }

        if (!updateSucceeded) {
            return state
        }

        logDebug("Selected training plan: planId=$planId")

        return reloadState(state)
    }

    fun showPlanList(state: PlanState): PlanState {
        logDebug("Showing training plan list")
        return state.copy(planRoute = PlanRoute.List)
    }

    fun showPlanOverview(state: PlanState): PlanState {
        logDebug("Showing training plan overview")
        return state.copy(planRoute = PlanRoute.Overview)
    }

    suspend fun startWorkout(state: PlanState): PlanState {
        if (state.workoutSession.isWorkoutGoing) {
            logWarn("Ignoring workout start because a workout session is already running")
            return state
        }

        val currentPlan = trainingPlan(state.trainingPlans, state.currentPlanId) ?: run {
            logWarn("Ignoring workout start because no current plan is selected")
            return state
        }
        val todayMotions = todaysPlanMotions(currentPlan)
        val workoutTargets = workoutSetTargetsForDay(todayMotions)

        if (workoutTargets.isEmpty()) {
            logWarn("Ignoring workout start because the current plan has no sets scheduled for today")
            return state
        }

        val now = nowProvider()
        val dayIndex = normalizedPlanCurrentIndex(currentPlan)

        logDebug("Starting workout session")

        withContext(Dispatchers.IO) {
            trainingPlanStore.startWorkout(now)
            trainingPlanStore.saveWorkoutProgress(
                createWorkoutProgressState(
                    planId = currentPlan.id,
                    dayIndex = dayIndex,
                    totalSetCount = workoutTargets.size
                )
            )
        }

        return reloadState(state)
    }

    suspend fun startNextWorkoutSet(state: PlanState): PlanState {
        val workoutSession = state.workoutSession

        if (!workoutSession.isWorkoutGoing || workoutSession.isPaused) {
            logWarn("Ignoring start set because the workout session is not active")
            return state
        }

        val workoutProgress = state.workoutProgress ?: run {
            logWarn("Ignoring start set because no workout progress is available")
            return state
        }
        val updatedProgress = startWorkoutSetProgress(workoutProgress)

        if (updatedProgress == workoutProgress) {
            logWarn("Ignoring start set because the workout progress was not ready for the next set")
            return state
        }

        withContext(Dispatchers.IO) {
            trainingPlanStore.saveWorkoutProgress(updatedProgress)
        }

        logDebug("Started workout set: setIndex=${updatedProgress.activeSetIndex ?: -1}")

        return reloadState(state)
    }

    suspend fun skipWorkoutSet(state: PlanState): PlanState {
        val workoutProgress = state.workoutProgress ?: run {
            logWarn("Ignoring workout set skip because no workout progress is available")
            return state
        }

        if (!state.workoutSession.isWorkoutGoing || state.workoutSession.isPaused) {
            logWarn("Ignoring workout set skip because the workout session is not active")
            return state
        }

        val elapsedTimeMs = workoutElapsedTimeMs(state.workoutSession, nowProvider())
        val updatedProgress = skipWorkoutSetProgress(
            progress = workoutProgress,
            completedElapsedTimeMs = elapsedTimeMs
        )

        if (updatedProgress == workoutProgress) {
            logWarn("Ignoring workout set skip because the next set was not skippable")
            return state
        }

        withContext(Dispatchers.IO) {
            trainingPlanStore.saveWorkoutProgress(updatedProgress)

            if (updatedProgress.isFinished) {
                trainingPlanStore.stopWorkout()
            }
        }

        logDebug(
            "Skipped workout set: completedSets=${completedWorkoutSetCount(updatedProgress)}, totalSets=${updatedProgress.totalSetCount}, finished=${updatedProgress.isFinished}"
        )

        return reloadState(state)
    }

    suspend fun finishCurrentWorkoutSet(
        state: PlanState,
        performance: WorkoutSetPerformanceInput
    ): PlanState {
        val workoutProgress = state.workoutProgress ?: run {
            logWarn("Ignoring workout set completion because no workout progress is available")
            return state
        }

        if (!state.workoutSession.isWorkoutGoing || state.workoutSession.isPaused) {
            logWarn("Ignoring workout set completion because the workout session is not active")
            return state
        }

        val now = nowProvider()
        val elapsedTimeMs = workoutElapsedTimeMs(state.workoutSession, now)
        val updatedProgress = finishWorkoutSetProgress(
            progress = workoutProgress,
            completedElapsedTimeMs = elapsedTimeMs,
            finishedAt = now,
            breakDurationSeconds = performance.breakDurationSeconds
        )

        if (updatedProgress == workoutProgress) {
            logWarn("Ignoring workout set completion because no active set was found")
            return state
        }

        withContext(Dispatchers.IO) {
            trainingPlanStore.saveWorkoutProgress(updatedProgress)

            if (updatedProgress.isFinished) {
                trainingPlanStore.stopWorkout()
            }
        }

        logDebug(
            "Finished workout set: repsDone=${performance.repsDone}, weightDone=${performance.weightDone}, feeling=${performance.feeling}, breakDurationSeconds=${performance.breakDurationSeconds}, finished=${updatedProgress.isFinished}"
        )

        return reloadState(state)
    }

    suspend fun toggleWorkoutPause(state: PlanState): PlanState {
        val workoutSession = state.workoutSession

        if (!workoutSession.isWorkoutGoing) {
            logWarn("Ignoring workout pause toggle because no workout session is active")
            return state
        }

        val now = nowProvider()

        withContext(Dispatchers.IO) {
            if (workoutSession.isPaused) {
                trainingPlanStore.resumeWorkout(now)
            } else {
                trainingPlanStore.pauseWorkout(now)
            }
        }

        logDebug("Toggled workout pause state: isPaused=${!workoutSession.isPaused}")

        return reloadState(state)
    }

    fun requestWorkoutStop(state: PlanState): PlanState {
        if (!state.workoutSession.isWorkoutGoing) {
            logWarn("Ignoring workout stop request because no workout session is active")
            return state
        }

        logDebug("Requesting workout stop confirmation")

        return state.copy(workoutStopPendingConfirmation = true)
    }

    fun dismissWorkoutStop(state: PlanState): PlanState {
        logDebug("Dismissing workout stop confirmation")
        return state.copy(workoutStopPendingConfirmation = false)
    }

    suspend fun confirmWorkoutStop(state: PlanState): PlanState {
        if (!state.workoutStopPendingConfirmation) {
            logWarn("Ignoring workout stop confirmation because no workout stop is pending")
            return state
        }

        logDebug("Stopping workout session")

        withContext(Dispatchers.IO) {
            trainingPlanStore.stopWorkout()
            trainingPlanStore.clearWorkoutProgress()
        }

        return reloadState(
            state = state,
            workoutStopPendingConfirmation = false
        )
    }

    fun showPlanDetail(
        state: PlanState,
        planId: Int
    ): PlanState {
        val plan = trainingPlan(state.trainingPlans, planId)

        if (plan == null) {
            logWarn("Cannot show plan detail because the plan was not found: planId=$planId")
            return state.copy(planRoute = PlanRoute.List)
        }

        logDebug("Showing plan detail editor: planId=$planId")

        return state.copy(
            planRoute = PlanRoute.Editor,
            planIdPendingDelete = null,
            motionPendingDelete = null,
            planEditor = plan.toEditorState()
        )
    }

    fun showMotionDetail(
        state: PlanState,
        motionEntryId: Int
    ): PlanState {
        val editor = state.planEditor ?: run {
            logWarn("Cannot show plan motion detail without an active plan editor: motionEntryId=$motionEntryId")
            return state.copy(planRoute = PlanRoute.List)
        }
        val motion = editor.motions.firstOrNull { planMotion -> planMotion.entryId == motionEntryId }

        if (motion == null) {
            logWarn("Cannot show plan motion detail because the motion entry was not found: motionEntryId=$motionEntryId")
            return state.copy(planRoute = PlanRoute.List)
        }

        logDebug("Showing plan motion detail: motionEntryId=$motionEntryId")

        return state.copy(planRoute = PlanRoute.Motion(motionEntryId = motionEntryId))
    }

    suspend fun handlePlanBack(state: PlanState): PlanState {
        logDebug("Handling plan back navigation from route=${state.planRoute}")

        return when (state.planRoute) {
            PlanRoute.Overview -> state

            PlanRoute.List -> state.copy(
                planRoute = PlanRoute.Overview,
                planIdPendingDelete = null,
                motionPendingDelete = null,
                planEditor = null
            )

            PlanRoute.Editor -> state.copy(
                planRoute = PlanRoute.List,
                planIdPendingDelete = null,
                motionPendingDelete = null,
                planEditor = null
            )

            PlanRoute.MotionPicker -> state.copy(
                planRoute = PlanRoute.Editor,
                motionPendingDelete = null
            )

            is PlanRoute.Motion -> state.copy(
                planRoute = PlanRoute.Editor,
                motionPendingDelete = null
            )
        }
    }

    suspend fun updatePlanEditorTitle(
        state: PlanState,
        newName: String
    ): PlanState {
        val editor = state.planEditor ?: run {
            logWarn("Ignoring plan title update because no plan editor is open")
            return state
        }
        val updatedEditor = editor.copy(
            title = newName,
            titleError = false
        )

        logDebug("Updating plan title: planId=${editor.planId ?: -1}, isNewPlan=${editor.isNewPlan}")

        if (updatedEditor.isNewPlan) {
            return state.copy(planEditor = updatedEditor)
        }

        val updatedState = persistEditorPlan(state, updatedEditor)

        if (updatedState == null) {
            logWarn("Failed to persist updated plan title: planId=${editor.planId}")
            return state
        }

        return updatedState
    }

    suspend fun updatePlanEditorCyclePeriod(
        state: PlanState,
        cyclePeriod: Int?
    ): PlanState {
        val editor = state.planEditor ?: run {
            logWarn("Ignoring cycle period update because no plan editor is open")
            return state
        }
        val normalizedCyclePeriod = cyclePeriod?.takeIf { value -> value > 0 }
        val selectedDayIndex = when {
            normalizedCyclePeriod == null -> null
            editor.selectedDayIndex == null -> null
            editor.selectedDayIndex > normalizedCyclePeriod -> normalizedCyclePeriod
            else -> editor.selectedDayIndex
        }
        val currentIndex = if (normalizedCyclePeriod == null) {
            1
        } else {
            editor.currentIndex.coerceIn(1, normalizedCyclePeriod)
        }
        val updatedEditor = editor.copy(
            cyclePeriod = normalizedCyclePeriod,
            currentIndex = currentIndex,
            selectedDayIndex = selectedDayIndex,
            motions = normalizeEditorMotions(
                motions = editor.motions.filter { motion ->
                    normalizedCyclePeriod == null || motion.dayIndex <= normalizedCyclePeriod
                },
                cyclePeriod = normalizedCyclePeriod
            ),
            cyclePeriodError = false
        )

        logDebug(
            "Updating plan cycle period: planId=${editor.planId ?: -1}, requested=$cyclePeriod, normalized=$normalizedCyclePeriod"
        )

        if (updatedEditor.isNewPlan) {
            return state.copy(planEditor = updatedEditor)
        }

        val updatedState = persistEditorPlan(state, updatedEditor)

        if (updatedState == null) {
            logWarn("Failed to persist updated cycle period: planId=${editor.planId}")
            return state
        }

        return updatedState
    }

    fun selectPlanEditorDay(
        state: PlanState,
        dayIndex: Int
    ): PlanState {
        val editor = state.planEditor ?: run {
            logWarn("Ignoring plan editor day selection because no plan editor is open")
            return state
        }
        val cyclePeriod = editor.cyclePeriod ?: run {
            logWarn("Ignoring plan editor day selection because cycle period is not set")
            return state
        }
        val normalizedDayIndex = dayIndex.coerceIn(1, cyclePeriod)

        logDebug(
            "Selecting plan editor day: planId=${editor.planId ?: -1}, requested=$dayIndex, selected=$normalizedDayIndex"
        )

        return state.copy(
            planEditor = editor.copy(selectedDayIndex = normalizedDayIndex)
        )
    }

    suspend fun updatePlanCurrentDay(
        state: PlanState,
        planId: Int,
        dayIndex: Int
    ): PlanState {
        logDebug("Updating current plan day: planId=$planId, requestedDayIndex=$dayIndex")

        val updateSucceeded = withContext(Dispatchers.IO) {
            val storedPlan = trainingPlanStore.getTrainingPlan(planId) ?: run {
                logWarn("Cannot update current day because the plan was not found: planId=$planId")
                return@withContext false
            }
            val updatedPlan = applyPlanCurrentIndexUpdate(
                plans = listOf(storedPlan),
                planId = planId,
                dayIndex = dayIndex
            ).first()

            if (updatedPlan == storedPlan) {
                logWarn("Current plan day update produced no change: planId=$planId, requestedDayIndex=$dayIndex")
                return@withContext false
            }

            val planUpdated = trainingPlanStore.updateTrainingPlan(updatedPlan)

            if (!planUpdated) {
                return@withContext false
            }

            if (trainingPlanStore.getCurrentPlanId() == planId) {
                trainingPlanStore.setCurrentPlan(planId, selectedAt = nowProvider())
            }

            true
        }

        if (!updateSucceeded) {
            return state
        }

        logDebug("Updated current plan day: planId=$planId")

        return reloadState(state)
    }

    suspend fun movePlanMotion(
        state: PlanState,
        motionEntryId: Int,
        direction: Int
    ): PlanState {
        val editor = state.planEditor ?: run {
            logWarn("Ignoring plan motion reorder because no plan editor is open: motionEntryId=$motionEntryId")
            return state
        }
        val updatedMotions = reorderEditorMotions(editor.motions, motionEntryId, direction)

        if (updatedMotions == editor.motions) {
            logWarn("Plan motion reorder produced no change: motionEntryId=$motionEntryId, direction=$direction")
            return state
        }

        val updatedEditor = editor.copy(motions = updatedMotions)

        logDebug("Reordering plan motion: planId=${editor.planId ?: -1}, motionEntryId=$motionEntryId, direction=$direction")

        if (updatedEditor.isNewPlan) {
            return state.copy(planEditor = updatedEditor)
        }

        return persistEditorPlan(state, updatedEditor) ?: run {
            logWarn("Failed to persist reordered plan motion: planId=${editor.planId}, motionEntryId=$motionEntryId")
            state
        }
    }

    suspend fun updateMotionSets(
        state: PlanState,
        motionEntryId: Int,
        sets: Int
    ): PlanState {
        val editor = state.planEditor ?: run {
            logWarn("Ignoring motion sets update because no plan editor is open: motionEntryId=$motionEntryId")
            return state
        }
        val updatedEditor = editor.copy(
            motions = editor.motions.map { motion ->
                if (motion.entryId == motionEntryId) {
                    motion.copy(sets = sets.coerceAtLeast(1))
                } else {
                    motion
                }
            }
        )

        if (updatedEditor == editor) {
            logWarn("Motion sets update produced no change: motionEntryId=$motionEntryId, requestedSets=$sets")
            return state
        }

        logDebug("Updating motion sets: planId=${editor.planId ?: -1}, motionEntryId=$motionEntryId, requestedSets=$sets")

        if (updatedEditor.isNewPlan) {
            return state.copy(planEditor = updatedEditor)
        }

        return persistEditorPlan(state, updatedEditor) ?: run {
            logWarn("Failed to persist motion sets update: planId=${editor.planId}, motionEntryId=$motionEntryId")
            state
        }
    }

    suspend fun updateMotionRepsPerSet(
        state: PlanState,
        motionEntryId: Int,
        repsPerSet: Int
    ): PlanState {
        val editor = state.planEditor ?: run {
            logWarn("Ignoring motion reps update because no plan editor is open: motionEntryId=$motionEntryId")
            return state
        }
        val updatedEditor = editor.copy(
            motions = editor.motions.map { motion ->
                if (motion.entryId == motionEntryId) {
                    motion.copy(repsPerSet = repsPerSet.coerceAtLeast(1))
                } else {
                    motion
                }
            }
        )

        if (updatedEditor == editor) {
            logWarn("Motion reps update produced no change: motionEntryId=$motionEntryId, requestedRepsPerSet=$repsPerSet")
            return state
        }

        logDebug(
            "Updating motion reps per set: planId=${editor.planId ?: -1}, motionEntryId=$motionEntryId, requestedRepsPerSet=$repsPerSet"
        )

        if (updatedEditor.isNewPlan) {
            return state.copy(planEditor = updatedEditor)
        }

        return persistEditorPlan(state, updatedEditor) ?: run {
            logWarn("Failed to persist motion reps update: planId=${editor.planId}, motionEntryId=$motionEntryId")
            state
        }
    }

    suspend fun updateMotionWeight(
        state: PlanState,
        motionEntryId: Int,
        weight: Double
    ): PlanState {
        val editor = state.planEditor ?: run {
            logWarn("Ignoring motion weight update because no plan editor is open: motionEntryId=$motionEntryId")
            return state
        }
        val updatedEditor = editor.copy(
            motions = editor.motions.map { motion ->
                if (motion.entryId == motionEntryId) {
                    motion.copy(weight = weight.coerceAtLeast(0.0))
                } else {
                    motion
                }
            }
        )

        if (updatedEditor == editor) {
            logWarn("Motion weight update produced no change: motionEntryId=$motionEntryId, requestedWeight=$weight")
            return state
        }

        logDebug("Updating motion weight: planId=${editor.planId ?: -1}, motionEntryId=$motionEntryId, requestedWeight=$weight")

        if (updatedEditor.isNewPlan) {
            return state.copy(planEditor = updatedEditor)
        }

        return persistEditorPlan(state, updatedEditor) ?: run {
            logWarn("Failed to persist motion weight update: planId=${editor.planId}, motionEntryId=$motionEntryId")
            state
        }
    }

    fun requestPlanDeletion(
        state: PlanState,
        planId: Int
    ): PlanState {
        if (trainingPlan(state.trainingPlans, planId) == null) {
            logWarn("Ignoring plan deletion request because the plan was not found: planId=$planId")
            return state
        }

        logDebug("Requesting plan deletion: planId=$planId")

        return state.copy(planIdPendingDelete = planId)
    }

    fun cancelPlanDeletion(state: PlanState): PlanState {
        logDebug("Cancelling plan deletion request")
        return state.copy(planIdPendingDelete = null)
    }

    suspend fun confirmPlanDeletion(state: PlanState): PlanState {
        val pendingPlanId = state.planIdPendingDelete ?: run {
            logWarn("Ignoring plan deletion confirmation because no plan is pending deletion")
            return state
        }

        logDebug("Confirming plan deletion: planId=$pendingPlanId")

        val deleteSucceeded = withContext(Dispatchers.IO) {
            deleteTrainingPlanAndUpdateSelection(pendingPlanId)
        }

        if (!deleteSucceeded) {
            logWarn("Failed to delete training plan: planId=$pendingPlanId")
            return state
        }

        return reloadState(
            state = state,
            requestedRoute = PlanRoute.List,
            planIdPendingDelete = null,
            motionPendingDelete = null,
            planEditor = state.planEditor?.takeUnless { editor -> editor.planId == pendingPlanId }
        )
    }

    fun requestMotionDeletion(
        state: PlanState,
        motionEntryId: Int
    ): PlanState {
        val editor = state.planEditor ?: run {
            logWarn("Ignoring motion deletion request because no plan editor is open: motionEntryId=$motionEntryId")
            return state
        }
        val planId = editor.planId ?: run {
            logWarn("Ignoring motion deletion request because the plan has not been created yet: motionEntryId=$motionEntryId")
            return state
        }
        val motion = editor.motions.firstOrNull { planMotion -> planMotion.entryId == motionEntryId }

        if (motion == null) {
            logWarn("Ignoring motion deletion request because the motion entry was not found: motionEntryId=$motionEntryId")
            return state
        }

        logDebug("Requesting motion deletion: planId=$planId, motionEntryId=$motionEntryId")

        return state.copy(
            motionPendingDelete = MotionDeleteTarget(
                planId = planId,
                motionEntryId = motionEntryId
            )
        )
    }

    fun cancelMotionDeletion(state: PlanState): PlanState {
        logDebug("Cancelling motion deletion request")
        return state.copy(motionPendingDelete = null)
    }

    suspend fun confirmMotionDeletion(state: PlanState): PlanState {
        val pendingTarget = state.motionPendingDelete ?: run {
            logWarn("Ignoring motion deletion confirmation because no motion is pending deletion")
            return state
        }
        val editor = state.planEditor ?: run {
            logWarn("Ignoring motion deletion confirmation because no plan editor is open")
            return state
        }

        logDebug(
            "Confirming motion deletion: planId=${pendingTarget.planId}, motionEntryId=${pendingTarget.motionEntryId}"
        )

        val updatedEditor = editor.copy(
            motions = normalizeEditorMotions(
                motions = editor.motions.filterNot { motion ->
                    motion.entryId == pendingTarget.motionEntryId
                },
                cyclePeriod = editor.cyclePeriod
            )
        )

        val updatedState = persistEditorPlan(
            state = state.copy(planEditor = updatedEditor),
            editor = updatedEditor,
            requestedRoute = PlanRoute.Editor,
            motionPendingDelete = null
        )

        if (updatedState == null) {
            logWarn(
                "Failed to delete motion from plan: planId=${pendingTarget.planId}, motionEntryId=${pendingTarget.motionEntryId}"
            )
            return state
        }

        return updatedState
    }

    fun openAddMotionPicker(state: PlanState): PlanState {
        val editor = state.planEditor ?: run {
            logWarn("Ignoring add motion picker request because no plan editor is open")
            return state
        }

        if (editor.cyclePeriod == null || editor.selectedDayIndex == null) {
            logWarn("Ignoring add motion picker request because day selection is incomplete")
            return state
        }

        logDebug("Opening add motion picker: planId=${editor.planId ?: -1}, selectedDayIndex=${editor.selectedDayIndex}")

        return state.copy(planRoute = PlanRoute.MotionPicker)
    }

    fun closeAddMotionPicker(state: PlanState): PlanState {
        logDebug("Closing add motion picker")
        return state.copy(planRoute = PlanRoute.Editor)
    }

    suspend fun addMotionToPlan(
        state: PlanState,
        motion: AvailableMotionState
    ): PlanState {
        val editor = state.planEditor ?: run {
            logWarn("Ignoring add motion request because no plan editor is open: motionId=${motion.id}")
            return state
        }
        val selectedDayIndex = editor.selectedDayIndex ?: run {
            logWarn("Ignoring add motion request because no training day is selected: motionId=${motion.id}")
            return state
        }
        val addedMotionEntryId = editor.nextTemporaryMotionEntryId
        val nextOrderIndex = editor.motions
            .filter { existingMotion -> existingMotion.dayIndex == selectedDayIndex }
            .maxOfOrNull { existingMotion -> existingMotion.orderIndex }
            ?.plus(1)
            ?: 1
        val updatedEditor = editor.copy(
            motions = normalizeEditorMotions(
                motions = editor.motions + PlanMotionState(
                    entryId = addedMotionEntryId,
                    motionId = motion.id,
                    title = motion.title,
                    dayIndex = selectedDayIndex,
                    sets = 1,
                    repsPerSet = 1,
                    intensity = 0.0,
                    weight = 0.0,
                    orderIndex = nextOrderIndex
                ),
                cyclePeriod = editor.cyclePeriod
            ),
            nextTemporaryMotionEntryId = editor.nextTemporaryMotionEntryId - 1
        )

        logDebug(
            "Adding motion to plan: planId=${editor.planId ?: -1}, motionId=${motion.id}, motionEntryId=$addedMotionEntryId, dayIndex=$selectedDayIndex"
        )

        if (updatedEditor.isNewPlan) {
            return state.copy(
                planRoute = PlanRoute.Motion(motionEntryId = addedMotionEntryId),
                planEditor = updatedEditor
            )
        }

        return persistEditorPlan(
            state = state,
            editor = updatedEditor,
            requestedRoute = PlanRoute.Motion(motionEntryId = addedMotionEntryId)
        ) ?: run {
            logWarn("Failed to persist added motion: planId=${editor.planId}, motionId=${motion.id}")
            state
        }
    }

    suspend fun submitPlanEditor(state: PlanState): PlanState {
        val editor = state.planEditor ?: run {
            logWarn("Ignoring plan submission because no plan editor is open")
            return state
        }
        val title = editor.title.trim()
        val cyclePeriod = editor.cyclePeriod
        val hasTitleError = title.isEmpty()
        val hasCyclePeriodError = cyclePeriod == null || cyclePeriod <= 0

        logDebug("Submitting plan editor: planId=${editor.planId ?: -1}, isNewPlan=${editor.isNewPlan}")

        if (hasTitleError || hasCyclePeriodError) {
            logWarn(
                "Plan submission validation failed: planId=${editor.planId ?: -1}, titleError=$hasTitleError, cyclePeriodError=$hasCyclePeriodError"
            )
            return state.copy(
                planEditor = editor.copy(
                    titleError = hasTitleError,
                    cyclePeriodError = hasCyclePeriodError
                )
            )
        }

        if (!editor.isNewPlan) {
            return persistEditorPlan(state, editor) ?: run {
                logWarn("Failed to persist existing plan submission: planId=${editor.planId}")
                state
            }
        }

        val createdPlanId = withContext(Dispatchers.IO) {
            trainingPlanStore.createTrainingPlan(
                plan = editor.toTrainingPlanState(createdAt = nowProvider())
            )
        }

        val storedPlan = withContext(Dispatchers.IO) {
            trainingPlanStore.getTrainingPlan(createdPlanId)
        } ?: run {
            logWarn("Created plan could not be reloaded after persistence: createdPlanId=$createdPlanId")
            return state
        }

        logDebug("Created new training plan: planId=$createdPlanId")

        return reloadState(
            state = state,
            requestedRoute = PlanRoute.Editor,
            planEditor = storedPlan.toEditorState()
        )
    }

    suspend fun refreshState(state: PlanState): PlanState {
        logDebug("Refreshing plan state")
        return reloadState(state)
    }

    private suspend fun reloadState(
        state: PlanState,
        requestedRoute: PlanRoute = state.planRoute,
        planIdPendingDelete: Int? = state.planIdPendingDelete,
        motionPendingDelete: MotionDeleteTarget? = state.motionPendingDelete,
        planEditor: PlanEditorState? = state.planEditor,
        workoutStopPendingConfirmation: Boolean = state.workoutStopPendingConfirmation
    ): PlanState {
        return withContext(Dispatchers.IO) {
            val availableMotions = trainingPlanStore.getAvailableMotions()
            val now = nowProvider()
            var trainingPlans = trainingPlanStore.getTrainingPlans()
            var currentPlanId = resolveCurrentPlanId(trainingPlans, now)

            trainingPlanStore.advanceCurrentPlanDayIfNeeded(now)

            trainingPlans = trainingPlanStore.getTrainingPlans()
            currentPlanId = resolveCurrentPlanId(trainingPlans, now)
            val workoutSession = trainingPlanStore.getWorkoutSession()
            val workoutProgress = trainingPlanStore.getWorkoutProgress()

            buildLoadedState(
                state = state,
                availableMotions = availableMotions,
                trainingPlans = trainingPlans,
                currentPlanId = currentPlanId,
                workoutProgress = workoutProgress,
                workoutSession = workoutSession,
                requestedRoute = requestedRoute,
                planIdPendingDelete = planIdPendingDelete,
                motionPendingDelete = motionPendingDelete,
                planEditor = planEditor,
                workoutStopPendingConfirmation = workoutStopPendingConfirmation
            )
        }
    }

    private fun buildLoadedState(
        state: PlanState,
        availableMotions: List<AvailableMotionState>,
        trainingPlans: List<TrainingPlanState>,
        currentPlanId: Int,
        workoutProgress: WorkoutProgressState?,
        workoutSession: WorkoutSessionState,
        requestedRoute: PlanRoute,
        planIdPendingDelete: Int?,
        motionPendingDelete: MotionDeleteTarget?,
        planEditor: PlanEditorState?,
        workoutStopPendingConfirmation: Boolean
    ): PlanState {
        val sanitizedPlanEditor = sanitizePlanEditor(trainingPlans, availableMotions, planEditor)
        val currentPlan = trainingPlan(trainingPlans, currentPlanId)
        val todayTargets = currentPlan?.let { plan ->
            workoutSetTargetsForDay(todaysPlanMotions(plan))
        }.orEmpty()
        val sanitizedWorkoutProgress = if (currentPlan == null) {
            null
        } else {
            sanitizeWorkoutProgressState(
                progress = workoutProgress,
                planId = currentPlan.id,
                dayIndex = normalizedPlanCurrentIndex(currentPlan),
                totalSetCount = todayTargets.size
            )
        }

        return state.copy(
            availableMotions = availableMotions,
            trainingPlans = trainingPlans,
            currentPlanId = currentPlanId,
            workoutProgress = sanitizedWorkoutProgress,
            workoutSession = workoutSession,
            planEditor = sanitizedPlanEditor,
            planRoute = sanitizePlanRoute(
                requestedRoute = requestedRoute,
                planEditor = sanitizedPlanEditor
            ),
            planIdPendingDelete = planIdPendingDelete?.takeIf { planId ->
                trainingPlan(trainingPlans, planId) != null
            },
            motionPendingDelete = motionPendingDelete?.takeIf { pendingTarget ->
                sanitizedPlanEditor?.motions?.any { motion ->
                    motion.entryId == pendingTarget.motionEntryId &&
                        sanitizedPlanEditor.planId == pendingTarget.planId
                } == true
            },
            workoutStopPendingConfirmation = workoutStopPendingConfirmation && workoutSession.isWorkoutGoing
        )
    }

    private fun sanitizePlanRoute(
        requestedRoute: PlanRoute,
        planEditor: PlanEditorState?
    ): PlanRoute {
        return when (requestedRoute) {
            PlanRoute.Overview -> PlanRoute.Overview

            PlanRoute.List -> PlanRoute.List

            PlanRoute.Editor -> {
                if (planEditor == null) {
                    PlanRoute.List
                } else {
                    PlanRoute.Editor
                }
            }

            PlanRoute.MotionPicker -> {
                if (planEditor == null || planEditor.selectedDayIndex == null || planEditor.cyclePeriod == null) {
                    PlanRoute.List
                } else {
                    PlanRoute.MotionPicker
                }
            }

            is PlanRoute.Motion -> {
                if (planEditor == null) {
                    PlanRoute.List
                } else if (planEditor.motions.none { motion -> motion.entryId == requestedRoute.motionEntryId }) {
                    PlanRoute.Editor
                } else {
                    requestedRoute
                }
            }
        }
    }

    private suspend fun persistEditorPlan(
        state: PlanState,
        editor: PlanEditorState,
        requestedRoute: PlanRoute = state.planRoute,
        motionPendingDelete: MotionDeleteTarget? = state.motionPendingDelete
    ): PlanState? {
        val planId = editor.planId ?: return state.copy(planEditor = editor)
        val storedPlan = withContext(Dispatchers.IO) {
            trainingPlanStore.getTrainingPlan(planId)
        } ?: run {
            logWarn("Cannot persist plan editor because the stored plan was not found: planId=$planId")
            return null
        }
        val cyclePeriod = editor.cyclePeriod ?: return state.copy(planEditor = editor)
        val normalizedEditor = editor.copy(
            title = editor.title.trim(),
            cyclePeriod = cyclePeriod,
            currentIndex = storedPlan.currentIndex.coerceIn(1, cyclePeriod),
            motions = normalizeEditorMotions(editor.motions, cyclePeriod)
        )

        if (normalizedEditor.title.isEmpty()) {
            logWarn("Cannot persist plan editor because the title is blank: planId=$planId")
            return state.copy(planEditor = normalizedEditor.copy(titleError = true))
        }

        val updateSucceeded = withContext(Dispatchers.IO) {
            trainingPlanStore.updateTrainingPlan(
                normalizedEditor.toTrainingPlanState(createdAt = storedPlan.lastAppliedAt)
                    .copy(id = planId)
            )
        }

        if (!updateSucceeded) {
            logWarn("Training plan persistence failed: planId=$planId")
            return null
        }

        val refreshedPlan = withContext(Dispatchers.IO) {
            trainingPlanStore.getTrainingPlan(planId)
        } ?: run {
            logWarn("Training plan reload failed after persistence: planId=$planId")
            return null
        }
        val refreshedEditor = refreshedPlan.toEditorState().copy(
            selectedDayIndex = normalizedEditor.selectedDayIndex,
            titleError = false,
            cyclePeriodError = false
        )
        val persistedMotionEntryIds = persistedMotionEntryIds(
            savedMotions = normalizedEditor.motions,
            refreshedMotions = refreshedEditor.motions
        )

        logDebug("Persisted plan editor changes: planId=$planId")

        return reloadState(
            state = state,
            requestedRoute = resolvePersistedPlanRoute(
                requestedRoute = requestedRoute,
                persistedMotionEntryIds = persistedMotionEntryIds
            ),
            motionPendingDelete = motionPendingDelete?.toPersistedTarget(
                persistedMotionEntryIds = persistedMotionEntryIds
            ),
            planEditor = refreshedEditor
        )
    }

    private fun resolveCurrentPlanId(
        trainingPlans: List<TrainingPlanState>,
        now: Long
    ): Int {
        val storedCurrentPlanId = trainingPlanStore.getCurrentPlanId()

        if (trainingPlans.any { plan -> plan.id == storedCurrentPlanId }) {
            return storedCurrentPlanId
        }

        val fallbackPlanId = sortPlansByLastApplied(trainingPlans).firstOrNull()?.id ?: -1

        if (fallbackPlanId == -1) {
            logWarn("No valid current training plan found; clearing selection")
            trainingPlanStore.clearCurrentPlan()
        } else {
            logWarn(
                "Stored current training plan was invalid; falling back to the most recently applied plan: planId=$fallbackPlanId"
            )
            trainingPlanStore.setCurrentPlan(fallbackPlanId, selectedAt = now)
        }

        return fallbackPlanId
    }

    private fun deleteTrainingPlanAndUpdateSelection(planId: Int): Boolean {
        val wasCurrentPlan = trainingPlanStore.getCurrentPlanId() == planId
        val deleted = trainingPlanStore.deleteTrainingPlan(planId)

        if (!deleted) {
            logWarn("Training plan delete operation was rejected: planId=$planId")
            return false
        }

        if (!wasCurrentPlan) {
            return true
        }

        val fallbackPlanId = sortPlansByLastApplied(trainingPlanStore.getTrainingPlans())
            .firstOrNull()
            ?.id
            ?: -1

        if (fallbackPlanId == -1) {
            logWarn("Deleted current training plan and no fallback remained; clearing current selection")
            trainingPlanStore.clearCurrentPlan()
        } else {
            logDebug("Deleted current training plan and selected fallback plan: planId=$fallbackPlanId")
            trainingPlanStore.setCurrentPlan(fallbackPlanId, selectedAt = nowProvider())
        }

        return true
    }

    private fun logDebug(message: String) {
        logger.debug(TAG, message)
    }

    private fun logWarn(message: String) {
        logger.warn(TAG, message)
    }

    companion object {
        private const val TAG = "PlanController"
    }
}

private fun TrainingPlanState.toEditorState(): PlanEditorState {
    return PlanEditorState(
        planId = id,
        title = name,
        cyclePeriod = cyclePeriod,
        currentIndex = currentIndex,
        selectedDayIndex = currentIndex.coerceIn(1, cyclePeriod),
        motions = normalizeEditorMotions(
            motions = motions,
            cyclePeriod = cyclePeriod
        ),
        nextTemporaryMotionEntryId = -1
    )
}

private fun PlanEditorState.toTrainingPlanState(createdAt: Long): TrainingPlanState {
    val normalizedCyclePeriod = cyclePeriod ?: 1

    return TrainingPlanState(
        id = planId ?: 0,
        name = title.trim(),
        lastAppliedAt = createdAt,
        cyclePeriod = normalizedCyclePeriod,
        currentIndex = currentIndex.coerceIn(1, normalizedCyclePeriod),
        motions = normalizeEditorMotions(
            motions = motions,
            cyclePeriod = normalizedCyclePeriod
        )
    )
}

private fun normalizeEditorMotions(
    motions: List<PlanMotionState>,
    cyclePeriod: Int?
): List<PlanMotionState> {
    if (motions.isEmpty()) {
        return emptyList()
    }

    val dayLimit = cyclePeriod ?: Int.MAX_VALUE
    val sortedMotions = motions
        .filter { motion -> motion.dayIndex in 1..dayLimit }
        .sortedWith(
            compareBy<PlanMotionState> { motion -> motion.dayIndex }
                .thenBy { motion -> motion.orderIndex }
                .thenBy { motion -> motion.entryId }
        )
    val nextOrderIndexByDay = mutableMapOf<Int, Int>()

    return sortedMotions.map { motion ->
        val nextOrderIndex = (nextOrderIndexByDay[motion.dayIndex] ?: 0) + 1
        nextOrderIndexByDay[motion.dayIndex] = nextOrderIndex

        motion.copy(orderIndex = nextOrderIndex)
    }
}

private fun reorderEditorMotions(
    motions: List<PlanMotionState>,
    motionEntryId: Int,
    direction: Int
): List<PlanMotionState> {
    val movingMotion = motions.firstOrNull { motion -> motion.entryId == motionEntryId } ?: return motions
    val dayMotions = motions.filter { motion -> motion.dayIndex == movingMotion.dayIndex }
    val currentIndex = dayMotions.indexOfFirst { motion -> motion.entryId == motionEntryId }

    if (currentIndex == -1) {
        return motions
    }

    val targetIndex = currentIndex + direction

    if (targetIndex !in dayMotions.indices) {
        return motions
    }

    val reorderedDayMotions = dayMotions.toMutableList()
    val removedMotion = reorderedDayMotions.removeAt(currentIndex)
    reorderedDayMotions.add(targetIndex, removedMotion)
    val reorderedByEntryId = normalizeEditorMotions(reorderedDayMotions, cyclePeriod = null)
        .associateBy { motion -> motion.entryId }

    return motions.map { motion ->
        reorderedByEntryId[motion.entryId] ?: motion
    }
}

private fun sanitizePlanEditor(
    trainingPlans: List<TrainingPlanState>,
    availableMotions: List<AvailableMotionState>,
    planEditor: PlanEditorState?
): PlanEditorState? {
    val editor = planEditor ?: return null
    val cyclePeriod = editor.cyclePeriod?.takeIf { value -> value > 0 }
    val availableMotionsById = availableMotions.associateBy { motion -> motion.id }
    val persistedPlan = editor.planId?.let { planId ->
        trainingPlan(trainingPlans, planId)
    }

    if (editor.planId != null && persistedPlan == null) {
        return null
    }

    val sanitizedMotions = normalizeEditorMotions(
        motions = editor.motions.map { motion ->
            val availableMotion = availableMotionsById[motion.motionId]

            if (availableMotion == null) {
                motion
            } else {
                motion.copy(title = availableMotion.title)
            }
        },
        cyclePeriod = cyclePeriod
    )

    return editor.copy(
        cyclePeriod = cyclePeriod,
        currentIndex = if (cyclePeriod == null) {
            1
        } else {
            editor.currentIndex.coerceIn(1, cyclePeriod)
        },
        selectedDayIndex = if (cyclePeriod == null) {
            null
        } else {
            editor.selectedDayIndex?.coerceIn(1, cyclePeriod)
        },
        motions = sanitizedMotions
    )
}

private fun persistedMotionEntryIds(
    savedMotions: List<PlanMotionState>,
    refreshedMotions: List<PlanMotionState>
): Map<Int, Int> {
    if (savedMotions.isEmpty() || refreshedMotions.isEmpty()) {
        return emptyMap()
    }

    val savedMotionsByOrder = normalizeEditorMotions(savedMotions, cyclePeriod = null)
    val refreshedMotionsByOrder = normalizeEditorMotions(refreshedMotions, cyclePeriod = null)
    val pairCount = minOf(savedMotionsByOrder.size, refreshedMotionsByOrder.size)

    if (pairCount == 0) {
        return emptyMap()
    }

    val persistedIds = mutableMapOf<Int, Int>()

    for (index in 0 until pairCount) {
        persistedIds[savedMotionsByOrder[index].entryId] = refreshedMotionsByOrder[index].entryId
    }

    return persistedIds
}

private fun resolvePersistedPlanRoute(
    requestedRoute: PlanRoute,
    persistedMotionEntryIds: Map<Int, Int>
): PlanRoute {
    if (requestedRoute !is PlanRoute.Motion) {
        return requestedRoute
    }

    val persistedMotionEntryId = persistedMotionEntryIds[requestedRoute.motionEntryId]
        ?: return PlanRoute.Editor

    return PlanRoute.Motion(motionEntryId = persistedMotionEntryId)
}

private fun MotionDeleteTarget.toPersistedTarget(
    persistedMotionEntryIds: Map<Int, Int>
): MotionDeleteTarget? {
    val persistedMotionEntryId = persistedMotionEntryIds[motionEntryId] ?: return null

    return copy(motionEntryId = persistedMotionEntryId)
}
