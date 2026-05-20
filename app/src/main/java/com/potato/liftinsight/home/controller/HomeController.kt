package com.potato.liftinsight.home.controller

import com.potato.liftinsight.body.model.BodyMetricState
import com.potato.liftinsight.body.model.defaultBodyMetrics
import com.potato.liftinsight.body.model.updateBodyMetric as applyBodyMetricUpdate
import com.potato.liftinsight.plan.data.TrainingPlanSeedCatalog
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.plan.model.sortPlansByLastApplied
import com.potato.liftinsight.plan.model.trainingPlan
import com.potato.liftinsight.plan.model.updatePlanCurrentIndex as applyPlanCurrentIndexUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class MainTab {
    Home,
    Body,
    Motion,
    Plan,
    Settings;

    companion object {
        fun fromIndex(index: Int): MainTab {
            return entries.getOrNull(index) ?: Home
        }
    }
}

data class HomeState(
    val selectedTab: MainTab = MainTab.Home,
    val bodyMetrics: List<BodyMetricState>,
    val availableMotions: List<AvailableMotionState>,
    val trainingPlans: List<TrainingPlanState>,
    val currentPlanId: Int,
    val planDestination: PlanDestination = PlanDestination.Overview,
    val planIdPendingDelete: Int? = null,
    val motionPendingDelete: MotionDeleteTarget? = null,
    val planEditor: PlanEditorState? = null
) {
    val selectedTabIndex: Int
        get() = selectedTab.ordinal
}

data class PlanEditorState(
    val planId: Int? = null,
    val title: String = "",
    val cyclePeriod: Int? = null,
    val currentIndex: Int = 1,
    val selectedDayIndex: Int? = null,
    val motions: List<PlanMotionState> = emptyList(),
    val nextTemporaryMotionEntryId: Int = -1,
    val titleError: Boolean = false,
    val cyclePeriodError: Boolean = false
) {
    val isNewPlan: Boolean
        get() = planId == null
}

sealed interface PlanDestination {
    data object Overview : PlanDestination

    data object List : PlanDestination

    data object Editor : PlanDestination

    data object MotionPicker : PlanDestination

    data class Motion(val motionEntryId: Int) : PlanDestination
}

data class MotionDeleteTarget(
    val planId: Int,
    val motionEntryId: Int
)

fun planDestinationDepth(destination: PlanDestination): Int {
    return when (destination) {
        PlanDestination.Overview -> 0
        PlanDestination.List -> 1
        PlanDestination.Editor -> 2
        PlanDestination.MotionPicker -> 3
        is PlanDestination.Motion -> 3
    }
}

