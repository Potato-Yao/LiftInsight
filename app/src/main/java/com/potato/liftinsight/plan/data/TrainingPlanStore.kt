package com.potato.liftinsight.plan.data

import android.content.Context
import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.plan.model.normalizePlanDayIndex
import com.potato.liftinsight.plan.model.sortPlansByLastApplied
import com.potato.liftinsight.training.data.CreateMotionRequest
import com.potato.liftinsight.training.data.CreateMetaPlanRequest
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
    private val database: LiftInsightDatabase,
    private val logger: AppLogger
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
        val currentPlanId = database.planDao().getPlanSelection()?.currentPlanId ?: -1

        logTrace("getCurrentPlanId result: currentPlanId=$currentPlanId")

        return currentPlanId
    }

    fun setCurrentPlan(planId: Int): Boolean {
        logTrace("setCurrentPlan start: planId=$planId")

        if (planStore.getPlan(planId) == null) {
            logTrace("setCurrentPlan result: planId=$planId, updated=false")
            return false
        }

        database.planDao().upsertPlanSelection(PlanSelectionEntity(currentPlanId = planId))

        logger.info(TAG, "Updated current training plan selection: planId=$planId")
        logTrace("setCurrentPlan result: planId=$planId, updated=true")

        return true
    }

    fun clearCurrentPlan() {
        logTrace("clearCurrentPlan start")

        database.planDao().clearPlanSelection()

        logger.info(TAG, "Cleared current training plan selection")
        logTrace("clearCurrentPlan result: cleared=true")
    }

    fun createTrainingPlan(
        plan: TrainingPlanState
    ): Int {
        return planStore.createPlan(
            CreatePlanRequest(
                name = plan.name,
                cyclePeriod = plan.cyclePeriod,
                currentIndex = normalizePlanDayIndex(
                    dayIndex = plan.currentIndex,
                    cyclePeriod = plan.cyclePeriod
                ),
                lastAppliedAt = plan.lastAppliedAt,
                metaPlans = plan.motions.map { motion ->
                    CreateMetaPlanRequest(
                        motionId = motion.motionId,
                        dayIndex = normalizePlanDayIndex(
                            dayIndex = motion.dayIndex,
                            cyclePeriod = plan.cyclePeriod
                        ),
                        sets = motion.sets,
                        reps = motion.repsPerSet,
                        intensity = motion.intensity,
                        weight = motion.weight,
                        orderIndex = motion.orderIndex
                    )
                }
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
                plan = plan.copy(
                    id = 0,
                    motions = emptyList()
                )
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
        private const val TAG = "TrainingPlanStore"

        fun from(context: Context): TrainingPlanStore {
            return fromDatabase(LiftInsightDatabase.from(context))
        }

        internal fun fromDatabase(
            database: LiftInsightDatabase,
            logger: AppLogger = AndroidAppLogger
        ): TrainingPlanStore {
            return TrainingPlanStore(
                motionStore = MotionStore.fromDatabase(database, logger),
                planStore = PlanStore.fromDatabase(database, logger),
                database = database,
                logger = logger
            )
        }
    }

    private fun logTrace(message: String) {
        logger.trace(TAG, message)
    }
}

private fun PlanRecord.toState(): TrainingPlanState {
    val sortedMetaPlans = metaPlans.sortedBy { metaPlan -> metaPlan.orderIndex }

    return TrainingPlanState(
        id = id,
        name = name,
        lastAppliedAt = lastAppliedAt,
        cyclePeriod = cyclePeriod,
        currentIndex = normalizePlanDayIndex(
            dayIndex = currentIndex,
            cyclePeriod = cyclePeriod
        ),
        motions = sortedMetaPlans
            .map { metaPlan ->
                PlanMotionState(
                    entryId = metaPlan.id,
                    motionId = metaPlan.motionId,
                    title = metaPlan.motionName,
                    dayIndex = metaPlan.dayIndex,
                    sets = metaPlan.sets,
                    repsPerSet = metaPlan.reps,
                    intensity = metaPlan.intensity,
                    weight = metaPlan.weight,
                    orderIndex = metaPlan.orderIndex
                )
            }
    )
}

private fun TrainingPlanState.toRecord(): PlanRecord {
    return PlanRecord(
        id = id,
        name = name,
        cyclePeriod = cyclePeriod,
        currentIndex = normalizePlanDayIndex(
            dayIndex = currentIndex,
            cyclePeriod = cyclePeriod
        ),
        lastAppliedAt = lastAppliedAt,
        metaPlans = motions.map { motion ->
            MetaPlanRecord(
                id = motion.entryId,
                motionId = motion.motionId,
                motionName = motion.title,
                dayIndex = normalizePlanDayIndex(
                    dayIndex = motion.dayIndex,
                    cyclePeriod = cyclePeriod
                ),
                sets = motion.sets,
                reps = motion.repsPerSet,
                intensity = motion.intensity,
                weight = motion.weight,
                orderIndex = motion.orderIndex
            )
        }
    )
}



