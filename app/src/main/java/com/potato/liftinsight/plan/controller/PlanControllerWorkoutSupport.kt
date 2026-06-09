package com.potato.liftinsight.plan.controller

import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.PlanState
import com.potato.liftinsight.plan.model.WorkoutSetPerformanceInput
import com.potato.liftinsight.plan.model.WorkoutSessionFeeling
import com.potato.liftinsight.plan.model.completedWorkoutSetCount
import com.potato.liftinsight.plan.model.createWorkoutProgressState
import com.potato.liftinsight.plan.model.normalizedPlanCurrentIndex
import com.potato.liftinsight.plan.model.todaysPlanMotions
import com.potato.liftinsight.plan.model.toRpe
import com.potato.liftinsight.plan.model.trainingPlan
import com.potato.liftinsight.plan.model.workoutElapsedTimeMs
import com.potato.liftinsight.plan.model.workoutSetTargetsWithInsertions
import com.potato.liftinsight.plan.model.workoutSetTargetsForDay
import com.potato.liftinsight.plan.route.PlanRoute
import com.potato.liftinsight.training.data.CreateHistoryRequest
import com.potato.liftinsight.training.data.CreateMetaHistoryRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal suspend fun PlanController.startWorkoutImpl(state: PlanState): PlanState {
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

    val activeHistoryId = withContext(Dispatchers.IO) {
        trainingPlanStore.startWorkout(now)

        val historyId = trainingPlanStore.createHistoryRecord(
            CreateHistoryRequest(
                planId = currentPlan.id,
                startTime = now,
                endTime = now,
                dayIndex = dayIndex
            )
        )

        trainingPlanStore.saveWorkoutProgress(
            createWorkoutProgressState(
                planId = currentPlan.id,
                dayIndex = dayIndex,
                totalSetCount = workoutTargets.size,
                activeHistoryId = historyId
            )
        )

        historyId
    }

    logDebug("Created history record for workout: historyId=$activeHistoryId")

    return reloadStateImpl(state)
}

internal fun PlanController.openCameraImpl(state: PlanState): PlanState {
    val workoutProgress = state.workoutProgress ?: run {
        logWarn("Ignoring camera open because no workout progress is available")
        return state
    }

    if (!state.workoutSession.isWorkoutGoing || state.workoutSession.isPaused) {
        logWarn("Ignoring camera open because the workout session is not active")
        return state
    }

    val currentPlan = trainingPlan(state.trainingPlans, state.currentPlanId)
    val todayMotions = currentPlan?.let { plan -> todaysPlanMotions(plan) }.orEmpty()
    val todayTargets = if (state.mergedTodayTargets.isNotEmpty()) {
        state.mergedTodayTargets
    } else {
        workoutSetTargetsForDay(todayMotions)
    }
    val activeTarget = workoutProgress.activeSetIndex
        ?.let { index -> todayTargets.getOrNull(index) }

    if (activeTarget == null) {
        logWarn("Ignoring camera open because no active set was found")
        return state
    }

    logDebug("Opening camera for motion: motionId=${activeTarget.motionId}, setIndex=${activeTarget.setIndex}")

    return state.copy(
        planRoute = PlanRoute.Camera(
            motionId = activeTarget.motionId,
            motionTitle = activeTarget.motionTitle,
            setIndex = activeTarget.setIndex,
            setsInMotion = activeTarget.setsInMotion,
            expectedReps = activeTarget.reps,
            expectedWeight = activeTarget.weight,
            expectedIntensity = activeTarget.intensity
        )
    )
}

internal fun PlanController.closeCameraImpl(state: PlanState): PlanState {
    logDebug("Closing camera, returning to plan overview")
    return state.copy(planRoute = PlanRoute.Overview)
}

internal fun PlanController.closeCameraWithVideoImpl(
    state: PlanState,
    videoName: String?
): PlanState {
    logDebug("Closing camera with video, returning to plan overview to mark performance")

    val normalizedVideoName = videoName
        ?.trim()
        ?.takeIf { recordedVideoName -> recordedVideoName.isNotEmpty() }

    return state.copy(
        planRoute = PlanRoute.Overview,
        cameraVideoName = normalizedVideoName
    )
}

internal fun PlanController.clearCameraVideoImpl(state: PlanState): PlanState {
    return state.copy(cameraVideoName = null)
}

internal suspend fun PlanController.startNextWorkoutSetImpl(state: PlanState): PlanState {
    val workoutSession = state.workoutSession

    if (!workoutSession.isWorkoutGoing || workoutSession.isPaused) {
        logWarn("Ignoring start set because the workout session is not active")
        return state
    }

    val workoutProgress = state.workoutProgress ?: run {
        logWarn("Ignoring start set because no workout progress is available")
        return state
    }
    val updatedProgress = com.potato.liftinsight.plan.model.startWorkoutSet(workoutProgress)

    if (updatedProgress == workoutProgress) {
        logWarn("Ignoring start set because the workout progress was not ready for the next set")
        return state
    }

    withContext(Dispatchers.IO) {
        trainingPlanStore.saveWorkoutProgress(updatedProgress)
    }

    logDebug("Started workout set: setIndex=${updatedProgress.activeSetIndex ?: -1}")

    return reloadStateImpl(state)
}

