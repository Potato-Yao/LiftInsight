package com.potato.liftinsight.home.controller

import com.potato.liftinsight.body.model.BodyMetricState
import com.potato.liftinsight.body.model.defaultBodyMetrics
import com.potato.liftinsight.body.model.updateBodyMetric as applyBodyMetricUpdate
import com.potato.liftinsight.plan.data.TrainingPlanSeedCatalog
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.plan.model.deletePlanMotion
import com.potato.liftinsight.plan.model.movePlanMotion as reorderPlanMotion
import com.potato.liftinsight.plan.model.planMotion
import com.potato.liftinsight.plan.model.sortPlansByLastApplied
import com.potato.liftinsight.plan.model.trainingPlan
import com.potato.liftinsight.plan.model.updateMotionRepsPerSet as applyMotionRepsPerSetUpdate
import com.potato.liftinsight.plan.model.updateMotionSets as applyMotionSetsUpdate
import com.potato.liftinsight.plan.model.updateTrainingPlanName
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
                requestedDestination = PlanDestination.List,
                planIdPendingDelete = null,
                motionPendingDelete = null,
                addMotionPlanId = null
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

    suspend fun createPlan(
        state: HomeState,
        name: String
    ): HomeState {
        val trimmedName = name.trim()

        if (trimmedName.isEmpty()) {
            return state
        }

        val createdPlanId = withContext(Dispatchers.IO) {
            trainingPlanStore.createTrainingPlan(
                name = trimmedName,
                createdAt = nowProvider()
            )
        }

        return reloadState(
            state = state,
            requestedDestination = PlanDestination.Detail(createdPlanId)
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

    suspend fun renamePlan(
        state: HomeState,
        planId: Int,
        newName: String
    ): HomeState {
        val trimmedName = newName.trim()

        if (trimmedName.isEmpty()) {
            return state
        }

        val updateSucceeded = withContext(Dispatchers.IO) {
            val storedPlan = trainingPlanStore.getTrainingPlan(planId) ?: return@withContext false
            val updatedPlan = updateTrainingPlanName(
                plans = listOf(storedPlan),
                planId = planId,
                newName = trimmedName
            ).first()

            trainingPlanStore.updateTrainingPlan(updatedPlan)
        }

        if (!updateSucceeded) {
            return state
        }

        return reloadState(state)
    }

    suspend fun movePlanMotion(
        state: HomeState,
        planId: Int,
        motionEntryId: Int,
        direction: Int
    ): HomeState {
        val updateSucceeded = withContext(Dispatchers.IO) {
            val storedPlan = trainingPlanStore.getTrainingPlan(planId) ?: return@withContext false
            val updatedPlan = reorderPlanMotion(
                plans = listOf(storedPlan),
                planId = planId,
                motionEntryId = motionEntryId,
                direction = direction
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

    suspend fun updateMotionSets(
        state: HomeState,
        planId: Int,
        motionEntryId: Int,
        sets: Int
    ): HomeState {
        val updateSucceeded = withContext(Dispatchers.IO) {
            val storedPlan = trainingPlanStore.getTrainingPlan(planId) ?: return@withContext false
            val updatedPlan = applyMotionSetsUpdate(
                plans = listOf(storedPlan),
                planId = planId,
                motionEntryId = motionEntryId,
                sets = sets
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

    suspend fun updateMotionRepsPerSet(
        state: HomeState,
        planId: Int,
        motionEntryId: Int,
        repsPerSet: Int
    ): HomeState {
        val updateSucceeded = withContext(Dispatchers.IO) {
            val storedPlan = trainingPlanStore.getTrainingPlan(planId) ?: return@withContext false
            val updatedPlan = applyMotionRepsPerSetUpdate(
                plans = listOf(storedPlan),
                planId = planId,
                motionEntryId = motionEntryId,
                repsPerSet = repsPerSet
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
            val wasCurrentPlan = trainingPlanStore.getCurrentPlanId() == pendingPlanId
            val deleted = trainingPlanStore.deleteTrainingPlan(pendingPlanId)

            if (!deleted) {
                return@withContext false
            }

            if (!wasCurrentPlan) {
                return@withContext true
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

            true
        }

        if (!deleteSucceeded) {
            return state
        }

        return reloadState(
            state = state,
            requestedDestination = PlanDestination.List,
            planIdPendingDelete = null,
            motionPendingDelete = null,
            addMotionPlanId = null
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

    suspend fun confirmMotionDeletion(state: HomeState): HomeState {
        val pendingTarget = state.motionPendingDelete ?: return state
        val deleteSucceeded = withContext(Dispatchers.IO) {
            val storedPlan = trainingPlanStore.getTrainingPlan(pendingTarget.planId) ?: return@withContext false
            val updatedPlan = storedPlan.copy(
                motions = deletePlanMotion(
                    plans = listOf(storedPlan),
                    planId = pendingTarget.planId,
                    motionEntryId = pendingTarget.motionEntryId
                ).first().motions
            )

            trainingPlanStore.updateTrainingPlan(updatedPlan)
        }

        if (!deleteSucceeded) {
            return state
        }

        return reloadState(
            state = state,
            requestedDestination = PlanDestination.Detail(pendingTarget.planId),
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

    suspend fun addMotionToPlan(
        state: HomeState,
        planId: Int,
        motion: AvailableMotionState
    ): HomeState {
        val addResult = withContext(Dispatchers.IO) {
            val storedPlan = trainingPlanStore.getTrainingPlan(planId) ?: return@withContext null
            val updatedPlan = storedPlan.copy(
                motions = storedPlan.motions + PlanMotionState(
                    entryId = 0,
                    motionId = motion.id,
                    title = motion.title,
                    sets = 1,
                    repsPerSet = 1,
                    intensity = 0.0
                )
            )

            if (!trainingPlanStore.updateTrainingPlan(updatedPlan)) {
                return@withContext null
            }

            val refreshedPlan = trainingPlanStore.getTrainingPlan(planId) ?: return@withContext null
            val createdMotionEntryId = refreshedPlan.motions
                .map { storedMotion -> storedMotion.entryId }
                .firstOrNull { entryId ->
                    storedPlan.motions.none { existingMotion -> existingMotion.entryId == entryId }
                }
                ?: -1

            createdMotionEntryId
        } ?: return state

        if (addResult == -1) {
            return reloadState(
                state = state,
                addMotionPlanId = null
            )
        }

        return reloadState(
            state = state,
            requestedDestination = PlanDestination.Motion(
                planId = planId,
                motionEntryId = addResult
            ),
            addMotionPlanId = null
        )
    }

    private suspend fun reloadState(
        state: HomeState,
        requestedDestination: PlanDestination = state.planDestination,
        planIdPendingDelete: Int? = state.planIdPendingDelete,
        motionPendingDelete: MotionDeleteTarget? = state.motionPendingDelete,
        addMotionPlanId: Int? = state.addMotionPlanId
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
                addMotionPlanId = addMotionPlanId
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
        addMotionPlanId: Int?
    ): HomeState {
        return state.copy(
            availableMotions = availableMotions,
            trainingPlans = trainingPlans,
            currentPlanId = currentPlanId,
            planDestination = sanitizePlanDestination(trainingPlans, requestedDestination),
            planIdPendingDelete = planIdPendingDelete?.takeIf { planId ->
                trainingPlan(trainingPlans, planId) != null
            },
            motionPendingDelete = motionPendingDelete?.takeIf { pendingTarget ->
                val plan = trainingPlan(trainingPlans, pendingTarget.planId)
                val motion = plan?.let { storedPlan ->
                    planMotion(storedPlan, pendingTarget.motionEntryId)
                }

                plan != null && motion != null
            },
            addMotionPlanId = addMotionPlanId?.takeIf { planId ->
                trainingPlan(trainingPlans, planId) != null
            }
        )
    }

    private fun sanitizePlanDestination(
        trainingPlans: List<TrainingPlanState>,
        requestedDestination: PlanDestination
    ): PlanDestination {
        return when (requestedDestination) {
            PlanDestination.List -> PlanDestination.List

            is PlanDestination.Detail -> {
                if (trainingPlan(trainingPlans, requestedDestination.planId) == null) {
                    PlanDestination.List
                } else {
                    requestedDestination
                }
            }

            is PlanDestination.Motion -> {
                val plan = trainingPlan(trainingPlans, requestedDestination.planId)
                val motion = plan?.let { storedPlan ->
                    planMotion(storedPlan, requestedDestination.motionEntryId)
                }

                if (plan == null) {
                    PlanDestination.List
                } else if (motion == null) {
                    PlanDestination.Detail(plan.id)
                } else {
                    requestedDestination
                }
            }
        }
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
}


