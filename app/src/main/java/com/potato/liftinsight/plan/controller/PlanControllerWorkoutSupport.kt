package com.potato.liftinsight.plan.controller

import com.potato.liftinsight.plan.model.PlanState
import com.potato.liftinsight.plan.model.WorkoutSetPerformanceInput
import com.potato.liftinsight.plan.model.completedWorkoutSetCount
import com.potato.liftinsight.plan.model.createWorkoutProgressState
import com.potato.liftinsight.plan.model.normalizedPlanCurrentIndex
import com.potato.liftinsight.plan.model.todaysPlanMotions
import com.potato.liftinsight.plan.model.toRpe
import com.potato.liftinsight.plan.model.trainingPlan
import com.potato.liftinsight.plan.model.workoutElapsedTimeMs
import com.potato.liftinsight.plan.model.workoutSetTargetsForDay
import com.potato.liftinsight.plan.route.PlanRoute
import com.potato.liftinsight.training.data.CreateMetaHistoryRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun PlanControllerEnvironment.startWorkout(state: PlanState): PlanState {
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

internal fun PlanControllerEnvironment.openCamera(state: PlanState): PlanState {
    val workoutProgress = state.workoutProgress ?: run {
        logWarn("Ignoring camera open because no workout progress is available")
        return state
    }

    if (!state.workoutSession.isWorkoutGoing || state.workoutSession.isPaused) {
        logWarn("Ignoring camera open because the workout session is not active")
        return state
    }

    val currentPlan = trainingPlan(state.trainingPlans, state.currentPlanId)
    val todayMotions = currentPlan?.let { todaysPlanMotions(it) }.orEmpty()
    val todayTargets = workoutSetTargetsForDay(todayMotions)
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

internal fun PlanControllerEnvironment.closeCamera(state: PlanState): PlanState {
    logDebug("Closing camera, returning to plan overview")
    return state.copy(planRoute = PlanRoute.Overview)
}

internal fun PlanControllerEnvironment.closeCameraWithVideo(
    state: PlanState,
    videoName: String?
): PlanState {
    logDebug("Closing camera with video, returning to plan overview to mark performance")

    val normalizedVideoName = videoName
        ?.trim()
        ?.takeIf { recordedVideoName -> recordedVideoName.isNotEmpty() }

    if (normalizedVideoName != null) {
        videoProcessor.submitForProcessing(normalizedVideoName)
    }

    return state.copy(
        planRoute = PlanRoute.Overview,
        cameraVideoName = normalizedVideoName
    )
}

internal fun PlanControllerEnvironment.clearCameraVideo(state: PlanState): PlanState {
    return state.copy(cameraVideoName = null)
}

internal suspend fun PlanControllerEnvironment.startNextWorkoutSet(state: PlanState): PlanState {
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

    return reloadState(state)
}

internal suspend fun PlanControllerEnvironment.skipWorkoutSet(state: PlanState): PlanState {
    val workoutProgress = state.workoutProgress ?: run {
        logWarn("Ignoring workout set skip because no workout progress is available")
        return state
    }

    if (!state.workoutSession.isWorkoutGoing || state.workoutSession.isPaused) {
        logWarn("Ignoring workout set skip because the workout session is not active")
        return state
    }

    val elapsedTimeMs = workoutElapsedTimeMs(state.workoutSession, nowProvider())
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
            trainingPlanStore.stopWorkout()
        }
    }

    logDebug(
        "Skipped workout set: completedSets=${completedWorkoutSetCount(updatedProgress)}, totalSets=${updatedProgress.totalSetCount}, finished=${updatedProgress.isFinished}"
    )

    return reloadState(state)
}

internal suspend fun PlanControllerEnvironment.finishCurrentWorkoutSet(
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
    val todayMotions = currentPlan?.let { todaysPlanMotions(it) }.orEmpty()
    val todayTargets = workoutSetTargetsForDay(todayMotions)
    val motionId = workoutProgress.activeSetIndex
        ?.let { index -> todayTargets.getOrNull(index)?.motionId }
        ?: -1

    val metaHistoryRequest = CreateMetaHistoryRequest(
        date = localDateTimeString(now),
        rep = performance.repsDone,
        rpe = performance.feeling.toRpe(),
        weight = performance.weightDone,
        motionId = motionId,
        videoName = performance.videoName
    )

    withContext(Dispatchers.IO) {
        trainingPlanStore.saveWorkoutProgress(updatedProgress)

        if (motionId > 0) {
            trainingPlanStore.insertMetaHistory(metaHistoryRequest)
        }

        if (updatedProgress.isFinished) {
            trainingPlanStore.stopWorkout()
        }
    }

    logDebug(
        "Finished workout set: repsDone=${performance.repsDone}, weightDone=${performance.weightDone}, feeling=${performance.feeling}, motionId=$motionId, breakDurationSeconds=${performance.breakDurationSeconds}, finished=${updatedProgress.isFinished}"
    )

    return reloadState(state)
}

internal suspend fun PlanControllerEnvironment.toggleWorkoutPause(state: PlanState): PlanState {
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

internal fun PlanControllerEnvironment.requestWorkoutStop(state: PlanState): PlanState {
    if (!state.workoutSession.isWorkoutGoing) {
        logWarn("Ignoring workout stop request because no workout session is active")
        return state
    }

    logDebug("Requesting workout stop confirmation")

    return state.copy(workoutStopPendingConfirmation = true)
}

internal fun PlanControllerEnvironment.dismissWorkoutStop(state: PlanState): PlanState {
    logDebug("Dismissing workout stop confirmation")
    return state.copy(workoutStopPendingConfirmation = false)
}

internal suspend fun PlanControllerEnvironment.confirmWorkoutStop(state: PlanState): PlanState {
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
