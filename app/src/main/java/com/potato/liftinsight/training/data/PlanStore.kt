package com.potato.liftinsight.training.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException

class PlanStore private constructor(
    private val planDao: PlanDao
) {
    fun createPlan(request: CreatePlanRequest): Int {
        val plan = PlanEntity(
            name = normalizeRequiredText(request.name, "Plan name"),
            repeatCycle = validateRepeatCycle(request.repeatCycle)
        )
        val metaPlans = prepareCreateMetaPlans(request.metaPlans)

        try {
            return planDao.createPlan(
                plan = plan,
                metaPlans = metaPlans.map { metaPlan -> metaPlan.toEntity(planId = 0) }
            )
        } catch (error: SQLiteConstraintException) {
            throw planConstraintError(error)
        }
    }

    fun getPlan(planId: Int): PlanRecord? {
        if (planId <= 0) {
            return null
        }

        return planDao.getPlanRecord(planId)
    }

    fun getPlans(): List<PlanRecord> {
        return planDao.getPlanRecords()
    }

    fun updatePlan(plan: PlanRecord): Boolean {
        if (plan.id <= 0) {
            return false
        }

        val updatedPlan = plan.copy(
            name = normalizeRequiredText(plan.name, "Plan name"),
            repeatCycle = validateRepeatCycle(plan.repeatCycle)
        )
        val metaPlans = prepareStoredMetaPlans(plan.metaPlans)

        try {
            return planDao.updatePlan(
                plan = updatedPlan.toEntity(),
                metaPlans = metaPlans.map { metaPlan -> metaPlan.toEntity(planId = plan.id) }
            )
        } catch (error: SQLiteConstraintException) {
            throw planConstraintError(error)
        }
    }

    fun deletePlan(planId: Int): Boolean {
        if (planId <= 0) {
            return false
        }

        return planDao.deletePlan(planId) > 0
    }

    companion object {
        fun from(context: Context): PlanStore {
            return PlanStore(LiftInsightDatabase.from(context).planDao())
        }

        internal fun fromDatabase(database: LiftInsightDatabase): PlanStore {
            return PlanStore(database.planDao())
        }
    }
}

private data class PreparedMetaPlan(
    val motionId: Int,
    val sets: Int,
    val reps: Int,
    val weight: Double,
    val orderIndex: Int
)

private fun validateRepeatCycle(repeatCycle: Int): Int {
    if (repeatCycle <= 0) {
        throw IllegalArgumentException("Repeat cycle must be greater than zero.")
    }

    return repeatCycle
}

private fun prepareCreateMetaPlans(metaPlans: List<CreateMetaPlanRequest>): List<PreparedMetaPlan> {
    return preparePreparedMetaPlans(
        metaPlans.map { metaPlan ->
            PreparedMetaPlan(
                motionId = metaPlan.motionId,
                sets = metaPlan.sets,
                reps = metaPlan.reps,
                weight = metaPlan.weight,
                orderIndex = metaPlan.orderIndex
            )
        }
    )
}

private fun prepareStoredMetaPlans(metaPlans: List<MetaPlanRecord>): List<PreparedMetaPlan> {
    return preparePreparedMetaPlans(
        metaPlans.map { metaPlan ->
            PreparedMetaPlan(
                motionId = metaPlan.motionId,
                sets = metaPlan.sets,
                reps = metaPlan.reps,
                weight = metaPlan.weight,
                orderIndex = metaPlan.orderIndex
            )
        }
    )
}

private fun preparePreparedMetaPlans(metaPlans: List<PreparedMetaPlan>): List<PreparedMetaPlan> {
    if (metaPlans.isEmpty()) {
        return emptyList()
    }

    val sortedMetaPlans = metaPlans.sortedBy { metaPlan -> metaPlan.orderIndex }

    for (index in sortedMetaPlans.indices) {
        val metaPlan = sortedMetaPlans[index]

        if (metaPlan.motionId <= 0) {
            throw IllegalArgumentException("Meta plan motion id must be greater than zero.")
        }

        if (metaPlan.sets <= 0) {
            throw IllegalArgumentException("Meta plan sets must be greater than zero.")
        }

        if (metaPlan.reps <= 0) {
            throw IllegalArgumentException("Meta plan reps must be greater than zero.")
        }

        if (metaPlan.weight < 0.0) {
            throw IllegalArgumentException("Meta plan weight must be zero or greater.")
        }

        if (metaPlan.orderIndex < 0) {
            throw IllegalArgumentException("Meta plan order index must be zero or greater.")
        }

        if (index == 0) {
            continue
        }

        val previousMetaPlan = sortedMetaPlans[index - 1]

        if (previousMetaPlan.orderIndex == metaPlan.orderIndex) {
            throw IllegalArgumentException("Meta plan order indexes must be unique within one plan.")
        }
    }

    return sortedMetaPlans
}

private fun PreparedMetaPlan.toEntity(planId: Int): MetaPlanEntity {
    return MetaPlanEntity(
        planId = planId,
        motionId = motionId,
        sets = sets,
        reps = reps,
        weight = weight,
        orderIndex = orderIndex
    )
}

private fun planConstraintError(error: SQLiteConstraintException): IllegalArgumentException {
    val message = error.message.orEmpty()

    if (message.contains("FOREIGN KEY", ignoreCase = true)) {
        return IllegalArgumentException("Meta plans must reference an existing motion.")
    }

    if (message.contains("plan_id") && message.contains("order_index")) {
        return IllegalArgumentException("Meta plan order indexes must be unique within one plan.")
    }

    return IllegalArgumentException("Plan could not be saved because it violates a database constraint.")
}


