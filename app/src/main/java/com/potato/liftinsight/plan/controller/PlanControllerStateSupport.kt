package com.potato.liftinsight.plan.controller

import com.potato.liftinsight.plan.data.TrainingPlanSeedCatalog
import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.PlanState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.plan.model.WorkoutProgressState
import com.potato.liftinsight.plan.model.WorkoutSessionState
import com.potato.liftinsight.plan.model.WorkoutSetTargetState
import com.potato.liftinsight.plan.model.normalizedPlanCurrentIndex
import com.potato.liftinsight.plan.model.sanitizeWorkoutProgressState
import com.potato.liftinsight.plan.model.sortPlansByLastApplied
import com.potato.liftinsight.plan.model.todaysPlanMotions
import com.potato.liftinsight.plan.model.trainingPlan
import com.potato.liftinsight.plan.model.workoutSetTargetsForDay
import com.potato.liftinsight.plan.route.MotionDeleteTarget
import com.potato.liftinsight.plan.route.PlanEditorState
import com.potato.liftinsight.plan.route.PlanRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun PlanControllerEnvironment.loadState(
    emptyState: PlanState,
    seedCatalog: TrainingPlanSeedCatalog
): PlanState {
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

        reloadStateInternal(
            state = emptyState,
            requestedRoute = PlanRoute.Overview,
            planIdPendingDelete = null,
            motionPendingDelete = null,
            planEditor = null,
            workoutStopPendingConfirmation = false,
            workoutInsertedMotions = emptyList(),
            mergedTodayTargets = emptyList()
        ).also { loadedState ->
            logDebug(
                "Loaded plan state: motions=${loadedState.availableMotions.size}, plans=${loadedState.trainingPlans.size}, currentPlanId=${loadedState.currentPlanId}"
            )
        }
    }
}

internal suspend fun PlanControllerEnvironment.selectPlan(
    state: PlanState,
    planId: Int
): PlanState {
    logDebug("Selecting training plan: planId=$planId")

    val updateSucceeded = withContext(Dispatchers.IO) {
        val selectedPlan = trainingPlanStore.getTrainingPlan(planId) ?: run {
            logWarn("Cannot select training plan because it was not found: planId=$planId")
            return@withContext false
        }

        val selectedAt = nowProvider()
        val planSaved = trainingPlanStore.updateTrainingPlan(
            selectedPlan.copy(lastAppliedAt = selectedAt)
        )
        if (!planSaved) {
            logWarn("Failed to update selected training plan timestamp: planId=$planId")
            return@withContext false
        }

        trainingPlanStore.setCurrentPlan(planId, selectedAt = selectedAt)
    }

    if (!updateSucceeded) {
        return state
    }

    logDebug("Selected training plan: planId=$planId")

    return reloadState(state)
}

