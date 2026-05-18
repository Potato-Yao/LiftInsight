package com.potato.liftinsight.home.controller

import com.potato.liftinsight.body.model.BodyMetricState
import com.potato.liftinsight.body.model.defaultBodyMetrics
import com.potato.liftinsight.body.model.updateBodyMetric as applyBodyMetricUpdate
import com.potato.liftinsight.home.model.HomeCatalog
import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.plan.model.addMotionToPlan as appendMotionToPlan
import com.potato.liftinsight.plan.model.createTrainingPlan
import com.potato.liftinsight.plan.model.deletePlanMotion
import com.potato.liftinsight.plan.model.deleteTrainingPlan
import com.potato.liftinsight.plan.model.movePlanMotion as reorderPlanMotion
import com.potato.liftinsight.plan.model.planMotion
import com.potato.liftinsight.plan.model.selectTrainingPlan
import com.potato.liftinsight.plan.model.trainingPlan
import com.potato.liftinsight.plan.model.updateMotionRepsPerSet as applyMotionRepsPerSetUpdate
import com.potato.liftinsight.plan.model.updateMotionSets as applyMotionSetsUpdate
import com.potato.liftinsight.plan.model.updateTrainingPlanName

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
    val planDestination: PlanDestination = PlanDestination.List,
    val planIdPendingDelete: Int? = null,
    val motionPendingDelete: MotionDeleteTarget? = null,
    val addMotionPlanId: Int? = null
) {
    val selectedTabIndex: Int
        get() = selectedTab.ordinal
}

sealed interface PlanDestination {
    data object List : PlanDestination

    data class Detail(val planId: Int) : PlanDestination

    data class Motion(val planId: Int, val motionEntryId: Int) : PlanDestination
}

data class MotionDeleteTarget(
    val planId: Int,
    val motionEntryId: Int
)

fun planDestinationDepth(destination: PlanDestination): Int {
    return when (destination) {
        PlanDestination.List -> 0
        is PlanDestination.Detail -> 1
        is PlanDestination.Motion -> 2
    }
}

