package com.potato.liftinsight.plan.data

import android.content.Context
import com.potato.liftinsight.R
import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.TrainingPlanState

data class TrainingPlanSeedCatalog(
    val availableMotions: List<AvailableMotionState>,
    val samplePlans: List<TrainingPlanState>,
    val sampleCurrentPlanId: Int
)

fun defaultTrainingPlanSeedCatalog(context: Context): TrainingPlanSeedCatalog {
    val availableMotions = listOf(
        AvailableMotionState(
            id = 1,
            title = context.getString(R.string.seed_motion_snatch)
        ),
        AvailableMotionState(
            id = 2,
            title = context.getString(R.string.seed_motion_clean_and_jerk)
        ),
        AvailableMotionState(
            id = 3,
            title = context.getString(R.string.seed_motion_snatch_pull)
        ),
        AvailableMotionState(
            id = 4,
            title = context.getString(R.string.seed_motion_clean_pull)
        ),
        AvailableMotionState(
            id = 5,
            title = context.getString(R.string.seed_motion_push_press)
        ),
        AvailableMotionState(
            id = 6,
            title = context.getString(R.string.seed_motion_front_squat)
        ),
        AvailableMotionState(
            id = 7,
            title = context.getString(R.string.seed_motion_back_squat)
        ),
        AvailableMotionState(
            id = 8,
            title = context.getString(R.string.seed_motion_bench_press)
        ),
        AvailableMotionState(
            id = 9,
            title = context.getString(R.string.seed_motion_deadlift)
        ),
        AvailableMotionState(
            id = 10,
            title = context.getString(R.string.seed_motion_warm_up)
        ),
        AvailableMotionState(
            id = 11,
            title = context.getString(R.string.seed_motion_cool_down)
        )
    )

    val warmUp = context.getString(R.string.seed_motion_warm_up)
    val coolDown = context.getString(R.string.seed_motion_cool_down)
    val snatch = context.getString(R.string.seed_motion_snatch)
    val cleanAndJerk = context.getString(R.string.seed_motion_clean_and_jerk)
    val snatchPull = context.getString(R.string.seed_motion_snatch_pull)
    val cleanPull = context.getString(R.string.seed_motion_clean_pull)
    val pushPress = context.getString(R.string.seed_motion_push_press)
    val frontSquat = context.getString(R.string.seed_motion_front_squat)
    val backSquat = context.getString(R.string.seed_motion_back_squat)
    val benchPress = context.getString(R.string.seed_motion_bench_press)
    val deadlift = context.getString(R.string.seed_motion_deadlift)

    val samplePlans = listOf(
        TrainingPlanState(
            id = 1,
            name = context.getString(R.string.seed_plan_temporary),
            lastAppliedAt = 1715600000000,
            currentIndex = 1,
            cyclePeriod = 1,
            motions = listOf(
                PlanMotionState(
                    entryId = 1, motionId = 10, title = warmUp,
                    dayIndex = 1, sets = 1, repsPerSet = 1, intensity = 0.0, orderIndex = 1
                )
            )
        ),
        TrainingPlanState(
            id = 2,
            name = context.getString(R.string.seed_plan_daily),
            lastAppliedAt = 1715800000000,
            currentIndex = 1,
            cyclePeriod = 7,
            motions = listOf(
                // Day 1: warm-up, snatch, front squat, cool-down
                PlanMotionState(
                    entryId = 1, motionId = 10, title = warmUp,
                    dayIndex = 1, sets = 2, repsPerSet = 10, intensity = 0.0, orderIndex = 1
                ),
                PlanMotionState(
                    entryId = 2, motionId = 1, title = snatch,
                    dayIndex = 1, sets = 5, repsPerSet = 2, intensity = 0.82, orderIndex = 2
                ),
                PlanMotionState(
                    entryId = 3, motionId = 6, title = frontSquat,
                    dayIndex = 1, sets = 5, repsPerSet = 3, intensity = 0.78, orderIndex = 3
                ),
                PlanMotionState(
                    entryId = 4, motionId = 11, title = coolDown,
                    dayIndex = 1, sets = 1, repsPerSet = 10, intensity = 0.0, orderIndex = 4
                ),
                // Day 2: warm-up, clean & jerk, push press, cool-down
                PlanMotionState(
                    entryId = 5, motionId = 10, title = warmUp,
                    dayIndex = 2, sets = 2, repsPerSet = 10, intensity = 0.0, orderIndex = 1
                ),
                PlanMotionState(
                    entryId = 6, motionId = 2, title = cleanAndJerk,
                    dayIndex = 2, sets = 5, repsPerSet = 2, intensity = 0.84, orderIndex = 2
                ),
                PlanMotionState(
                    entryId = 7, motionId = 5, title = pushPress,
                    dayIndex = 2, sets = 4, repsPerSet = 3, intensity = 0.76, orderIndex = 3
                ),
                PlanMotionState(
                    entryId = 8, motionId = 11, title = coolDown,
                    dayIndex = 2, sets = 1, repsPerSet = 10, intensity = 0.0, orderIndex = 4
                ),
                // Day 3: warm-up, snatch pull, deadlift, cool-down
                PlanMotionState(
                    entryId = 9, motionId = 10, title = warmUp,
                    dayIndex = 3, sets = 2, repsPerSet = 10, intensity = 0.0, orderIndex = 1
                ),
                PlanMotionState(
                    entryId = 10, motionId = 3, title = snatchPull,
                    dayIndex = 3, sets = 4, repsPerSet = 3, intensity = 0.88, orderIndex = 2
                ),
                PlanMotionState(
                    entryId = 11, motionId = 9, title = deadlift,
                    dayIndex = 3, sets = 4, repsPerSet = 5, intensity = 0.82, orderIndex = 3
                ),
                PlanMotionState(
                    entryId = 12, motionId = 11, title = coolDown,
                    dayIndex = 3, sets = 1, repsPerSet = 10, intensity = 0.0, orderIndex = 4
                ),
                // Day 4: warm-up, clean pull, back squat, cool-down
                PlanMotionState(
                    entryId = 13, motionId = 10, title = warmUp,
                    dayIndex = 4, sets = 2, repsPerSet = 10, intensity = 0.0, orderIndex = 1
                ),
                PlanMotionState(
                    entryId = 14, motionId = 4, title = cleanPull,
                    dayIndex = 4, sets = 4, repsPerSet = 3, intensity = 0.86, orderIndex = 2
                ),
                PlanMotionState(
                    entryId = 15, motionId = 7, title = backSquat,
                    dayIndex = 4, sets = 5, repsPerSet = 5, intensity = 0.80, orderIndex = 3
                ),
                PlanMotionState(
                    entryId = 16, motionId = 11, title = coolDown,
                    dayIndex = 4, sets = 1, repsPerSet = 10, intensity = 0.0, orderIndex = 4
                ),
                // Day 5: warm-up, bench press, front squat, cool-down
                PlanMotionState(
                    entryId = 17, motionId = 10, title = warmUp,
                    dayIndex = 5, sets = 2, repsPerSet = 10, intensity = 0.0, orderIndex = 1
                ),
                PlanMotionState(
                    entryId = 18, motionId = 8, title = benchPress,
                    dayIndex = 5, sets = 4, repsPerSet = 8, intensity = 0.74, orderIndex = 2
                ),
                PlanMotionState(
                    entryId = 19, motionId = 6, title = frontSquat,
                    dayIndex = 5, sets = 4, repsPerSet = 3, intensity = 0.72, orderIndex = 3
                ),
                PlanMotionState(
                    entryId = 20, motionId = 11, title = coolDown,
                    dayIndex = 5, sets = 1, repsPerSet = 10, intensity = 0.0, orderIndex = 4
                ),
                // Day 6: warm-up, clean & jerk, deadlift, cool-down
                PlanMotionState(
                    entryId = 21, motionId = 10, title = warmUp,
                    dayIndex = 6, sets = 2, repsPerSet = 10, intensity = 0.0, orderIndex = 1
                ),
                PlanMotionState(
                    entryId = 22, motionId = 2, title = cleanAndJerk,
                    dayIndex = 6, sets = 5, repsPerSet = 2, intensity = 0.86, orderIndex = 2
                ),
                PlanMotionState(
                    entryId = 23, motionId = 9, title = deadlift,
                    dayIndex = 6, sets = 4, repsPerSet = 3, intensity = 0.84, orderIndex = 3
                ),
                PlanMotionState(
                    entryId = 24, motionId = 11, title = coolDown,
                    dayIndex = 6, sets = 1, repsPerSet = 10, intensity = 0.0, orderIndex = 4
                ),
                // Day 7: warm-up, snatch, back squat, cool-down
                PlanMotionState(
                    entryId = 25, motionId = 10, title = warmUp,
                    dayIndex = 7, sets = 2, repsPerSet = 10, intensity = 0.0, orderIndex = 1
                ),
                PlanMotionState(
                    entryId = 26, motionId = 1, title = snatch,
                    dayIndex = 7, sets = 4, repsPerSet = 2, intensity = 0.80, orderIndex = 2
                ),
                PlanMotionState(
                    entryId = 27, motionId = 7, title = backSquat,
                    dayIndex = 7, sets = 5, repsPerSet = 3, intensity = 0.82, orderIndex = 3
                ),
                PlanMotionState(
                    entryId = 28, motionId = 11, title = coolDown,
                    dayIndex = 7, sets = 1, repsPerSet = 10, intensity = 0.0, orderIndex = 4
                )
            )
        )
    )

    return TrainingPlanSeedCatalog(
        availableMotions = availableMotions,
        samplePlans = samplePlans,
        sampleCurrentPlanId = 2
    )
}
