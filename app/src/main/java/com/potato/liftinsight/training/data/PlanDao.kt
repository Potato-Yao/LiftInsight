package com.potato.liftinsight.training.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
abstract class PlanDao {
    @Insert
    protected abstract fun insertPlanEntity(plan: PlanEntity): Long

    @Insert
    protected abstract fun insertMetaPlanEntities(metaPlans: List<MetaPlanEntity>)

    @Update
    protected abstract fun updatePlanEntity(plan: PlanEntity): Int

    @Query("DELETE FROM `plan` WHERE id = :planId")
    abstract fun deletePlan(planId: Int): Int

    @Query("DELETE FROM metaplan WHERE plan_id = :planId")
    protected abstract fun deleteMetaPlansForPlan(planId: Int): Int

    @Query("SELECT * FROM `plan` WHERE id = :planId")
    protected abstract fun getPlanEntity(planId: Int): PlanEntity?

    @Query("SELECT * FROM `plan` ORDER BY name COLLATE NOCASE ASC, id ASC")
    protected abstract fun getPlanEntities(): List<PlanEntity>

    @Query("SELECT COUNT(*) FROM metaplan WHERE plan_id = :planId")
    abstract fun countMetaPlansForPlan(planId: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertPlanSelection(selection: PlanSelectionEntity)

    @Query("SELECT * FROM plan_selection WHERE id = 1")
    abstract fun getPlanSelection(): PlanSelectionEntity?

    @Query("DELETE FROM plan_selection")
    abstract fun clearPlanSelection()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertWorkoutSession(session: WorkoutSessionEntity)

    @Query("SELECT * FROM workout_session WHERE id = 1")
    abstract fun getWorkoutSession(): WorkoutSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertWorkoutProgress(progress: WorkoutProgressEntity)

    @Query("SELECT * FROM workout_progress WHERE id = 1")
    abstract fun getWorkoutProgress(): WorkoutProgressEntity?

    @Query("DELETE FROM workout_progress")
    abstract fun clearWorkoutProgress()

    @Query(
        """
        SELECT
            metaplan.id,
            metaplan.plan_id,
            metaplan.motion_id,
            motion.name AS motion_name,
            metaplan.day_index,
            metaplan.sets,
            metaplan.reps,
            metaplan.intensity,
            metaplan.weight,
            metaplan.order_index
        FROM metaplan
        INNER JOIN motion ON motion.id = metaplan.motion_id
        WHERE metaplan.plan_id = :planId
        ORDER BY metaplan.day_index ASC, metaplan.order_index ASC, metaplan.id ASC
        """
    )
    protected abstract fun getMetaPlanRowsForPlan(planId: Int): List<MetaPlanRow>

    @Query(
        """
        SELECT
            metaplan.id,
            metaplan.plan_id,
            metaplan.motion_id,
            motion.name AS motion_name,
            metaplan.day_index,
            metaplan.sets,
            metaplan.reps,
            metaplan.intensity,
            metaplan.weight,
            metaplan.order_index
        FROM metaplan
        INNER JOIN motion ON motion.id = metaplan.motion_id
        WHERE metaplan.plan_id IN (:planIds)
        ORDER BY metaplan.plan_id ASC, metaplan.day_index ASC, metaplan.order_index ASC, metaplan.id ASC
        """
    )
    protected abstract fun getMetaPlanRowsForPlans(planIds: List<Int>): List<MetaPlanRow>

    @Transaction
    open fun createPlan(
        plan: PlanEntity,
        metaPlans: List<MetaPlanEntity>
    ): Int {
        val planId = insertPlanEntity(plan).toInt()

        if (metaPlans.isNotEmpty()) {
            insertMetaPlanEntities(metaPlans.map { metaPlan ->
                metaPlan.copy(planId = planId)
            })
        }

        return planId
    }

    @Transaction
    open fun updatePlan(
        plan: PlanEntity,
        metaPlans: List<MetaPlanEntity>
    ): Boolean {
        val updatedRows = updatePlanEntity(plan)

        if (updatedRows == 0) {
            return false
        }

        deleteMetaPlansForPlan(plan.id)

        if (metaPlans.isNotEmpty()) {
            insertMetaPlanEntities(metaPlans.map { metaPlan ->
                metaPlan.copy(planId = plan.id)
            })
        }

        return true
    }

    @Transaction
    open fun getPlanRecord(planId: Int): PlanRecord? {
        val plan = getPlanEntity(planId) ?: return null
        val metaPlans = getMetaPlanRowsForPlan(planId)

        return plan.toRecord(metaPlans)
    }

    @Transaction
    open fun getPlanRecords(): List<PlanRecord> {
        val plans = getPlanEntities()

        if (plans.isEmpty()) {
            return emptyList()
        }

        val metaPlansByPlanId = getMetaPlanRowsForPlans(plans.map { plan -> plan.id })
            .groupBy { metaPlan -> metaPlan.planId }

        return plans.map { plan ->
            plan.toRecord(metaPlansByPlanId[plan.id].orEmpty())
        }
    }
}