internal suspend fun PlanControllerEnvironment.updatePlanCurrentDay(
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
        val updatedPlan = com.potato.liftinsight.plan.model.updatePlanCurrentIndex(
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

internal suspend fun PlanControllerEnvironment.refreshState(state: PlanState): PlanState {
    logDebug("Refreshing plan state")
    return reloadState(state)
}

internal suspend fun PlanControllerEnvironment.reloadState(
    state: PlanState,
    requestedRoute: PlanRoute = state.planRoute,
    planIdPendingDelete: Int? = state.planIdPendingDelete,
    motionPendingDelete: MotionDeleteTarget? = state.motionPendingDelete,
    planEditor: PlanEditorState? = state.planEditor,
    workoutStopPendingConfirmation: Boolean = state.workoutStopPendingConfirmation,
    workoutInsertedMotions: List<PlanMotionState> = state.workoutInsertedMotions,
    mergedTodayTargets: List<WorkoutSetTargetState> = state.mergedTodayTargets
): PlanState {
    return withContext(Dispatchers.IO) {
        reloadStateInternal(
            state = state,
            requestedRoute = requestedRoute,
            planIdPendingDelete = planIdPendingDelete,
            motionPendingDelete = motionPendingDelete,
            planEditor = planEditor,
            workoutStopPendingConfirmation = workoutStopPendingConfirmation,
            workoutInsertedMotions = workoutInsertedMotions,
            mergedTodayTargets = mergedTodayTargets
        )
    }
}

private fun PlanControllerEnvironment.reloadStateInternal(
    state: PlanState,
    requestedRoute: PlanRoute,
    planIdPendingDelete: Int?,
    motionPendingDelete: MotionDeleteTarget?,
    planEditor: PlanEditorState?,
    workoutStopPendingConfirmation: Boolean,
    workoutInsertedMotions: List<PlanMotionState>,
    mergedTodayTargets: List<WorkoutSetTargetState>
): PlanState {
    val availableMotions = trainingPlanStore.getAvailableMotions()
    val now = nowProvider()
    var trainingPlans = trainingPlanStore.getTrainingPlans()
    var currentPlanId = resolveCurrentPlanId(trainingPlans, now)

    trainingPlanStore.advanceCurrentPlanDayIfNeeded(now)

    trainingPlans = trainingPlanStore.getTrainingPlans()
    currentPlanId = resolveCurrentPlanId(trainingPlans, now)
    val workoutSession = trainingPlanStore.getWorkoutSession()
    val workoutProgress = trainingPlanStore.getWorkoutProgress()

    return buildLoadedState(
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
        workoutStopPendingConfirmation = workoutStopPendingConfirmation,
        workoutInsertedMotions = workoutInsertedMotions,
        mergedTodayTargets = mergedTodayTargets
    )
}

private fun PlanControllerEnvironment.buildLoadedState(
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
    workoutStopPendingConfirmation: Boolean,
    workoutInsertedMotions: List<PlanMotionState>,
    mergedTodayTargets: List<WorkoutSetTargetState>
): PlanState {
    val sanitizedPlanEditor = sanitizePlanEditor(trainingPlans, availableMotions, planEditor)
    val currentPlan = trainingPlan(trainingPlans, currentPlanId)
    val todayMotions = currentPlan?.let { plan -> todaysPlanMotions(plan) }.orEmpty()
    val todayTargets = mergedTodayTargets.ifEmpty {
        workoutSetTargetsForDay(todayMotions)
    }
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

    // Preserve inserted motions only when workout is active and not finished
    val preservedInsertedMotions = if (workoutSession.isWorkoutGoing && workoutProgress?.isFinished != true) {
        workoutInsertedMotions
    } else {
        emptyList()
    }

    val preservedMergedTargets = if (workoutSession.isWorkoutGoing && workoutProgress?.isFinished != true) {
        mergedTodayTargets
    } else {
        emptyList()
    }

    return state.copy(
        availableMotions = availableMotions,
        trainingPlans = trainingPlans,
        currentPlanId = currentPlanId,
        workoutProgress = sanitizedWorkoutProgress,
        workoutSession = workoutSession,
        planEditor = sanitizedPlanEditor,
        planRoute = sanitizePlanRoute(requestedRoute, sanitizedPlanEditor),
        planIdPendingDelete = planIdPendingDelete?.takeIf { planId ->
            trainingPlan(trainingPlans, planId) != null
        },
        motionPendingDelete = motionPendingDelete?.takeIf { pendingTarget ->
            sanitizedPlanEditor?.motions?.any { motion ->
                motion.entryId == pendingTarget.motionEntryId &&
                    sanitizedPlanEditor.planId == pendingTarget.planId
            } == true
        },
        cameraVideoName = null,
        workoutStopPendingConfirmation = workoutStopPendingConfirmation && workoutSession.isWorkoutGoing,
        workoutInsertedMotions = preservedInsertedMotions,
        mergedTodayTargets = preservedMergedTargets
    )
}

private fun sanitizePlanRoute(
    requestedRoute: PlanRoute,
    planEditor: PlanEditorState?
): PlanRoute {
    return when (requestedRoute) {
        PlanRoute.Overview -> PlanRoute.Overview
        PlanRoute.List -> PlanRoute.List
        PlanRoute.Editor -> if (planEditor == null) PlanRoute.List else PlanRoute.Editor
        is PlanRoute.Camera -> requestedRoute
        PlanRoute.MotionPicker -> {
            if (planEditor == null || planEditor.selectedDayIndex == null || planEditor.cyclePeriod == null) {
                PlanRoute.List
            } else {
                PlanRoute.MotionPicker
            }
        }
        PlanRoute.WorkoutMotionPicker -> PlanRoute.Overview
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

internal fun PlanControllerEnvironment.resolveCurrentPlanId(
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

internal fun PlanControllerEnvironment.deleteTrainingPlanAndUpdateSelection(planId: Int): Boolean {
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

internal fun localDateTimeString(timestamp: Long): String {
    val formatter = java.text.SimpleDateFormat(
        "yyyy-MM-dd-HH-mm-ss",
        java.util.Locale.getDefault()
    )

    return formatter.format(java.util.Date(timestamp))
}
