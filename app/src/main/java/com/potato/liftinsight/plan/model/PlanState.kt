package com.potato.liftinsight.plan.model

import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.plan.model.WorkoutProgressState
import com.potato.liftinsight.plan.model.WorkoutSessionState
import com.potato.liftinsight.plan.route.MotionDeleteTarget
import com.potato.liftinsight.plan.route.PlanEditorState
import com.potato.liftinsight.plan.route.PlanRoute

data class PlanState(
    val availableMotions: List<AvailableMotionState>,
    val trainingPlans: List<TrainingPlanState>,
    val currentPlanId: Int,
    val workoutProgress: WorkoutProgressState? = null,
    val workoutSession: WorkoutSessionState = WorkoutSessionState(),
    val planRoute: PlanRoute = PlanRoute.Overview,
    val planIdPendingDelete: Int? = null,
    val motionPendingDelete: MotionDeleteTarget? = null,
    val planEditor: PlanEditorState? = null,
    val workoutStopPendingConfirmation: Boolean = false,
    val cameraVideoName: String? = null
)
