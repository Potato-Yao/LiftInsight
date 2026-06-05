package com.potato.liftinsight.plan.model

data class TrainingPlanState(
    val id: Int,
    val name: String,
    val lastAppliedAt: Long,
    val cyclePeriod: Int = 7,
    val currentIndex: Int = 1,
    val motions: List<PlanMotionState> = emptyList()
)

data class PlanMotionState(
    val entryId: Int,
    val motionId: Int,
    val title: String,
    val dayIndex: Int,
    val sets: Int,
    val repsPerSet: Int,
    val intensity: Double = 0.0,
    val weight: Double = 0.0,
    val orderIndex: Int = 1
)

data class AvailableMotionState(
    val id: Int,
    val title: String
)

data class TrainingPlanSelectionResult(
    val plans: List<TrainingPlanState>,
    val currentPlanId: Int
)

data class WorkoutSessionState(
    val isWorkoutGoing: Boolean = false,
    val isPaused: Boolean = false,
    val startedAt: Long = 0L,
    val lastResumedAt: Long = 0L,
    val elapsedBeforePauseMs: Long = 0L
)

data class CreateTrainingPlanResult(
    val plans: List<TrainingPlanState>,
    val createdPlanId: Int
)

data class DeleteTrainingPlanResult(
    val plans: List<TrainingPlanState>,
    val currentPlanId: Int
)

data class AddMotionToPlanResult(
    val plans: List<TrainingPlanState>,
    val motionEntryId: Int
)

fun sortPlansByLastApplied(plans: List<TrainingPlanState>): List<TrainingPlanState> {
    return plans.sortedWith(
        compareByDescending<TrainingPlanState> { it.lastAppliedAt }
            .thenBy { it.name.lowercase() }
    )
}

fun currentPlanName(
    plans: List<TrainingPlanState>,
    currentPlanId: Int
): String? {
    return plans.firstOrNull { it.id == currentPlanId }?.name
}

fun trainingPlan(
    plans: List<TrainingPlanState>,
    planId: Int
): TrainingPlanState? {
    return plans.firstOrNull { it.id == planId }
}

fun planMotion(
    plan: TrainingPlanState,
    motionEntryId: Int
): PlanMotionState? {
    return plan.motions.firstOrNull { it.entryId == motionEntryId }
}

fun normalizedPlanCurrentIndex(plan: TrainingPlanState): Int {
    return normalizePlanDayIndex(
        dayIndex = plan.currentIndex,
        cyclePeriod = plan.cyclePeriod
    )
}

fun normalizePlanDayIndex(
    dayIndex: Int,
    cyclePeriod: Int
): Int {
    if (cyclePeriod <= 0) {
        return 1
    }

    if (dayIndex <= 0) {
        return 1
    }

    if (dayIndex > cyclePeriod) {
        return cyclePeriod
    }

    return dayIndex
}

fun motionsForPlanDay(
    plan: TrainingPlanState,
    dayIndex: Int
): List<PlanMotionState> {
    val normalizedDayIndex = normalizePlanDayIndex(
        dayIndex = dayIndex,
        cyclePeriod = plan.cyclePeriod
    )

    return plan.motions.filter { motion -> motion.dayIndex == normalizedDayIndex }
}

fun todaysPlanMotions(plan: TrainingPlanState): List<PlanMotionState> {
    return motionsForPlanDay(
        plan = plan,
        dayIndex = normalizedPlanCurrentIndex(plan)
    )
}

fun workoutSetTargetsWithInsertions(
    plan: TrainingPlanState,
    temporaryMotion: PlanMotionState,
    nextSetIndex: Int
): List<WorkoutSetTargetState> {
    val planMotions = todaysPlanMotions(plan)
    val originalTargets = workoutSetTargetsForDay(planMotions)

    if (originalTargets.isEmpty()) {
        return workoutSetTargetsForDay(listOf(temporaryMotion))
    }

    // Insert the temporary motion's target at nextSetIndex
    val tempTarget = WorkoutSetTargetState(
        orderIndex = nextSetIndex,
        motionEntryId = temporaryMotion.entryId,
        motionId = temporaryMotion.motionId,
        motionTitle = temporaryMotion.title,
        setIndex = 1,
        setsInMotion = temporaryMotion.sets.coerceAtLeast(1),
        reps = temporaryMotion.repsPerSet.coerceAtLeast(1),
        weight = temporaryMotion.weight.coerceAtLeast(0.0),
        intensity = temporaryMotion.intensity.coerceAtLeast(0.0)
    )

    val insertAt = nextSetIndex.coerceIn(0, originalTargets.size)
    val merged = originalTargets.toMutableList()
    merged.add(insertAt, tempTarget)

    // Re-index orderIndex
    return merged.mapIndexed { index, target -> target.copy(orderIndex = index) }
}

