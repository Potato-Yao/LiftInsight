package com.potato.liftinsight.plan.route

import com.potato.liftinsight.plan.model.PlanMotionState

sealed interface PlanRoute {
    data object Overview : PlanRoute

    data object List : PlanRoute

    data object Editor : PlanRoute

    data object MotionPicker : PlanRoute

    data object WorkoutMotionPicker : PlanRoute

    data class Motion(val motionEntryId: Int) : PlanRoute

    data class Camera(
        val motionId: Int,
        val motionTitle: String,
        val setIndex: Int,
        val setsInMotion: Int,
        val expectedReps: Int,
        val expectedWeight: Double,
        val expectedIntensity: Double
    ) : PlanRoute
}

fun planRouteDepth(route: PlanRoute): Int {
    return when (route) {
        PlanRoute.Overview -> 0
        PlanRoute.List -> 1
        PlanRoute.Editor -> 2
        PlanRoute.MotionPicker -> 3
        PlanRoute.WorkoutMotionPicker -> 3
        is PlanRoute.Motion -> 3
        is PlanRoute.Camera -> 4
    }
}

fun isPlanRouteFullScreen(route: PlanRoute): Boolean {
    return route is PlanRoute.Camera
}

data class PlanEditorState(
    val planId: Int? = null,
    val title: String = "",
    val cyclePeriod: Int? = null,
    val currentIndex: Int = 1,
    val selectedDayIndex: Int? = null,
    val motions: List<PlanMotionState> = emptyList(),
    val nextTemporaryMotionEntryId: Int = -1,
    val titleError: Boolean = false,
    val cyclePeriodError: Boolean = false
) {
    val isNewPlan: Boolean
        get() = planId == null
}

data class MotionDeleteTarget(
    val planId: Int,
    val motionEntryId: Int
)
