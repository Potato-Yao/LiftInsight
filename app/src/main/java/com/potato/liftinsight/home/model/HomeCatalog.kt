package com.potato.liftinsight.home.model

import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.TrainingPlanState

data class HomeLabels(
    val strengthBaseName: String,
    val competitionPeakName: String,
    val techniqueCycleName: String,
    val pullVolumeBlockName: String,
    val snatchTitle: String,
    val cleanAndJerkTitle: String,
    val snatchPullTitle: String,
    val cleanPullTitle: String,
    val pushPressTitle: String,
    val frontSquatTitle: String,
    val backSquatTitle: String
)

data class HomeCatalog(
    val availableMotions: List<AvailableMotionState>,
    val initialTrainingPlans: List<TrainingPlanState>,
    val initialCurrentPlanId: Int
)

fun defaultHomeCatalog(labels: HomeLabels): HomeCatalog {
    val availableMotions = listOf(
        AvailableMotionState(id = 1, title = labels.snatchTitle, defaultSets = 5, defaultRepsPerSet = 2),
        AvailableMotionState(id = 2, title = labels.cleanAndJerkTitle, defaultSets = 5, defaultRepsPerSet = 1),
        AvailableMotionState(id = 3, title = labels.snatchPullTitle, defaultSets = 4, defaultRepsPerSet = 3),
        AvailableMotionState(id = 4, title = labels.cleanPullTitle, defaultSets = 4, defaultRepsPerSet = 3),
        AvailableMotionState(id = 5, title = labels.pushPressTitle, defaultSets = 4, defaultRepsPerSet = 4),
        AvailableMotionState(id = 6, title = labels.frontSquatTitle, defaultSets = 5, defaultRepsPerSet = 3),
        AvailableMotionState(id = 7, title = labels.backSquatTitle, defaultSets = 5, defaultRepsPerSet = 5)
    )

    val initialTrainingPlans = listOf(
        TrainingPlanState(
            id = 1,
            name = labels.strengthBaseName,
            lastAppliedAt = 1715600000000,
            motions = listOf(
                PlanMotionState(entryId = 1, motionId = 1, title = labels.snatchTitle, sets = 5, repsPerSet = 2),
                PlanMotionState(entryId = 2, motionId = 6, title = labels.frontSquatTitle, sets = 5, repsPerSet = 3),
                PlanMotionState(entryId = 3, motionId = 3, title = labels.snatchPullTitle, sets = 4, repsPerSet = 3)
            )
        ),
        TrainingPlanState(
            id = 2,
            name = labels.competitionPeakName,
            lastAppliedAt = 1715800000000,
            motions = listOf(
                PlanMotionState(entryId = 1, motionId = 2, title = labels.cleanAndJerkTitle, sets = 6, repsPerSet = 1),
                PlanMotionState(entryId = 2, motionId = 1, title = labels.snatchTitle, sets = 5, repsPerSet = 1),
                PlanMotionState(entryId = 3, motionId = 5, title = labels.pushPressTitle, sets = 4, repsPerSet = 3)
            )
        ),
        TrainingPlanState(
            id = 3,
            name = labels.techniqueCycleName,
            lastAppliedAt = 1715400000000,
            motions = listOf(
                PlanMotionState(entryId = 1, motionId = 1, title = labels.snatchTitle, sets = 6, repsPerSet = 2),
                PlanMotionState(entryId = 2, motionId = 2, title = labels.cleanAndJerkTitle, sets = 5, repsPerSet = 2),
                PlanMotionState(entryId = 3, motionId = 4, title = labels.cleanPullTitle, sets = 4, repsPerSet = 3)
            )
        ),
        TrainingPlanState(
            id = 4,
            name = labels.pullVolumeBlockName,
            lastAppliedAt = 1715200000000,
            motions = listOf(
                PlanMotionState(entryId = 1, motionId = 4, title = labels.cleanPullTitle, sets = 5, repsPerSet = 3),
                PlanMotionState(entryId = 2, motionId = 7, title = labels.backSquatTitle, sets = 5, repsPerSet = 5),
                PlanMotionState(entryId = 3, motionId = 5, title = labels.pushPressTitle, sets = 4, repsPerSet = 4)
            )
        )
    )

    return HomeCatalog(
        availableMotions = availableMotions,
        initialTrainingPlans = initialTrainingPlans,
        initialCurrentPlanId = 2
    )
}