fun workoutElapsedTimeMs(
    workoutSession: WorkoutSessionState,
    now: Long
): Long {
    if (!workoutSession.isWorkoutGoing) {
        return 0L
    }

    if (workoutSession.isPaused || workoutSession.lastResumedAt <= 0L) {
        return workoutSession.elapsedBeforePauseMs.coerceAtLeast(0L)
    }

    val activeSegmentMs = (now - workoutSession.lastResumedAt).coerceAtLeast(0L)

    return (workoutSession.elapsedBeforePauseMs + activeSegmentMs).coerceAtLeast(0L)
}

fun selectTrainingPlan(
    plans: List<TrainingPlanState>,
    currentPlanId: Int,
    planId: Int,
    selectedAt: Long
): TrainingPlanSelectionResult {
    if (plans.none { it.id == planId }) {
        return TrainingPlanSelectionResult(
            plans = plans,
            currentPlanId = currentPlanId
        )
    }

    val updatedPlans = plans.map { plan ->
        if (plan.id == planId) {
            plan.copy(lastAppliedAt = selectedAt)
        } else {
            plan
        }
    }

    return TrainingPlanSelectionResult(
        plans = updatedPlans,
        currentPlanId = planId
    )
}

fun createTrainingPlan(
    plans: List<TrainingPlanState>,
    name: String,
    cyclePeriod: Int,
    currentIndex: Int = 1,
    createdAt: Long
): CreateTrainingPlanResult {
    val normalizedName = name.trim()

    if (normalizedName.isEmpty()) {
        return CreateTrainingPlanResult(
            plans = plans,
            createdPlanId = -1
        )
    }

    if (cyclePeriod <= 0) {
        return CreateTrainingPlanResult(
            plans = plans,
            createdPlanId = -1
        )
    }

    val createdPlanId = (plans.maxOfOrNull { it.id } ?: 0) + 1
    val createdPlan = TrainingPlanState(
        id = createdPlanId,
        name = normalizedName,
        lastAppliedAt = createdAt,
        cyclePeriod = cyclePeriod,
        currentIndex = normalizePlanDayIndex(
            dayIndex = currentIndex,
            cyclePeriod = cyclePeriod
        )
    )

    return CreateTrainingPlanResult(
        plans = plans + createdPlan,
        createdPlanId = createdPlanId
    )
}

fun updateTrainingPlanName(
    plans: List<TrainingPlanState>,
    planId: Int,
    newName: String
): List<TrainingPlanState> {
    val trimmedName = newName.trim()

    if (trimmedName.isEmpty()) {
        return plans
    }

    return plans.map { plan ->
        if (plan.id == planId) {
            plan.copy(name = trimmedName)
        } else {
            plan
        }
    }
}

fun deleteTrainingPlan(
    plans: List<TrainingPlanState>,
    currentPlanId: Int,
    planId: Int
): DeleteTrainingPlanResult {
    if (plans.none { it.id == planId }) {
        return DeleteTrainingPlanResult(
            plans = plans,
            currentPlanId = currentPlanId
        )
    }

    val updatedPlans = plans.filterNot { it.id == planId }
    val updatedCurrentPlanId = if (currentPlanId != planId) {
        currentPlanId
    } else {
        sortPlansByLastApplied(updatedPlans).firstOrNull()?.id ?: -1
    }

    return DeleteTrainingPlanResult(
        plans = updatedPlans,
        currentPlanId = updatedCurrentPlanId
    )
}

fun addMotionToPlan(
    plans: List<TrainingPlanState>,
    planId: Int,
    dayIndex: Int,
    motion: AvailableMotionState
): AddMotionToPlanResult {
    val targetPlan = trainingPlan(plans, planId)

    if (targetPlan == null) {
        return AddMotionToPlanResult(
            plans = plans,
            motionEntryId = -1
        )
    }

    val normalizedDayIndex = normalizePlanDayIndex(
        dayIndex = dayIndex,
        cyclePeriod = targetPlan.cyclePeriod
    )
    val nextOrderIndex = targetPlan.motions
        .filter { existingMotion -> existingMotion.dayIndex == normalizedDayIndex }
        .maxOfOrNull { existingMotion -> existingMotion.orderIndex }
        ?.plus(1)
        ?: 1
    val createdMotionEntryId = (targetPlan.motions.maxOfOrNull { it.entryId } ?: 0) + 1
    val createdMotion = PlanMotionState(
        entryId = createdMotionEntryId,
        motionId = motion.id,
        title = motion.title,
        dayIndex = normalizedDayIndex,
        sets = 1,
        repsPerSet = 1,
        intensity = 0.0,
        weight = 0.0,
        orderIndex = nextOrderIndex
    )

    return AddMotionToPlanResult(
        plans = plans.map { plan ->
            if (plan.id == planId) {
                plan.copy(motions = reindexPlanMotions(plan.motions + createdMotion))
            } else {
                plan
            }
        },
        motionEntryId = createdMotionEntryId
    )
}