class HomeController(
    private val trainingPlanStore: TrainingPlanStore,
    private val shouldSeedDebugPlans: Boolean = false,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    fun emptyState(): HomeState {
        return HomeState(
            bodyMetrics = defaultBodyMetrics(),
            availableMotions = emptyList(),
            trainingPlans = emptyList(),
            currentPlanId = -1
        )
    }

    suspend fun loadState(seedCatalog: TrainingPlanSeedCatalog): HomeState {
        return withContext(Dispatchers.IO) {
            trainingPlanStore.ensureAvailableMotions(seedCatalog.availableMotions)

            if (shouldSeedDebugPlans) {
                trainingPlanStore.seedPlansIfEmpty(
                    plans = seedCatalog.debugPlans,
                    currentPlanId = seedCatalog.debugCurrentPlanId
                )
            }

            val availableMotions = trainingPlanStore.getAvailableMotions()
            val trainingPlans = trainingPlanStore.getTrainingPlans()
            val currentPlanId = resolveCurrentPlanId(trainingPlans)

            buildLoadedState(
                state = emptyState(),
                availableMotions = availableMotions,
                trainingPlans = trainingPlans,
                currentPlanId = currentPlanId,
                requestedDestination = PlanDestination.Overview,
                planIdPendingDelete = null,
                motionPendingDelete = null,
                planEditor = null
            )
        }
    }

    fun selectTab(state: HomeState, tabIndex: Int): HomeState {
        return state.copy(selectedTab = MainTab.fromIndex(tabIndex))
    }

    fun updateBodyMetric(
        state: HomeState,
        metricId: Int,
        newValue: String
    ): HomeState {
        return state.copy(
            bodyMetrics = applyBodyMetricUpdate(
                metrics = state.bodyMetrics,
                metricId = metricId,
                newValue = newValue
            )
        )
    }

    fun createPlan(state: HomeState): HomeState {
        return state.copy(
            planDestination = PlanDestination.Editor,
            planIdPendingDelete = null,
            motionPendingDelete = null,
            planEditor = PlanEditorState()
        )
    }

    suspend fun selectPlan(
        state: HomeState,
        planId: Int
    ): HomeState {
        val updateSucceeded = withContext(Dispatchers.IO) {
            val selectedPlan = trainingPlanStore.getTrainingPlan(planId) ?: return@withContext false

            val planSaved = trainingPlanStore.updateTrainingPlan(
                selectedPlan.copy(lastAppliedAt = nowProvider())
            )
            if (!planSaved) {
                return@withContext false
            }

            trainingPlanStore.setCurrentPlan(planId)
        }

        if (!updateSucceeded) {
            return state
        }

        return reloadState(state)
    }

    fun showPlanList(state: HomeState): HomeState {
        return state.copy(planDestination = PlanDestination.List)
    }

    fun showPlanOverview(state: HomeState): HomeState {
        return state.copy(planDestination = PlanDestination.Overview)
    }

    fun showPlanDetail(
        state: HomeState,
        planId: Int
    ): HomeState {
        val plan = trainingPlan(state.trainingPlans, planId)

        if (plan == null) {
            return state.copy(planDestination = PlanDestination.List)
        }

        return state.copy(
            planDestination = PlanDestination.Editor,
            planIdPendingDelete = null,
            motionPendingDelete = null,
            planEditor = plan.toEditorState()
        )
    }

    fun showMotionDetail(
        state: HomeState,
        motionEntryId: Int
    ): HomeState {
        val editor = state.planEditor ?: return state.copy(planDestination = PlanDestination.List)
        val motion = editor.motions.firstOrNull { planMotion -> planMotion.entryId == motionEntryId }

        if (motion == null) {
            return state.copy(planDestination = PlanDestination.List)
        }

        return state.copy(planDestination = PlanDestination.Motion(motionEntryId = motionEntryId))
    }

    suspend fun handlePlanBack(state: HomeState): HomeState {
        return when (state.planDestination) {
            PlanDestination.Overview -> state

            PlanDestination.List -> state.copy(
                planDestination = PlanDestination.Overview,
                planIdPendingDelete = null,
                motionPendingDelete = null,
                planEditor = null
            )

            PlanDestination.Editor -> state.copy(
                planDestination = PlanDestination.List,
                planIdPendingDelete = null,
                motionPendingDelete = null,
                planEditor = null
            )

            PlanDestination.MotionPicker -> state.copy(
                planDestination = PlanDestination.Editor,
                motionPendingDelete = null
            )

            is PlanDestination.Motion -> state.copy(
                planDestination = PlanDestination.Editor,
                motionPendingDelete = null
            )
        }
    }

    suspend fun updatePlanEditorTitle(
        state: HomeState,
        newName: String
    ): HomeState {
        val editor = state.planEditor ?: return state
        val updatedEditor = editor.copy(
            title = newName,
            titleError = false
        )

        if (updatedEditor.isNewPlan) {
            return state.copy(planEditor = updatedEditor)
        }

        val updatedState = persistEditorPlan(state, updatedEditor)

        if (updatedState == null) {
            return state
        }

        return updatedState
    }

    suspend fun updatePlanEditorCyclePeriod(
        state: HomeState,
        cyclePeriod: Int?
    ): HomeState {
        val editor = state.planEditor ?: return state
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

        if (updatedEditor.isNewPlan) {
            return state.copy(planEditor = updatedEditor)
        }

        val updatedState = persistEditorPlan(state, updatedEditor)

        if (updatedState == null) {
            return state
        }

        return updatedState
    }

    fun selectPlanEditorDay(
        state: HomeState,
        dayIndex: Int
    ): HomeState {
        val editor = state.planEditor ?: return state
        val cyclePeriod = editor.cyclePeriod ?: return state
        val normalizedDayIndex = dayIndex.coerceIn(1, cyclePeriod)

        return state.copy(
            planEditor = editor.copy(selectedDayIndex = normalizedDayIndex)
        )
    }

    suspend fun updatePlanCurrentDay(
        state: HomeState,
        planId: Int,
        dayIndex: Int
    ): HomeState {
        val updateSucceeded = withContext(Dispatchers.IO) {
            val storedPlan = trainingPlanStore.getTrainingPlan(planId) ?: return@withContext false
            val updatedPlan = applyPlanCurrentIndexUpdate(
                plans = listOf(storedPlan),
                planId = planId,
                dayIndex = dayIndex
            ).first()

            if (updatedPlan == storedPlan) {
                return@withContext false
            }

            trainingPlanStore.updateTrainingPlan(updatedPlan)
        }

        if (!updateSucceeded) {
            return state
        }

        return reloadState(state)
    }

    suspend fun movePlanMotion(
        state: HomeState,
        motionEntryId: Int,
        direction: Int
    ): HomeState {
        val editor = state.planEditor ?: return state
        val updatedMotions = reorderEditorMotions(editor.motions, motionEntryId, direction)

        if (updatedMotions == editor.motions) {
            return state
        }

        val updatedEditor = editor.copy(motions = updatedMotions)

        if (updatedEditor.isNewPlan) {
            return state.copy(planEditor = updatedEditor)
        }

        return persistEditorPlan(state, updatedEditor) ?: state
    }

    suspend fun updateMotionSets(
        state: HomeState,
        motionEntryId: Int,
        sets: Int
    ): HomeState {
        val editor = state.planEditor ?: return state
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
            return state
        }

        if (updatedEditor.isNewPlan) {
            return state.copy(planEditor = updatedEditor)
        }

        return persistEditorPlan(state, updatedEditor) ?: state
    }

    suspend fun updateMotionRepsPerSet(
        state: HomeState,
        motionEntryId: Int,
        repsPerSet: Int
    ): HomeState {
        val editor = state.planEditor ?: return state
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
            return state
        }

        if (updatedEditor.isNewPlan) {
            return state.copy(planEditor = updatedEditor)
        }

        return persistEditorPlan(state, updatedEditor) ?: state
    }

    fun requestPlanDeletion(
        state: HomeState,
        planId: Int
    ): HomeState {
        if (trainingPlan(state.trainingPlans, planId) == null) {
            return state
        }

        return state.copy(planIdPendingDelete = planId)
    }

    fun cancelPlanDeletion(state: HomeState): HomeState {
        return state.copy(planIdPendingDelete = null)
    }

    suspend fun confirmPlanDeletion(state: HomeState): HomeState {
        val pendingPlanId = state.planIdPendingDelete ?: return state
        val deleteSucceeded = withContext(Dispatchers.IO) {
            deleteTrainingPlanAndUpdateSelection(pendingPlanId)
        }

        if (!deleteSucceeded) {
            return state
        }

        return reloadState(
            state = state,
            requestedDestination = PlanDestination.List,
            planIdPendingDelete = null,
            motionPendingDelete = null,
            planEditor = state.planEditor?.takeUnless { editor -> editor.planId == pendingPlanId }
        )
    }

    fun requestMotionDeletion(
        state: HomeState,
        motionEntryId: Int
    ): HomeState {
        val editor = state.planEditor ?: return state
        val planId = editor.planId ?: return state
        val motion = editor.motions.firstOrNull { planMotion -> planMotion.entryId == motionEntryId }

        if (motion == null) {
            return state
        }

        return state.copy(
            motionPendingDelete = MotionDeleteTarget(
                planId = planId,
                motionEntryId = motionEntryId
            )
        )
    }

    fun cancelMotionDeletion(state: HomeState): HomeState {
        return state.copy(motionPendingDelete = null)
    }

    suspend fun confirmMotionDeletion(state: HomeState): HomeState {
        val pendingTarget = state.motionPendingDelete ?: return state
        val editor = state.planEditor ?: return state
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
            requestedDestination = PlanDestination.Editor,
            motionPendingDelete = null
        )

        if (updatedState == null) {
            return state
        }

        return updatedState
    }

    fun openAddMotionPicker(state: HomeState): HomeState {
        val editor = state.planEditor ?: return state

        if (editor.cyclePeriod == null || editor.selectedDayIndex == null) {
            return state
        }

        return state.copy(planDestination = PlanDestination.MotionPicker)
    }

    fun closeAddMotionPicker(state: HomeState): HomeState {
        return state.copy(planDestination = PlanDestination.Editor)
    }

    suspend fun addMotionToPlan(
        state: HomeState,
        motion: AvailableMotionState
    ): HomeState {
        val editor = state.planEditor ?: return state
        val selectedDayIndex = editor.selectedDayIndex ?: return state
        val nextOrderIndex = editor.motions
            .filter { existingMotion -> existingMotion.dayIndex == selectedDayIndex }
            .maxOfOrNull { existingMotion -> existingMotion.orderIndex }
            ?.plus(1)
            ?: 1
        val updatedEditor = editor.copy(
            motions = normalizeEditorMotions(
                motions = editor.motions + PlanMotionState(
                    entryId = editor.nextTemporaryMotionEntryId,
                    motionId = motion.id,
                    title = motion.title,
                    dayIndex = selectedDayIndex,
                    sets = 1,
                    repsPerSet = 1,
                    intensity = 0.0,
                    orderIndex = nextOrderIndex
                ),
                cyclePeriod = editor.cyclePeriod
            ),
            nextTemporaryMotionEntryId = editor.nextTemporaryMotionEntryId - 1
        )

        if (updatedEditor.isNewPlan) {
            return state.copy(
                planDestination = PlanDestination.Editor,
                planEditor = updatedEditor
            )
        }

        return persistEditorPlan(
            state = state,
            editor = updatedEditor,
            requestedDestination = PlanDestination.Editor
        ) ?: state
    }

    suspend fun submitPlanEditor(state: HomeState): HomeState {
        val editor = state.planEditor ?: return state
        val title = editor.title.trim()
        val cyclePeriod = editor.cyclePeriod
        val hasTitleError = title.isEmpty()
        val hasCyclePeriodError = cyclePeriod == null || cyclePeriod <= 0

        if (hasTitleError || hasCyclePeriodError) {
            return state.copy(
                planEditor = editor.copy(
                    titleError = hasTitleError,
                    cyclePeriodError = hasCyclePeriodError
                )
            )
        }

        if (!editor.isNewPlan) {
            return persistEditorPlan(state, editor) ?: state
        }

        val createdPlanId = withContext(Dispatchers.IO) {
            trainingPlanStore.createTrainingPlan(
                plan = editor.toTrainingPlanState(createdAt = nowProvider())
            )
        }

        val storedPlan = withContext(Dispatchers.IO) {
            trainingPlanStore.getTrainingPlan(createdPlanId)
        } ?: return state

        return reloadState(
            state = state,
            requestedDestination = PlanDestination.Editor,
            planEditor = storedPlan.toEditorState()
        )
    }

    suspend fun refreshState(state: HomeState): HomeState {
        return reloadState(state)
    }

    private suspend fun reloadState(
        state: HomeState,
        requestedDestination: PlanDestination = state.planDestination,
        planIdPendingDelete: Int? = state.planIdPendingDelete,
        motionPendingDelete: MotionDeleteTarget? = state.motionPendingDelete,
        planEditor: PlanEditorState? = state.planEditor
    ): HomeState {
        return withContext(Dispatchers.IO) {
            val availableMotions = trainingPlanStore.getAvailableMotions()
            val trainingPlans = trainingPlanStore.getTrainingPlans()
            val currentPlanId = resolveCurrentPlanId(trainingPlans)

            buildLoadedState(
                state = state,
                availableMotions = availableMotions,
                trainingPlans = trainingPlans,
                currentPlanId = currentPlanId,
                requestedDestination = requestedDestination,
                planIdPendingDelete = planIdPendingDelete,
                motionPendingDelete = motionPendingDelete,
                planEditor = planEditor
            )
        }
    }

    private fun buildLoadedState(
        state: HomeState,
        availableMotions: List<AvailableMotionState>,
        trainingPlans: List<TrainingPlanState>,
        currentPlanId: Int,
        requestedDestination: PlanDestination,
        planIdPendingDelete: Int?,
        motionPendingDelete: MotionDeleteTarget?,
        planEditor: PlanEditorState?
    ): HomeState {
        val sanitizedPlanEditor = sanitizePlanEditor(trainingPlans, availableMotions, planEditor)

        return state.copy(
            availableMotions = availableMotions,
            trainingPlans = trainingPlans,
            currentPlanId = currentPlanId,
            planEditor = sanitizedPlanEditor,
            planDestination = sanitizePlanDestination(
                requestedDestination = requestedDestination,
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
            }
        )
    }

    private fun sanitizePlanDestination(
        requestedDestination: PlanDestination,
        planEditor: PlanEditorState?
    ): PlanDestination {
        return when (requestedDestination) {
            PlanDestination.Overview -> PlanDestination.Overview

            PlanDestination.List -> PlanDestination.List

            PlanDestination.Editor -> {
                if (planEditor == null) {
                    PlanDestination.List
                } else {
                    PlanDestination.Editor
                }
            }

            PlanDestination.MotionPicker -> {
                if (planEditor == null || planEditor.selectedDayIndex == null || planEditor.cyclePeriod == null) {
                    PlanDestination.List
                } else {
                    PlanDestination.MotionPicker
                }
            }

            is PlanDestination.Motion -> {
                if (planEditor == null) {
                    PlanDestination.List
                } else if (planEditor.motions.none { motion -> motion.entryId == requestedDestination.motionEntryId }) {
                    PlanDestination.Editor
                } else {
                    requestedDestination
                }
            }
        }
    }

    private suspend fun persistEditorPlan(
        state: HomeState,
        editor: PlanEditorState,
        requestedDestination: PlanDestination = state.planDestination,
        motionPendingDelete: MotionDeleteTarget? = state.motionPendingDelete
    ): HomeState? {
        val planId = editor.planId ?: return state.copy(planEditor = editor)
        val storedPlan = withContext(Dispatchers.IO) {
            trainingPlanStore.getTrainingPlan(planId)
        } ?: return null
        val cyclePeriod = editor.cyclePeriod ?: return state.copy(planEditor = editor)
        val normalizedEditor = editor.copy(
            title = editor.title.trim(),
            cyclePeriod = cyclePeriod,
            currentIndex = storedPlan.currentIndex.coerceIn(1, cyclePeriod),
            motions = normalizeEditorMotions(editor.motions, cyclePeriod)
        )

        if (normalizedEditor.title.isEmpty()) {
            return state.copy(planEditor = normalizedEditor.copy(titleError = true))
        }

        val updateSucceeded = withContext(Dispatchers.IO) {
            trainingPlanStore.updateTrainingPlan(
                normalizedEditor.toTrainingPlanState(createdAt = storedPlan.lastAppliedAt)
                    .copy(id = planId)
            )
        }

        if (!updateSucceeded) {
            return null
        }

        val refreshedPlan = withContext(Dispatchers.IO) {
            trainingPlanStore.getTrainingPlan(planId)
        } ?: return null

        return reloadState(
            state = state,
            requestedDestination = requestedDestination,
            motionPendingDelete = motionPendingDelete,
            planEditor = refreshedPlan.toEditorState().copy(
                selectedDayIndex = normalizedEditor.selectedDayIndex,
                titleError = false,
                cyclePeriodError = false
            )
        )
    }

    private fun resolveCurrentPlanId(trainingPlans: List<TrainingPlanState>): Int {
        val storedCurrentPlanId = trainingPlanStore.getCurrentPlanId()

        if (trainingPlans.any { plan -> plan.id == storedCurrentPlanId }) {
            return storedCurrentPlanId
        }

        val fallbackPlanId = sortPlansByLastApplied(trainingPlans).firstOrNull()?.id ?: -1

        if (fallbackPlanId == -1) {
            trainingPlanStore.clearCurrentPlan()
        } else {
            trainingPlanStore.setCurrentPlan(fallbackPlanId)
        }

        return fallbackPlanId
    }

    private fun deleteTrainingPlanAndUpdateSelection(planId: Int): Boolean {
        val wasCurrentPlan = trainingPlanStore.getCurrentPlanId() == planId
        val deleted = trainingPlanStore.deleteTrainingPlan(planId)

        if (!deleted) {
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
            trainingPlanStore.clearCurrentPlan()
        } else {
            trainingPlanStore.setCurrentPlan(fallbackPlanId)
        }

        return true
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