internal suspend fun PlanController.skipWorkoutSetImpl(state: PlanState): PlanState {
    val workoutProgress = state.workoutProgress ?: run {
        logWarn("Ignoring workout set skip because no workout progress is available")
        return state
    }

    if (!state.workoutSession.isWorkoutGoing || state.workoutSession.isPaused) {
        logWarn("Ignoring workout set skip because the workout session is not active")
        return state
    }

    val now = nowProvider()
    val elapsedTimeMs = workoutElapsedTimeMs(state.workoutSession, now)
    val updatedProgress = com.potato.liftinsight.plan.model.skipWorkoutSet(
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
            val activeHistoryId = workoutProgress.activeHistoryId
            if (activeHistoryId != null) {
                trainingPlanStore.updateHistoryEndTime(activeHistoryId, now)
            }
            trainingPlanStore.stopWorkout()
        }
    }

    logDebug(
        "Skipped workout set: completedSets=${completedWorkoutSetCount(updatedProgress)}, totalSets=${updatedProgress.totalSetCount}, finished=${updatedProgress.isFinished}"
    )

    return reloadStateImpl(state)
}

internal suspend fun PlanController.finishCurrentWorkoutSetImpl(
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
    val updatedProgress = com.potato.liftinsight.plan.model.finishWorkoutSet(
        progress = workoutProgress,
        completedElapsedTimeMs = elapsedTimeMs,
        finishedAt = now,
        breakDurationSeconds = performance.breakDurationSeconds
    )

    if (updatedProgress == workoutProgress) {
        logWarn("Ignoring workout set completion because no active set was found")
        return state
    }

    val currentPlan = trainingPlan(state.trainingPlans, state.currentPlanId)
    val todayMotions = currentPlan?.let { plan -> todaysPlanMotions(plan) }.orEmpty()
    val todayTargets = if (state.mergedTodayTargets.isNotEmpty()) {
        state.mergedTodayTargets
    } else {
        workoutSetTargetsForDay(todayMotions)
    }
    val motionId = workoutProgress.activeSetIndex
        ?.let { index -> todayTargets.getOrNull(index)?.motionId }
        ?: -1

    val metaHistoryRequest = CreateMetaHistoryRequest(
        date = localDateTimeString(now),
        rep = performance.repsDone,
        rpe = performance.feeling.toRpe(),
        weight = performance.weightDone,
        motionId = motionId,
        videoName = performance.videoName,
        historyId = workoutProgress.activeHistoryId
    )

    withContext(Dispatchers.IO) {
        trainingPlanStore.saveWorkoutProgress(updatedProgress)

        if (motionId > 0) {
            trainingPlanStore.insertMetaHistory(metaHistoryRequest)
        }

        if (updatedProgress.isFinished) {
            val activeHistoryId = workoutProgress.activeHistoryId
            if (activeHistoryId != null) {
                trainingPlanStore.updateHistoryEndTime(activeHistoryId, now)
            }
            trainingPlanStore.stopWorkout()
        }
    }

    logDebug(
        "Finished workout set: repsDone=${performance.repsDone}, weightDone=${performance.weightDone}, feeling=${performance.feeling}, motionId=$motionId, breakDurationSeconds=${performance.breakDurationSeconds}, finished=${updatedProgress.isFinished}"
    )

    return reloadStateImpl(state)
}

internal suspend fun PlanController.toggleWorkoutPauseImpl(state: PlanState): PlanState {
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

    return reloadStateImpl(state)
}

internal fun PlanController.requestWorkoutStopImpl(state: PlanState): PlanState {
    if (!state.workoutSession.isWorkoutGoing) {
        logWarn("Ignoring workout stop request because no workout session is active")
        return state
    }

    logDebug("Requesting workout stop confirmation")

    return state.copy(workoutStopPendingConfirmation = true)
}

internal fun PlanController.dismissWorkoutStopImpl(state: PlanState): PlanState {
    logDebug("Dismissing workout stop confirmation")
    return state.copy(workoutStopPendingConfirmation = false)
}

internal suspend fun PlanController.confirmWorkoutStopImpl(state: PlanState): PlanState {
    if (!state.workoutStopPendingConfirmation) {
        logWarn("Ignoring workout stop confirmation because no workout stop is pending")
        return state
    }

    logDebug("Stopping workout session")

    val now = nowProvider()

    withContext(Dispatchers.IO) {
        val activeHistoryId = state.workoutProgress?.activeHistoryId
        if (activeHistoryId != null) {
            trainingPlanStore.updateHistoryEndTime(activeHistoryId, now)
        }
        trainingPlanStore.stopWorkout()
        trainingPlanStore.clearWorkoutProgress()
    }

    return reloadStateImpl(
        state = state,
        workoutStopPendingConfirmation = false,
        workoutInsertedMotions = emptyList(),
        mergedTodayTargets = emptyList()
    )
}

