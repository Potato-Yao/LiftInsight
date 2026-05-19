package com.potato.liftinsight.plan.data

import android.content.Context
import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.plan.model.sortPlansByLastApplied
import com.potato.liftinsight.training.data.CreateMotionRequest
import com.potato.liftinsight.training.data.CreatePlanRequest
import com.potato.liftinsight.training.data.LiftInsightDatabase
import com.potato.liftinsight.training.data.MetaPlanRecord
import com.potato.liftinsight.training.data.MotionStore
import com.potato.liftinsight.training.data.PlanSelectionEntity
import com.potato.liftinsight.training.data.PlanStore
import com.potato.liftinsight.training.data.PlanRecord

class TrainingPlanStore private constructor(
    private val motionStore: MotionStore,
    private val planStore: PlanStore,
    private val database: LiftInsightDatabase
) {
    fun getAvailableMotions(): List<AvailableMotionState> {
        return motionStore.getMotions().map { motion ->
            AvailableMotionState(
                id = motion.id,
                title = motion.name
            )
        }
    }

    fun ensureAvailableMotions(motions: List<AvailableMotionState>) {
        val existingMotionsByName = motionStore.getMotions().associateBy { motion -> motion.name }

        motions.forEach { motion ->
            val existingMotion = existingMotionsByName[motion.title]

            if (existingMotion == null) {
                motionStore.createMotion(
                    CreateMotionRequest(
                        name = motion.title
                    )
                )
                return@forEach
            }
        }
    }

    fun getTrainingPlans(): List<TrainingPlanState> {
        return sortPlansByLastApplied(
            planStore.getPlans().map { plan ->
                plan.toState()
            }
        )
    }

    fun getTrainingPlan(planId: Int): TrainingPlanState? {
        return planStore.getPlan(planId)?.toState()
    }

    fun getCurrentPlanId(): Int {
        return database.planDao().getPlanSelection()?.currentPlanId ?: -1
    }

    fun setCurrentPlan(planId: Int): Boolean {
        if (planStore.getPlan(planId) == null) {
            return false
        }

        database.planDao().upsertPlanSelection(PlanSelectionEntity(currentPlanId = planId))
        return true
    }

    fun clearCurrentPlan() {
        database.planDao().clearPlanSelection()
    }

    fun createTrainingPlan(
        name: String,
        createdAt: Long
    ): Int {
        return planStore.createPlan(
            CreatePlanRequest(
                name = name,
                repeatCycle = DEFAULT_REPEAT_CYCLE,
                lastAppliedAt = createdAt
            )
        )
    }

    fun updateTrainingPlan(plan: TrainingPlanState): Boolean {
        return planStore.updatePlan(plan.toRecord())
    }

    fun deleteTrainingPlan(planId: Int): Boolean {
        return planStore.deletePlan(planId)
    }

    fun seedPlansIfEmpty(
        plans: List<TrainingPlanState>,
        currentPlanId: Int
    ) {
        if (plans.isEmpty() || planStore.getPlans().isNotEmpty()) {
            return
        }

        val availableMotionsByTitle = getAvailableMotions().associateBy { motion -> motion.title }
        val createdPlanIds = mutableMapOf<Int, Int>()

        plans.forEach { plan ->
            val createdPlanId = createTrainingPlan(
                name = plan.name,
                createdAt = plan.lastAppliedAt
            )
            createdPlanIds[plan.id] = createdPlanId

            val seededPlan = plan.copy(
                id = createdPlanId,
                motions = plan.motions.mapNotNull { motion ->
                    val storedMotion = availableMotionsByTitle[motion.title] ?: return@mapNotNull null

                    motion.copy(
                        entryId = 0,
                        motionId = storedMotion.id
                    )
                }
            )
            updateTrainingPlan(seededPlan)
        }

        val resolvedCurrentPlanId = createdPlanIds[currentPlanId] ?: return
        setCurrentPlan(resolvedCurrentPlanId)
    }

    companion object {
        private const val DEFAULT_REPEAT_CYCLE = 7

        fun from(context: Context): TrainingPlanStore {
            return fromDatabase(LiftInsightDatabase.from(context))
        }

        internal fun fromDatabase(database: LiftInsightDatabase): TrainingPlanStore {
            return TrainingPlanStore(
                motionStore = MotionStore.fromDatabase(database),
                planStore = PlanStore.fromDatabase(database),
                database = database
            )
        }
    }
}

private fun PlanRecord.toState(): TrainingPlanState {
    return TrainingPlanState(
        id = id,
        name = name,
        lastAppliedAt = lastAppliedAt,
        motions = metaPlans
            .sortedBy { metaPlan -> metaPlan.orderIndex }
            .map { metaPlan ->
                PlanMotionState(
                    entryId = metaPlan.id,
                    motionId = metaPlan.motionId,
                    title = metaPlan.motionName,
                    sets = metaPlan.sets,
                    repsPerSet = metaPlan.reps,
                    intensity = metaPlan.intensity
                )
            }
    )
}

private fun TrainingPlanState.toRecord(): PlanRecord {
    return PlanRecord(
        id = id,
        name = name,
        repeatCycle = 7,
        lastAppliedAt = lastAppliedAt,
        metaPlans = motions.mapIndexed { index, motion ->
            MetaPlanRecord(
                id = motion.entryId,
                motionId = motion.motionId,
                motionName = motion.title,
                sets = motion.sets,
                reps = motion.repsPerSet,
                intensity = motion.intensity,
                weight = 0.0,
                orderIndex = index
            )
        }
    )
}



