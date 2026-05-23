package com.potato.liftinsight.plan.model

import kotlin.math.roundToInt

data class WorkoutProgressState(
    val planId: Int,
    val dayIndex: Int,
    val nextSetIndex: Int = 0,
    val activeSetIndex: Int? = null,
    val totalSetCount: Int,
    val breakEndsAt: Long = 0L,
    val isFinished: Boolean = false,
    val completedElapsedTimeMs: Long = 0L
)

data class WorkoutSetTargetState(
    val orderIndex: Int,
    val motionEntryId: Int,
    val motionId: Int,
    val motionTitle: String,
    val setIndex: Int,
    val setsInMotion: Int,
    val reps: Int,
    val weight: Double,
    val intensity: Double
)

enum class WorkoutSetFeeling {
    TooEasy,
    Simple,
    HardButControlled,
    AlmostLostControl
}

data class WorkoutSetPerformanceInput(
    val repsDone: Int,
    val weightDone: Double,
    val feeling: WorkoutSetFeeling,
    val breakDurationSeconds: Int
)

fun workoutSetTargetsForDay(todayMotions: List<PlanMotionState>): List<WorkoutSetTargetState> {
    if (todayMotions.isEmpty()) {
        return emptyList()
    }

    val orderedMotions = todayMotions.sortedWith(
        compareBy<PlanMotionState> { motion -> motion.orderIndex }
            .thenBy { motion -> motion.entryId }
    )
    val targets = mutableListOf<WorkoutSetTargetState>()

    orderedMotions.forEach { motion ->
        for (setIndex in 1..motion.sets.coerceAtLeast(1)) {
            targets += WorkoutSetTargetState(
                orderIndex = targets.size,
                motionEntryId = motion.entryId,
                motionId = motion.motionId,
                motionTitle = motion.title,
                setIndex = setIndex,
                setsInMotion = motion.sets.coerceAtLeast(1),
                reps = motion.repsPerSet.coerceAtLeast(1),
                weight = motion.weight.coerceAtLeast(0.0),
                intensity = motion.intensity.coerceAtLeast(0.0)
            )
        }
    }

    return targets
}

fun createWorkoutProgressState(
    planId: Int,
    dayIndex: Int,
    totalSetCount: Int
): WorkoutProgressState {
    return WorkoutProgressState(
        planId = planId,
        dayIndex = dayIndex,
        totalSetCount = totalSetCount.coerceAtLeast(0)
    )
}

fun sanitizeWorkoutProgressState(
    progress: WorkoutProgressState?,
    planId: Int,
    dayIndex: Int,
    totalSetCount: Int
): WorkoutProgressState? {
    val currentProgress = progress ?: return null
    val normalizedTotalSetCount = totalSetCount.coerceAtLeast(0)

    if (currentProgress.planId != planId || currentProgress.dayIndex != dayIndex) {
        return null
    }

    if (normalizedTotalSetCount <= 0) {
        return null
    }

    if (currentProgress.totalSetCount != normalizedTotalSetCount) {
        return null
    }

    val clampedNextSetIndex = currentProgress.nextSetIndex.coerceIn(0, normalizedTotalSetCount)
    val clampedActiveSetIndex = currentProgress.activeSetIndex?.coerceIn(0, normalizedTotalSetCount - 1)
    val finished = currentProgress.isFinished || clampedNextSetIndex >= normalizedTotalSetCount

    return currentProgress.copy(
        nextSetIndex = if (finished) {
            normalizedTotalSetCount
        } else {
            clampedNextSetIndex
        },
        activeSetIndex = if (finished) {
            null
        } else if (clampedActiveSetIndex != null && clampedActiveSetIndex < clampedNextSetIndex) {
            clampedNextSetIndex
        } else {
            clampedActiveSetIndex
        },
        totalSetCount = normalizedTotalSetCount,
        breakEndsAt = currentProgress.breakEndsAt.coerceAtLeast(0L),
        isFinished = finished,
        completedElapsedTimeMs = currentProgress.completedElapsedTimeMs.coerceAtLeast(0L)
    )
}

fun startWorkoutSet(progress: WorkoutProgressState): WorkoutProgressState {
    if (progress.isFinished) {
        return progress
    }

    if (progress.activeSetIndex != null) {
        return progress
    }

    if (progress.nextSetIndex !in 0 until progress.totalSetCount) {
        return progress
    }

    return progress.copy(
        activeSetIndex = progress.nextSetIndex,
        breakEndsAt = 0L
    )
}

fun skipWorkoutSet(
    progress: WorkoutProgressState,
    completedElapsedTimeMs: Long
): WorkoutProgressState {
    if (progress.isFinished) {
        return progress
    }

    if (progress.activeSetIndex != null) {
        return progress
    }

    val nextSetIndex = (progress.nextSetIndex + 1).coerceAtMost(progress.totalSetCount)
    val finished = nextSetIndex >= progress.totalSetCount

    return progress.copy(
        nextSetIndex = nextSetIndex,
        activeSetIndex = null,
        breakEndsAt = 0L,
        isFinished = finished,
        completedElapsedTimeMs = if (finished) {
            completedElapsedTimeMs.coerceAtLeast(0L)
        } else {
            0L
        }
    )
}

fun finishWorkoutSet(
    progress: WorkoutProgressState,
    completedElapsedTimeMs: Long,
    finishedAt: Long,
    breakDurationSeconds: Int
): WorkoutProgressState {
    val activeSetIndex = progress.activeSetIndex ?: return progress

    if (progress.isFinished) {
        return progress
    }

    val nextSetIndex = (activeSetIndex + 1).coerceAtMost(progress.totalSetCount)
    val finished = nextSetIndex >= progress.totalSetCount
    val breakEndsAt = if (finished) {
        0L
    } else {
        finishedAt.coerceAtLeast(0L) + breakDurationSeconds.coerceAtLeast(0) * 1_000L
    }

    return progress.copy(
        nextSetIndex = nextSetIndex,
        activeSetIndex = null,
        breakEndsAt = breakEndsAt,
        isFinished = finished,
        completedElapsedTimeMs = if (finished) {
            completedElapsedTimeMs.coerceAtLeast(0L)
        } else {
            0L
        }
    )
}

fun completedWorkoutSetCount(progress: WorkoutProgressState?): Int {
    val currentProgress = progress ?: return 0

    if (currentProgress.isFinished) {
        return currentProgress.totalSetCount
    }

    return currentProgress.nextSetIndex.coerceIn(0, currentProgress.totalSetCount)
}

fun workoutCompletionPercentage(progress: WorkoutProgressState?): Int {
    val currentProgress = progress ?: return 0

    if (currentProgress.totalSetCount <= 0) {
        return 0
    }

    return ((completedWorkoutSetCount(currentProgress).toDouble() / currentProgress.totalSetCount.toDouble()) * 100.0)
        .roundToInt()
        .coerceIn(0, 100)
}

fun workoutRemainingBreakMs(
    progress: WorkoutProgressState?,
    now: Long
): Long {
    val currentProgress = progress ?: return 0L

    if (currentProgress.isFinished || currentProgress.activeSetIndex != null) {
        return 0L
    }

    return (currentProgress.breakEndsAt - now).coerceAtLeast(0L)
}

