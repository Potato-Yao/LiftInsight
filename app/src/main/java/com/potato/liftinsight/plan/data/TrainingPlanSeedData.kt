package com.potato.liftinsight.plan.data

import android.content.Context
import com.potato.liftinsight.R
import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.TrainingPlanState

data class TrainingPlanSeedCatalog(
    val availableMotions: List<AvailableMotionState>,
    val debugPlans: List<TrainingPlanState>,
    val debugCurrentPlanId: Int
)

fun defaultTrainingPlanSeedCatalog(context: Context): TrainingPlanSeedCatalog {
    val availableMotions = listOf(
        AvailableMotionState(
            id = 1,
            title = context.getString(R.string.motion_name_snatch)
        ),
        AvailableMotionState(
            id = 2,
            title = context.getString(R.string.motion_name_clean_and_jerk)
        ),
        AvailableMotionState(
            id = 3,
            title = context.getString(R.string.motion_name_snatch_pull)
        ),
        AvailableMotionState(
            id = 4,
            title = context.getString(R.string.motion_name_clean_pull)
        ),
        AvailableMotionState(
            id = 5,
            title = context.getString(R.string.motion_name_push_press)
        ),
        AvailableMotionState(
            id = 6,
            title = context.getString(R.string.body_front_squat)
        ),
        AvailableMotionState(
            id = 7,
            title = context.getString(R.string.body_back_squat)
        )
    )

    val debugPlans = listOf(
        TrainingPlanState(
            id = 1,
            name = context.getString(R.string.plan_name_strength_base),
            lastAppliedAt = 1715600000000,
            currentIndex = 1,
            motions = listOf(
                PlanMotionState(entryId = 1, motionId = 1, title = context.getString(R.string.motion_name_snatch), dayIndex = 1, sets = 5, repsPerSet = 2, intensity = 0.82, orderIndex = 1),
                PlanMotionState(entryId = 2, motionId = 6, title = context.getString(R.string.body_front_squat), dayIndex = 2, sets = 5, repsPerSet = 3, intensity = 0.78, orderIndex = 1),
                PlanMotionState(entryId = 3, motionId = 3, title = context.getString(R.string.motion_name_snatch_pull), dayIndex = 3, sets = 4, repsPerSet = 3, intensity = 0.9, orderIndex = 1)
            )
        ),
        TrainingPlanState(
            id = 2,
            name = context.getString(R.string.plan_name_competition_peak),
            lastAppliedAt = 1715800000000,
            currentIndex = 1,
            motions = listOf(
                PlanMotionState(entryId = 1, motionId = 2, title = context.getString(R.string.motion_name_clean_and_jerk), dayIndex = 1, sets = 6, repsPerSet = 1, intensity = 0.92, orderIndex = 1),
                PlanMotionState(entryId = 2, motionId = 1, title = context.getString(R.string.motion_name_snatch), dayIndex = 2, sets = 5, repsPerSet = 1, intensity = 0.88, orderIndex = 1),
                PlanMotionState(entryId = 3, motionId = 5, title = context.getString(R.string.motion_name_push_press), dayIndex = 3, sets = 4, repsPerSet = 3, intensity = 0.8, orderIndex = 1)
            )
        ),
        TrainingPlanState(
            id = 3,
            name = context.getString(R.string.plan_name_technique_cycle),
            lastAppliedAt = 1715400000000,
            currentIndex = 1,
            motions = listOf(
                PlanMotionState(entryId = 1, motionId = 1, title = context.getString(R.string.motion_name_snatch), dayIndex = 1, sets = 6, repsPerSet = 2, intensity = 0.74, orderIndex = 1),
                PlanMotionState(entryId = 2, motionId = 2, title = context.getString(R.string.motion_name_clean_and_jerk), dayIndex = 2, sets = 5, repsPerSet = 2, intensity = 0.76, orderIndex = 1),
                PlanMotionState(entryId = 3, motionId = 4, title = context.getString(R.string.motion_name_clean_pull), dayIndex = 3, sets = 4, repsPerSet = 3, intensity = 0.84, orderIndex = 1)
            )
        ),
        TrainingPlanState(
            id = 4,
            name = context.getString(R.string.plan_name_pull_volume_block),
            lastAppliedAt = 1715200000000,
            currentIndex = 1,
            motions = listOf(
                PlanMotionState(entryId = 1, motionId = 4, title = context.getString(R.string.motion_name_clean_pull), dayIndex = 1, sets = 5, repsPerSet = 3, intensity = 0.86, orderIndex = 1),
                PlanMotionState(entryId = 2, motionId = 7, title = context.getString(R.string.body_back_squat), dayIndex = 2, sets = 5, repsPerSet = 5, intensity = 0.8, orderIndex = 1),
                PlanMotionState(entryId = 3, motionId = 5, title = context.getString(R.string.motion_name_push_press), dayIndex = 3, sets = 4, repsPerSet = 4, intensity = 0.72, orderIndex = 1)
            )
        )
    )

    return TrainingPlanSeedCatalog(
        availableMotions = availableMotions,
        debugPlans = debugPlans,
        debugCurrentPlanId = 2
    )
}

