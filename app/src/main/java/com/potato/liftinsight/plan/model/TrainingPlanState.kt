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
    val sets: Int,
    val repsPerSet: Int,
    val intensity: Double = 0.0,
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

    return plan.motions.filter { motion -> motion.orderIndex == normalizedDayIndex }
}

fun todaysPlanMotions(plan: TrainingPlanState): List<PlanMotionState> {
    return motionsForPlanDay(
        plan = plan,
        dayIndex = normalizedPlanCurrentIndex(plan)
    )
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
    createdAt: Long
): CreateTrainingPlanResult {
    val createdPlanId = (plans.maxOfOrNull { it.id } ?: 0) + 1
    val createdPlan = TrainingPlanState(
        id = createdPlanId,
        name = name.trim(),
        lastAppliedAt = createdAt,
        currentIndex = 1
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
    motion: AvailableMotionState
): AddMotionToPlanResult {
    val targetPlan = trainingPlan(plans, planId)

    if (targetPlan == null) {
        return AddMotionToPlanResult(
            plans = plans,
            motionEntryId = -1
        )
    }

    val createdMotionEntryId = (targetPlan.motions.maxOfOrNull { it.entryId } ?: 0) + 1
    val createdMotion = PlanMotionState(
        entryId = createdMotionEntryId,
        motionId = motion.id,
        title = motion.title,
        sets = 1,
        repsPerSet = 1,
        intensity = 0.0,
        orderIndex = targetPlan.motions.size + 1
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

        val currentIndex = plan.motions.indexOfFirst { it.entryId == motionEntryId }

        if (currentIndex == -1) {
            return@map plan
        }

        val targetIndex = currentIndex + direction

        if (targetIndex !in plan.motions.indices) {
            return@map plan
        }

        val updatedMotions = plan.motions.toMutableList()
        val currentMotion = updatedMotions.removeAt(currentIndex)
        updatedMotions.add(targetIndex, currentMotion)

        plan.copy(motions = reindexPlanMotions(updatedMotions))
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

private fun reindexPlanMotions(motions: List<PlanMotionState>): List<PlanMotionState> {
    return motions.mapIndexed { index, motion ->
        motion.copy(orderIndex = index + 1)
    }
}

