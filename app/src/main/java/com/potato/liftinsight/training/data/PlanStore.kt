package com.potato.liftinsight.training.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger

class PlanStore private constructor(
    private val planDao: PlanDao,
    private val logger: AppLogger
) {
    fun createPlan(request: CreatePlanRequest): Int {
        logTrace(
            "createPlan start: requestedName=${request.name.orEmpty().trim()}, cyclePeriod=${request.cyclePeriod}, metaPlanCount=${request.metaPlans.size}"
        )

        val cyclePeriod = validateCyclePeriod(request.cyclePeriod)
        val plan = PlanEntity(
            name = normalizeRequiredText(request.name, "Plan name"),
            cyclePeriod = cyclePeriod,
            currentIndex = validateCurrentIndex(
                currentIndex = request.currentIndex,
                cyclePeriod = cyclePeriod
            ),
            lastAppliedAt = validateLastAppliedAt(request.lastAppliedAt)
        )
        val metaPlans = prepareCreateMetaPlans(
            metaPlans = request.metaPlans,
            cyclePeriod = cyclePeriod
        )

        try {
            val planId = planDao.createPlan(
                plan = plan,
                metaPlans = metaPlans.map { metaPlan -> metaPlan.toEntity(planId = 0) }
            )

            logTrace("createPlan result: planId=$planId, metaPlanCount=${metaPlans.size}")

            return planId
        } catch (error: SQLiteConstraintException) {
            logTrace("createPlan constraint violation: requestedName=${plan.name}, metaPlanCount=${metaPlans.size}")
            throw planConstraintError(error)
        }
    }

    fun getPlan(planId: Int): PlanRecord? {
        if (planId <= 0) {
            logTrace("getPlan skipped: planId=$planId")
            return null
        }

        val plan = planDao.getPlanRecord(planId)

        logTrace("getPlan result: planId=$planId, found=${plan != null}")

        return plan
    }

    fun getPlans(): List<PlanRecord> {
        val plans = planDao.getPlanRecords()

        logTrace("getPlans result: count=${plans.size}")

        return plans
    }

    fun updatePlan(plan: PlanRecord): Boolean {
        logTrace(
            "updatePlan start: planId=${plan.id}, requestedName=${plan.name.trim()}, cyclePeriod=${plan.cyclePeriod}, metaPlanCount=${plan.metaPlans.size}"
        )

        if (plan.id <= 0) {
            logTrace("updatePlan skipped: planId=${plan.id}")
            return false
        }

        val cyclePeriod = validateCyclePeriod(plan.cyclePeriod)
        val updatedPlan = plan.copy(
            name = normalizeRequiredText(plan.name, "Plan name"),
            cyclePeriod = cyclePeriod,
            currentIndex = validateCurrentIndex(
                currentIndex = plan.currentIndex,
                cyclePeriod = cyclePeriod
            ),
            lastAppliedAt = validateLastAppliedAt(plan.lastAppliedAt)
        )
        val metaPlans = prepareStoredMetaPlans(
            metaPlans = plan.metaPlans,
            cyclePeriod = cyclePeriod
        )

        try {
            val updated = planDao.updatePlan(
                plan = updatedPlan.toEntity(),
                metaPlans = metaPlans.map { metaPlan -> metaPlan.toEntity(planId = plan.id) }
            )

            logTrace("updatePlan result: planId=${plan.id}, updated=$updated, metaPlanCount=${metaPlans.size}")

            return updated
        } catch (error: SQLiteConstraintException) {
            logTrace("updatePlan constraint violation: planId=${plan.id}, requestedName=${updatedPlan.name}")
            throw planConstraintError(error)
        }
    }

    fun deletePlan(planId: Int): Boolean {
        logTrace("deletePlan start: planId=$planId")

        if (planId <= 0) {
            logTrace("deletePlan skipped: planId=$planId")
            return false
        }

        val deleted = planDao.deletePlan(planId) > 0

        logTrace("deletePlan result: planId=$planId, deleted=$deleted")

        return deleted
    }

    private fun logTrace(message: String) {
        logger.trace(TAG, message)
    }

    companion object {
        private const val TAG = "PlanStore"

        fun from(context: Context): PlanStore {
            return PlanStore(
                planDao = LiftInsightDatabase.from(context).planDao(),
                logger = AndroidAppLogger
            )
        }

        internal fun fromDatabase(
            database: LiftInsightDatabase,
            logger: AppLogger = AndroidAppLogger
        ): PlanStore {
            return PlanStore(database.planDao(), logger)
        }
    }
}

private data class PreparedMetaPlan(
    val motionId: Int,
    val dayIndex: Int,
    val sets: Int,
    val reps: Int,
    val intensity: Double,
    val weight: Double,
    val orderIndex: Int
)

private fun validateCyclePeriod(cyclePeriod: Int): Int {
    if (cyclePeriod <= 0) {
        throw IllegalArgumentException("Cycle period must be greater than zero.")
    }

    return cyclePeriod
}