class HomeController(
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    fun createInitialState(catalog: HomeCatalog): HomeState {
        val currentPlanId = if (catalog.initialTrainingPlans.any { it.id == catalog.initialCurrentPlanId }) {
            catalog.initialCurrentPlanId
        } else {
            catalog.initialTrainingPlans.firstOrNull()?.id ?: -1
        }

        return HomeState(
            bodyMetrics = defaultBodyMetrics(),
            availableMotions = catalog.availableMotions,
            trainingPlans = catalog.initialTrainingPlans,
            currentPlanId = currentPlanId
        )
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

    fun createPlan(
        state: HomeState,
        name: String
    ): HomeState {
        val createResult = createTrainingPlan(
            plans = state.trainingPlans,
            name = name,
            createdAt = nowProvider()
        )

        return state.copy(
            trainingPlans = createResult.plans,
            planDestination = PlanDestination.Detail(createResult.createdPlanId)
        )
    }

    fun selectPlan(
        state: HomeState,
        planId: Int
    ): HomeState {
        val selectionResult = selectTrainingPlan(
            plans = state.trainingPlans,
            currentPlanId = state.currentPlanId,
            planId = planId,
            selectedAt = nowProvider()
        )

        return state.copy(
            trainingPlans = selectionResult.plans,
            currentPlanId = selectionResult.currentPlanId
        )
    }

    fun showPlanList(state: HomeState): HomeState {
        return state.copy(planDestination = PlanDestination.List)
    }

    fun showPlanDetail(
        state: HomeState,
        planId: Int
    ): HomeState {
        if (trainingPlan(state.trainingPlans, planId) == null) {
            return state.copy(planDestination = PlanDestination.List)
        }

        return state.copy(planDestination = PlanDestination.Detail(planId))
    }

    fun showMotionDetail(
        state: HomeState,
        planId: Int,
        motionEntryId: Int
    ): HomeState {
        val plan = trainingPlan(state.trainingPlans, planId)
        val motion = plan?.let { planMotion(it, motionEntryId) }

        if (plan == null || motion == null) {
            return state.copy(planDestination = PlanDestination.List)
        }

        return state.copy(
            planDestination = PlanDestination.Motion(
                planId = planId,
                motionEntryId = motionEntryId
            )
        )
    }

    fun renamePlan(
        state: HomeState,
        planId: Int,
        newName: String
    ): HomeState {
        return state.copy(
            trainingPlans = updateTrainingPlanName(
                plans = state.trainingPlans,
                planId = planId,
                newName = newName
            )
        )
    }

    fun movePlanMotion(
        state: HomeState,
        planId: Int,
        motionEntryId: Int,
        direction: Int
    ): HomeState {
        return state.copy(
            trainingPlans = reorderPlanMotion(
                plans = state.trainingPlans,
                planId = planId,
                motionEntryId = motionEntryId,
                direction = direction
            )
        )
    }

    fun updateMotionSets(
        state: HomeState,
        planId: Int,
        motionEntryId: Int,
        sets: Int
    ): HomeState {
        return state.copy(
            trainingPlans = applyMotionSetsUpdate(
                plans = state.trainingPlans,
                planId = planId,
                motionEntryId = motionEntryId,
                sets = sets
            )
        )
    }

    fun updateMotionRepsPerSet(
        state: HomeState,
        planId: Int,
        motionEntryId: Int,
        repsPerSet: Int
    ): HomeState {
        return state.copy(
            trainingPlans = applyMotionRepsPerSetUpdate(
                plans = state.trainingPlans,
                planId = planId,
                motionEntryId = motionEntryId,
                repsPerSet = repsPerSet
            )
        )
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

    fun confirmPlanDeletion(state: HomeState): HomeState {
        val pendingPlanId = state.planIdPendingDelete ?: return state
        val deleteResult = deleteTrainingPlan(
            plans = state.trainingPlans,
            currentPlanId = state.currentPlanId,
            planId = pendingPlanId
        )

        return state.copy(
            trainingPlans = deleteResult.plans,
            currentPlanId = deleteResult.currentPlanId,
            planDestination = PlanDestination.List,
            planIdPendingDelete = null
        )
    }

    fun requestMotionDeletion(
        state: HomeState,
        planId: Int,
        motionEntryId: Int
    ): HomeState {
        val plan = trainingPlan(state.trainingPlans, planId)
        val motion = plan?.let { planMotion(it, motionEntryId) }

        if (plan == null || motion == null) {
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

    fun confirmMotionDeletion(state: HomeState): HomeState {
        val pendingTarget = state.motionPendingDelete ?: return state

        return state.copy(
            trainingPlans = deletePlanMotion(
                plans = state.trainingPlans,
                planId = pendingTarget.planId,
                motionEntryId = pendingTarget.motionEntryId
            ),
            planDestination = PlanDestination.Detail(pendingTarget.planId),
            motionPendingDelete = null
        )
    }

    fun openAddMotionPicker(
        state: HomeState,
        planId: Int
    ): HomeState {
        if (trainingPlan(state.trainingPlans, planId) == null) {
            return state
        }

        return state.copy(addMotionPlanId = planId)
    }

    fun closeAddMotionPicker(state: HomeState): HomeState {
        return state.copy(addMotionPlanId = null)
    }

    fun addMotionToPlan(
        state: HomeState,
        planId: Int,
        motion: AvailableMotionState
    ): HomeState {
        val addResult = appendMotionToPlan(
            plans = state.trainingPlans,
            planId = planId,
            motion = motion
        )
        val updatedState = state.copy(
            trainingPlans = addResult.plans,
            addMotionPlanId = null
        )

        if (addResult.motionEntryId == -1) {
            return updatedState
        }

        return updatedState.copy(
            planDestination = PlanDestination.Motion(
                planId = planId,
                motionEntryId = addResult.motionEntryId
            )
        )
    }
}