internal suspend fun PlanController.submitWorkoutFeelingImpl(
    state: PlanState,
    feeling: WorkoutSessionFeeling
): PlanState {
    val workoutProgress = state.workoutProgress ?: run {
        logWarn("Ignoring workout feeling submission because no workout progress is available")
        return state
    }

    if (!workoutProgress.isFinished) {
        logWarn("Ignoring workout feeling submission because workout is not finished")
        return state
    }

    if (workoutProgress.workoutIntensity != null) {
        logWarn("Ignoring workout feeling submission because intensity was already recorded")
        return state
    }

    val durationMinutes = ((workoutProgress.completedElapsedTimeMs / 60_000.0)).roundToInt().coerceAtLeast(1)
    val intensity = feeling.sRpe * durationMinutes
    val activeHistoryId = workoutProgress.activeHistoryId

    withContext(Dispatchers.IO) {
        if (activeHistoryId != null) {
            trainingPlanStore.updateHistoryIntensity(activeHistoryId, intensity)
        }
        trainingPlanStore.saveWorkoutProgress(workoutProgress.copy(workoutIntensity = intensity))
    }

    logDebug("Recorded workout feeling: feeling=${feeling.name}, sRpe=${feeling.sRpe}, durationMin=$durationMinutes, intensity=$intensity")

    return reloadStateImpl(state)
}

internal fun PlanController.openWorkoutMotionPickerImpl(state: PlanState): PlanState {
    if (!state.workoutSession.isWorkoutGoing || state.workoutSession.isPaused) {
        logWarn("Ignoring workout motion picker open because the workout is not active")
        return state
    }

    if (state.workoutProgress?.activeSetIndex != null) {
        logWarn("Ignoring workout motion picker open because a set is currently active")
        return state
    }

    if (state.workoutProgress?.isFinished == true) {
        logWarn("Ignoring workout motion picker open because the workout is finished")
        return state
    }

    logDebug("Opening workout motion picker")

    return state.copy(planRoute = PlanRoute.WorkoutMotionPicker)
}

internal fun PlanController.closeWorkoutMotionPickerImpl(state: PlanState): PlanState {
    logDebug("Closing workout motion picker")
    return state.copy(planRoute = PlanRoute.Overview)
}

internal suspend fun PlanController.insertMotionIntoWorkoutImpl(
    state: PlanState,
    motion: AvailableMotionState
): PlanState {
    val workoutProgress = state.workoutProgress ?: run {
        logWarn("Ignoring workout motion insert because no workout progress is available")
        return state
    }

    if (!state.workoutSession.isWorkoutGoing) {
        logWarn("Ignoring workout motion insert because no workout is active")
        return state
    }

    val insertedMotion = PlanMotionState(
        entryId = -1000 - state.workoutInsertedMotions.size,
        motionId = motion.id,
        title = motion.title,
        dayIndex = workoutProgress.dayIndex,
        sets = 1,
        repsPerSet = 1,
        intensity = 0.0,
        weight = 0.0,
        orderIndex = 0
    )

    val updatedInsertedMotions = state.workoutInsertedMotions + insertedMotion
    val currentPlan = trainingPlan(state.trainingPlans, state.currentPlanId)

    if (currentPlan == null) {
        logWarn("Ignoring workout motion insert because no current plan found")
        return state
    }

    // Get existing merged targets or fall back to plan targets
    val existingTargets = if (state.mergedTodayTargets.isNotEmpty()) {
        state.mergedTodayTargets
    } else {
        val planMotions = todaysPlanMotions(currentPlan)
        workoutSetTargetsForDay(planMotions)
    }

    // Merge at target level to preserve correct set counts
    val mergedTargets = workoutSetTargetsWithInsertions(
        existingTargets = existingTargets,
        temporaryMotion = insertedMotion,
        nextSetIndex = workoutProgress.nextSetIndex
    )
    val newTotalSetCount = mergedTargets.size

    // Update totalSetCount in progress to account for the inserted set
    val updatedProgress = workoutProgress.copy(
        totalSetCount = newTotalSetCount
    )

    logDebug(
        "Inserted motion into workout: motionId=${motion.id}, motionTitle=${motion.title}, " +
            "newTotalSetCount=$newTotalSetCount, insertedMotionCount=${updatedInsertedMotions.size}"
    )

    withContext(Dispatchers.IO) {
        trainingPlanStore.saveWorkoutProgress(updatedProgress)
    }

    return reloadStateImpl(
        state = state.copy(
            planRoute = PlanRoute.Overview,
            mergedTodayTargets = mergedTargets
        ),
        workoutInsertedMotions = updatedInsertedMotions,
        mergedTodayTargets = mergedTargets
    )
}