private fun validateCurrentIndex(
    currentIndex: Int,
    cyclePeriod: Int
): Int {
    if (currentIndex <= 0) {
        throw IllegalArgumentException("Current index must be greater than zero.")
    }

    if (currentIndex > cyclePeriod) {
        throw IllegalArgumentException("Current index must not be greater than the cycle period.")
    }

    return currentIndex
}

private fun validateLastAppliedAt(lastAppliedAt: Long): Long {
    if (lastAppliedAt < 0L) {
        throw IllegalArgumentException("Last applied time must be zero or greater.")
    }

    return lastAppliedAt
}

private fun prepareCreateMetaPlans(
    metaPlans: List<CreateMetaPlanRequest>,
    cyclePeriod: Int
): List<PreparedMetaPlan> {
    return preparePreparedMetaPlans(
        metaPlans.map { metaPlan ->
            PreparedMetaPlan(
                motionId = metaPlan.motionId,
                dayIndex = metaPlan.dayIndex,
                sets = metaPlan.sets,
                reps = metaPlan.reps,
                intensity = metaPlan.intensity,
                weight = metaPlan.weight,
                orderIndex = metaPlan.orderIndex
            )
        },
        cyclePeriod = cyclePeriod
    )
}

private fun prepareStoredMetaPlans(
    metaPlans: List<MetaPlanRecord>,
    cyclePeriod: Int
): List<PreparedMetaPlan> {
    return preparePreparedMetaPlans(
        metaPlans.map { metaPlan ->
            PreparedMetaPlan(
                motionId = metaPlan.motionId,
                dayIndex = metaPlan.dayIndex,
                sets = metaPlan.sets,
                reps = metaPlan.reps,
                intensity = metaPlan.intensity,
                weight = metaPlan.weight,
                orderIndex = metaPlan.orderIndex
            )
        },
        cyclePeriod = cyclePeriod
    )
}

private fun preparePreparedMetaPlans(
    metaPlans: List<PreparedMetaPlan>,
    cyclePeriod: Int
): List<PreparedMetaPlan> {
    if (metaPlans.isEmpty()) {
        return emptyList()
    }

    val sortedMetaPlans = metaPlans.sortedWith(
        compareBy<PreparedMetaPlan> { metaPlan -> metaPlan.dayIndex }
            .thenBy { metaPlan -> metaPlan.orderIndex }
    )

    for (index in sortedMetaPlans.indices) {
        val metaPlan = sortedMetaPlans[index]

        if (metaPlan.motionId <= 0) {
            throw IllegalArgumentException("Meta plan motion id must be greater than zero.")
        }

        if (metaPlan.sets <= 0) {
            throw IllegalArgumentException("Meta plan sets must be greater than zero.")
        }

        if (metaPlan.dayIndex <= 0) {
            throw IllegalArgumentException("Meta plan day index must be greater than zero.")
        }

        if (metaPlan.dayIndex > cyclePeriod) {
            throw IllegalArgumentException("Meta plan day index must not be greater than the plan cycle period.")
        }

        if (metaPlan.reps <= 0) {
            throw IllegalArgumentException("Meta plan reps must be greater than zero.")
        }

        if (metaPlan.intensity < 0.0) {
            throw IllegalArgumentException("Meta plan intensity must be zero or greater.")
        }

        if (metaPlan.weight < 0.0) {
            throw IllegalArgumentException("Meta plan weight must be zero or greater.")
        }

        if (metaPlan.orderIndex <= 0) {
            throw IllegalArgumentException("Meta plan order index must be greater than zero.")
        }

        if (metaPlan.orderIndex > cyclePeriod) {
            throw IllegalArgumentException("Meta plan order index must not be greater than the plan cycle period.")
        }

        if (index == 0) {
            continue
        }

        val previousMetaPlan = sortedMetaPlans[index - 1]

        if (
            previousMetaPlan.dayIndex == metaPlan.dayIndex &&
            previousMetaPlan.orderIndex == metaPlan.orderIndex
        ) {
            throw IllegalArgumentException("Meta plan order indexes must be unique within one day.")
        }
    }

    return sortedMetaPlans
}

private fun PreparedMetaPlan.toEntity(planId: Int): MetaPlanEntity {
    return MetaPlanEntity(
        planId = planId,
        motionId = motionId,
        dayIndex = dayIndex,
        sets = sets,
        reps = reps,
        intensity = intensity,
        weight = weight,
        orderIndex = orderIndex
    )
}

private fun planConstraintError(error: SQLiteConstraintException): IllegalArgumentException {
    val message = error.message.orEmpty()

    if (message.contains("FOREIGN KEY", ignoreCase = true)) {
        return IllegalArgumentException("Meta plans must reference an existing motion.")
    }

    if (message.contains("plan_id") && message.contains("day_index") && message.contains("order_index")) {
        return IllegalArgumentException("Meta plan order indexes must be unique within one day.")
    }

    return IllegalArgumentException("Plan could not be saved because it violates a database constraint.")
}


