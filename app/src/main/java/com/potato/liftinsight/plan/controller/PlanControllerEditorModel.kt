package com.potato.liftinsight.plan.controller

import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.plan.model.trainingPlan
import com.potato.liftinsight.plan.route.MotionDeleteTarget
import com.potato.liftinsight.plan.route.PlanEditorState
import com.potato.liftinsight.plan.route.PlanRoute

internal fun TrainingPlanState.toEditorState(): PlanEditorState {
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

internal fun PlanEditorState.toTrainingPlanState(createdAt: Long): TrainingPlanState {
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

internal fun normalizeEditorMotions(
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

internal fun reorderEditorMotions(
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
    val reorderedByEntryId = reorderedDayMotions
        .mapIndexed { index, motion -> motion.copy(orderIndex = index + 1) }
        .associateBy { motion -> motion.entryId }

    return normalizeEditorMotions(
        motions = motions.map { motion ->
            reorderedByEntryId[motion.entryId] ?: motion
        },
        cyclePeriod = null
    )
}

internal fun sanitizePlanEditor(
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

internal fun persistedMotionEntryIds(
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

internal fun resolvePersistedPlanRoute(
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

internal fun MotionDeleteTarget.toPersistedTarget(
    persistedMotionEntryIds: Map<Int, Int>
): MotionDeleteTarget? {
    val persistedMotionEntryId = persistedMotionEntryIds[motionEntryId] ?: return null

    return copy(motionEntryId = persistedMotionEntryId)
}