fun movePlanMotion(
    plans: List<TrainingPlanState>,
    planId: Int,
    motionEntryId: Int,
    direction: Int
): List<TrainingPlanState> {
    return plans.map { plan ->
        if (plan.id != planId) {
            return@map plan
        }

        val movingMotion = plan.motions.firstOrNull { it.entryId == motionEntryId }

        if (movingMotion == null) {
            return@map plan
        }

        val motionsForDay = plan.motions.filter { motion -> motion.dayIndex == movingMotion.dayIndex }
        val currentIndex = motionsForDay.indexOfFirst { motion -> motion.entryId == motionEntryId }

        if (currentIndex == -1) {
            return@map plan
        }

        val targetIndex = currentIndex + direction

        if (targetIndex !in motionsForDay.indices) {
            return@map plan
        }

        val reorderedDayMotions = motionsForDay.toMutableList()
        val currentMotion = reorderedDayMotions.removeAt(currentIndex)
        reorderedDayMotions.add(targetIndex, currentMotion)
        val reorderedDayMotionsByEntryId = reindexPlanMotions(reorderedDayMotions)
            .associateBy { motion -> motion.entryId }

        plan.copy(
            motions = plan.motions.map { motion ->
                reorderedDayMotionsByEntryId[motion.entryId] ?: motion
            }
        )
    }
}

fun updateMotionSets(
    plans: List<TrainingPlanState>,
    planId: Int,
    motionEntryId: Int,
    sets: Int
): List<TrainingPlanState> {
    return plans.map { plan ->
        if (plan.id != planId) {
            return@map plan
        }

        plan.copy(
            motions = plan.motions.map { motion ->
                if (motion.entryId == motionEntryId) {
                    motion.copy(sets = sets.coerceAtLeast(1))
                } else {
                    motion
                }
            }
        )
    }
}

fun updateMotionRepsPerSet(
    plans: List<TrainingPlanState>,
    planId: Int,
    motionEntryId: Int,
    repsPerSet: Int
): List<TrainingPlanState> {
    return plans.map { plan ->
        if (plan.id != planId) {
            return@map plan
        }

        plan.copy(
            motions = plan.motions.map { motion ->
                if (motion.entryId == motionEntryId) {
                    motion.copy(repsPerSet = repsPerSet.coerceAtLeast(1))
                } else {
                    motion
                }
            }
        )
    }
}

fun deletePlanMotion(
    plans: List<TrainingPlanState>,
    planId: Int,
    motionEntryId: Int
): List<TrainingPlanState> {
    return plans.map { plan ->
        if (plan.id == planId) {
            plan.copy(
                motions = reindexPlanMotions(
                    plan.motions.filterNot { it.entryId == motionEntryId }
                )
            )
        } else {
            plan
        }
    }
}

fun updatePlanCurrentIndex(
    plans: List<TrainingPlanState>,
    planId: Int,
    dayIndex: Int
): List<TrainingPlanState> {
    return plans.map { plan ->
        if (plan.id != planId) {
            return@map plan
        }

        plan.copy(
            currentIndex = normalizePlanDayIndex(
                dayIndex = dayIndex,
                cyclePeriod = plan.cyclePeriod
            )
        )
    }
}

fun advancePlanDayIndex(
    currentIndex: Int,
    cyclePeriod: Int,
    dayOffset: Long
): Int {
    val normalizedCyclePeriod = cyclePeriod.coerceAtLeast(1)
    val normalizedCurrentIndex = normalizePlanDayIndex(
        dayIndex = currentIndex,
        cyclePeriod = normalizedCyclePeriod
    )

    if (dayOffset <= 0L) {
        return normalizedCurrentIndex
    }

    val zeroBasedIndex = normalizedCurrentIndex - 1L
    val normalizedOffset = dayOffset % normalizedCyclePeriod.toLong()

    return ((zeroBasedIndex + normalizedOffset) % normalizedCyclePeriod.toLong()).toInt() + 1
}

private fun reindexPlanMotions(motions: List<PlanMotionState>): List<PlanMotionState> {
    val nextIndexByDay = mutableMapOf<Int, Int>()

    return motions.map { motion ->
        val normalizedDayIndex = motion.dayIndex.coerceAtLeast(1)
        val nextOrderIndex = (nextIndexByDay[normalizedDayIndex] ?: 0) + 1
        nextIndexByDay[normalizedDayIndex] = nextOrderIndex

        motion.copy(
            dayIndex = normalizedDayIndex,
            orderIndex = nextOrderIndex
        )
    }
}

