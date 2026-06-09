package com.potato.liftinsight.plan.controller

import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.PlanState
import com.potato.liftinsight.plan.model.trainingPlan
import com.potato.liftinsight.plan.route.MotionDeleteTarget
import com.potato.liftinsight.plan.route.PlanEditorState
import com.potato.liftinsight.plan.route.PlanRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun PlanController.createPlanImpl(state: PlanState): PlanState {
    logDebug("Opening new plan editor")

    return state.copy(
        planRoute = PlanRoute.Editor,
        planIdPendingDelete = null,
        motionPendingDelete = null,
        planEditor = PlanEditorState()
    )
}

internal fun PlanController.showPlanListImpl(state: PlanState): PlanState {
    logDebug("Showing training plan list")
    return state.copy(planRoute = PlanRoute.List)
}

internal fun PlanController.showPlanOverviewImpl(state: PlanState): PlanState {
    logDebug("Showing training plan overview")
    return state.copy(planRoute = PlanRoute.Overview)
}

internal fun PlanController.showPlanDetailImpl(
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

internal fun PlanController.showMotionDetailImpl(
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

internal suspend fun PlanController.handlePlanBackImpl(state: PlanState): PlanState {
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
        PlanRoute.WorkoutMotionPicker -> state.copy(planRoute = PlanRoute.Overview)
        is PlanRoute.Motion -> state.copy(
            planRoute = PlanRoute.Editor,
            motionPendingDelete = null
        )
        is PlanRoute.Camera -> state.copy(planRoute = PlanRoute.Overview)
    }
}

internal suspend fun PlanController.updatePlanEditorTitleImpl(
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

    val updatedState = persistEditorPlanImpl(state, updatedEditor)

    if (updatedState == null) {
        logWarn("Failed to persist updated plan title: planId=${editor.planId}")
        return state
    }

    return updatedState
}

internal suspend fun PlanController.updatePlanEditorCyclePeriodImpl(
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

    val updatedState = persistEditorPlanImpl(state, updatedEditor)

    if (updatedState == null) {
        logWarn("Failed to persist updated cycle period: planId=${editor.planId}")
        return state
    }

    return updatedState
}

internal fun PlanController.selectPlanEditorDayImpl(
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

    return state.copy(planEditor = editor.copy(selectedDayIndex = normalizedDayIndex))
}

internal suspend fun PlanController.movePlanMotionImpl(
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

    return persistEditorPlanImpl(state, updatedEditor) ?: run {
        logWarn("Failed to persist reordered plan motion: planId=${editor.planId}, motionEntryId=$motionEntryId")
        state
    }
}

internal suspend fun PlanController.updateMotionSetsImpl(
    state: PlanState,
    motionEntryId: Int,
    sets: Int
): PlanState {
    return updateEditorMotionField(
        state = state,
        motionEntryId = motionEntryId,
        noEditorMessage = "Ignoring motion sets update because no plan editor is open: motionEntryId=$motionEntryId",
        noChangeMessage = "Motion sets update produced no change: motionEntryId=$motionEntryId, requestedSets=$sets",
        updateMessage = "Updating motion sets: motionEntryId=$motionEntryId, requestedSets=$sets",
        persistFailureMessage = "Failed to persist motion sets update: motionEntryId=$motionEntryId"
    ) { motion ->
        motion.copy(sets = sets.coerceAtLeast(1))
    }
}

internal suspend fun PlanController.updateMotionRepsPerSetImpl(
    state: PlanState,
    motionEntryId: Int,
    repsPerSet: Int
): PlanState {
    return updateEditorMotionField(
        state = state,
        motionEntryId = motionEntryId,
        noEditorMessage = "Ignoring motion reps update because no plan editor is open: motionEntryId=$motionEntryId",
        noChangeMessage = "Motion reps update produced no change: motionEntryId=$motionEntryId, requestedRepsPerSet=$repsPerSet",
        updateMessage = "Updating motion reps per set: motionEntryId=$motionEntryId, requestedRepsPerSet=$repsPerSet",
        persistFailureMessage = "Failed to persist motion reps update: motionEntryId=$motionEntryId"
    ) { motion ->
        motion.copy(repsPerSet = repsPerSet.coerceAtLeast(1))
    }
}

internal suspend fun PlanController.updateMotionWeightImpl(
    state: PlanState,
    motionEntryId: Int,
    weight: Double
): PlanState {
    return updateEditorMotionField(
        state = state,
        motionEntryId = motionEntryId,
        noEditorMessage = "Ignoring motion weight update because no plan editor is open: motionEntryId=$motionEntryId",
        noChangeMessage = "Motion weight update produced no change: motionEntryId=$motionEntryId, requestedWeight=$weight",
        updateMessage = "Updating motion weight: motionEntryId=$motionEntryId, requestedWeight=$weight",
        persistFailureMessage = "Failed to persist motion weight update: motionEntryId=$motionEntryId"
    ) { motion ->
        motion.copy(weight = weight.coerceAtLeast(0.0))
    }
}

private suspend fun PlanController.updateEditorMotionField(
    state: PlanState,
    motionEntryId: Int,
    noEditorMessage: String,
    noChangeMessage: String,
    updateMessage: String,
    persistFailureMessage: String,
    updateMotion: (PlanMotionState) -> PlanMotionState
): PlanState {
    val editor = state.planEditor ?: run {
        logWarn(noEditorMessage)
        return state
    }
    val updatedEditor = editor.copy(
        motions = editor.motions.map { motion ->
            if (motion.entryId == motionEntryId) updateMotion(motion) else motion
        }
    )

    if (updatedEditor == editor) {
        logWarn(noChangeMessage)
        return state
    }

    logDebug("$updateMessage, planId=${editor.planId ?: -1}")

    if (updatedEditor.isNewPlan) {
        return state.copy(planEditor = updatedEditor)
    }

    return persistEditorPlanImpl(state, updatedEditor) ?: run {
        logWarn("$persistFailureMessage, planId=${editor.planId}")
        state
    }
}

internal fun PlanController.requestPlanDeletionImpl(
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

internal fun PlanController.cancelPlanDeletionImpl(state: PlanState): PlanState {
    logDebug("Cancelling plan deletion request")
    return state.copy(planIdPendingDelete = null)
}

internal suspend fun PlanController.confirmPlanDeletionImpl(state: PlanState): PlanState {
    val pendingPlanId = state.planIdPendingDelete ?: run {
        logWarn("Ignoring plan deletion confirmation because no plan is pending deletion")
        return state
    }

    logDebug("Confirming plan deletion: planId=$pendingPlanId")

    val deleteSucceeded = withContext(Dispatchers.IO) {
        deleteTrainingPlanAndUpdateSelectionImpl(pendingPlanId)
    }

    if (!deleteSucceeded) {
        logWarn("Failed to delete training plan: planId=$pendingPlanId")
        return state
    }

    return reloadStateImpl(
        state = state,
        requestedRoute = PlanRoute.List,
        planIdPendingDelete = null,
        motionPendingDelete = null,
        planEditor = state.planEditor?.takeUnless { editor -> editor.planId == pendingPlanId }
    )
}

internal fun PlanController.requestMotionDeletionImpl(
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

internal fun PlanController.cancelMotionDeletionImpl(state: PlanState): PlanState {
    logDebug("Cancelling motion deletion request")
    return state.copy(motionPendingDelete = null)
}

internal suspend fun PlanController.confirmMotionDeletionImpl(state: PlanState): PlanState {
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

    val updatedState = persistEditorPlanImpl(
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

internal fun PlanController.openAddMotionPickerImpl(state: PlanState): PlanState {
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

internal fun PlanController.closeAddMotionPickerImpl(state: PlanState): PlanState {
    logDebug("Closing add motion picker")
    return state.copy(planRoute = PlanRoute.Editor)
}

internal suspend fun PlanController.addMotionToPlanImpl(
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

    return persistEditorPlanImpl(
        state = state,
        editor = updatedEditor,
        requestedRoute = PlanRoute.Motion(motionEntryId = addedMotionEntryId)
    ) ?: run {
        logWarn("Failed to persist added motion: planId=${editor.planId}, motionId=${motion.id}")
        state
    }
}

internal suspend fun PlanController.submitPlanEditorImpl(state: PlanState): PlanState {
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
        return persistEditorPlanImpl(state, editor) ?: run {
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

    return reloadStateImpl(
        state = state,
        requestedRoute = PlanRoute.Editor,
        planEditor = storedPlan.toEditorState()
    )
}

internal suspend fun PlanController.persistEditorPlanImpl(
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

    return reloadStateImpl(
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
