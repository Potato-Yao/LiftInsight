package com.potato.liftinsight.plan

import com.potato.liftinsight.home.controller.HomeController
import com.potato.liftinsight.home.controller.HomeState
import com.potato.liftinsight.plan.model.AvailableMotionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class PlanRouteActions(
    val onCreatePlan: () -> Unit,
    val onSelectPlan: (Int) -> Unit,
    val onOpenPlanDetail: (Int) -> Unit,
    val onOpenPlanList: () -> Unit,
    val onBackInPlan: () -> Unit,
    val onRenamePlan: (String) -> Unit,
    val onUpdatePlanCyclePeriod: (Int?) -> Unit,
    val onSelectPlanEditorDay: (Int) -> Unit,
    val onSelectPlanDay: (Int, Int) -> Unit,
    val onMoveMotionUp: (Int) -> Unit,
    val onMoveMotionDown: (Int) -> Unit,
    val onOpenMotionDetail: (Int) -> Unit,
    val onDecreaseMotionSets: (Int, Int) -> Unit,
    val onIncreaseMotionSets: (Int, Int) -> Unit,
    val onDecreaseMotionReps: (Int, Int) -> Unit,
    val onIncreaseMotionReps: (Int, Int) -> Unit,
    val onUpdateMotionWeight: (Int, Double) -> Unit,
    val onRequestPlanDeletion: (Int) -> Unit,
    val onDismissPlanDeletion: () -> Unit,
    val onConfirmPlanDeletion: () -> Unit,
    val onRequestMotionDeletion: (Int) -> Unit,
    val onDismissMotionDeletion: () -> Unit,
    val onConfirmMotionDeletion: () -> Unit,
    val onOpenAddMotionPicker: () -> Unit,
    val onDismissAddMotionPicker: () -> Unit,
    val onAddMotionToPlan: (AvailableMotionState) -> Unit,
    val onSubmitPlan: () -> Unit
)

internal fun buildPlanRouteActions(
    controller: HomeController,
    currentState: () -> HomeState,
    updateState: (HomeState) -> Unit,
    coroutineScope: CoroutineScope
): PlanRouteActions {
    return PlanRouteActions(
        onCreatePlan = {
            updateState(controller.createPlan(currentState()))
        },
        onSelectPlan = { planId ->
            coroutineScope.launch {
                updateState(controller.selectPlan(currentState(), planId))
            }
        },
        onOpenPlanDetail = { planId ->
            updateState(controller.showPlanDetail(currentState(), planId))
        },
        onOpenPlanList = {
            updateState(controller.showPlanList(currentState()))
        },
        onBackInPlan = {
            coroutineScope.launch {
                updateState(controller.handlePlanBack(currentState()))
            }
        },
        onRenamePlan = { newName ->
            coroutineScope.launch {
                updateState(controller.updatePlanEditorTitle(currentState(), newName))
            }
        },
        onUpdatePlanCyclePeriod = { cyclePeriod ->
            coroutineScope.launch {
                updateState(controller.updatePlanEditorCyclePeriod(currentState(), cyclePeriod))
            }
        },
        onSelectPlanEditorDay = { dayIndex ->
            updateState(controller.selectPlanEditorDay(currentState(), dayIndex))
        },
        onSelectPlanDay = { planId, dayIndex ->
            coroutineScope.launch {
                updateState(
                    controller.updatePlanCurrentDay(
                        state = currentState(),
                        planId = planId,
                        dayIndex = dayIndex
                    )
                )
            }
        },
        onMoveMotionUp = { motionEntryId ->
            coroutineScope.launch {
                updateState(
                    controller.movePlanMotion(
                        state = currentState(),
                        motionEntryId = motionEntryId,
                        direction = -1
                    )
                )
            }
        },
        onMoveMotionDown = { motionEntryId ->
            coroutineScope.launch {
                updateState(
                    controller.movePlanMotion(
                        state = currentState(),
                        motionEntryId = motionEntryId,
                        direction = 1
                    )
                )
            }
        },
        onOpenMotionDetail = { motionEntryId ->
            updateState(controller.showMotionDetail(currentState(), motionEntryId))
        },
        onDecreaseMotionSets = { motionEntryId, sets ->
            coroutineScope.launch {
                updateState(
                    controller.updateMotionSets(
                        state = currentState(),
                        motionEntryId = motionEntryId,
                        sets = sets - 1
                    )
                )
            }
        },
        onIncreaseMotionSets = { motionEntryId, sets ->
            coroutineScope.launch {
                updateState(
                    controller.updateMotionSets(
                        state = currentState(),
                        motionEntryId = motionEntryId,
                        sets = sets + 1
                    )
                )
            }
        },
        onDecreaseMotionReps = { motionEntryId, repsPerSet ->
            coroutineScope.launch {
                updateState(
                    controller.updateMotionRepsPerSet(
                        state = currentState(),
                        motionEntryId = motionEntryId,
                        repsPerSet = repsPerSet - 1
                    )
                )
            }
        },
        onIncreaseMotionReps = { motionEntryId, repsPerSet ->
            coroutineScope.launch {
                updateState(
                    controller.updateMotionRepsPerSet(
                        state = currentState(),
                        motionEntryId = motionEntryId,
                        repsPerSet = repsPerSet + 1
                    )
                )
            }
        },
        onUpdateMotionWeight = { motionEntryId, weight ->
            coroutineScope.launch {
                updateState(
                    controller.updateMotionWeight(
                        state = currentState(),
                        motionEntryId = motionEntryId,
                        weight = weight
                    )
                )
            }
        },
        onRequestPlanDeletion = { planId ->
            updateState(controller.requestPlanDeletion(currentState(), planId))
        },
        onDismissPlanDeletion = {
            updateState(controller.cancelPlanDeletion(currentState()))
        },
        onConfirmPlanDeletion = {
            coroutineScope.launch {
                updateState(controller.confirmPlanDeletion(currentState()))
            }
        },
        onRequestMotionDeletion = { motionEntryId ->
            updateState(controller.requestMotionDeletion(currentState(), motionEntryId))
        },
        onDismissMotionDeletion = {
            updateState(controller.cancelMotionDeletion(currentState()))
        },
        onConfirmMotionDeletion = {
            coroutineScope.launch {
                updateState(controller.confirmMotionDeletion(currentState()))
            }
        },
        onOpenAddMotionPicker = {
            updateState(controller.openAddMotionPicker(currentState()))
        },
        onDismissAddMotionPicker = {
            updateState(controller.closeAddMotionPicker(currentState()))
        },
        onAddMotionToPlan = { motion ->
            coroutineScope.launch {
                updateState(controller.addMotionToPlan(currentState(), motion))
            }
        },
        onSubmitPlan = {
            coroutineScope.launch {
                updateState(controller.submitPlanEditor(currentState()))
            }
        }
    )
}

