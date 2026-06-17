package com.potato.liftinsight.plan

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.potato.liftinsight.R
import com.potato.liftinsight.camera.CameraScreen
import com.potato.liftinsight.motion.MotionScreen
import com.potato.liftinsight.motion.controller.MotionController
import com.potato.liftinsight.motion.model.MotionState
import com.potato.liftinsight.plan.controller.PlanController
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.PlanState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.plan.model.WorkoutSessionFeeling
import com.potato.liftinsight.plan.model.todaysPlanMotions
import com.potato.liftinsight.plan.model.trainingPlan
import com.potato.liftinsight.plan.route.PlanRoute
import com.potato.liftinsight.plan.route.planRouteDepth
import com.potato.liftinsight.ui.theme.LiftInsightMotion
import kotlinx.coroutines.launch

@Composable
internal fun PlanTabHost(
    planState: PlanState,
    onPlanStateChange: (PlanState) -> Unit,
    planController: PlanController,
    motionState: MotionState,
    onMotionStateChange: (MotionState) -> Unit,
    motionController: MotionController,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    val shouldHandlePlanBack =
        planState.planRoute != PlanRoute.Overview &&
            planState.planIdPendingDelete == null &&
            planState.motionPendingDelete == null

    BackHandler(enabled = shouldHandlePlanBack) {
        coroutineScope.launch {
            onPlanStateChange(planController.handlePlanBack(planState))
        }
    }

    AnimatedContent(
        targetState = planState.planRoute,
        transitionSpec = {
            val direction = if (
                planRouteDepth(targetState) >= planRouteDepth(initialState)
            ) {
                1
            } else {
                -1
            }

            (fadeIn(
                animationSpec = tween(
                    durationMillis = LiftInsightMotion.MediumDuration,
                    delayMillis = 40,
                    easing = LiftInsightMotion.EnterEasing
                )
            ) +
                slideInHorizontally(
                    animationSpec = tween(
                        durationMillis = LiftInsightMotion.LongDuration,
                        easing = LiftInsightMotion.EnterEasing
                    ),
                    initialOffsetX = { fullWidth -> direction * (fullWidth / 10) }
                ) +
                slideInVertically(
                    animationSpec = tween(
                        durationMillis = LiftInsightMotion.MediumDuration,
                        easing = LiftInsightMotion.EnterEasing
                    ),
                    initialOffsetY = { fullHeight -> fullHeight / 48 }
                )) togetherWith
                (fadeOut(
                    animationSpec = tween(
                        durationMillis = LiftInsightMotion.ShortDuration,
                        easing = LiftInsightMotion.ExitEasing
                    )
                ) +
                    slideOutHorizontally(
                        animationSpec = tween(
                            durationMillis = LiftInsightMotion.ShortDuration,
                            easing = LiftInsightMotion.ExitEasing
                        ),
                        targetOffsetX = { fullWidth -> -direction * (fullWidth / 12) }
                    ) +
                    slideOutVertically(
                        animationSpec = tween(
                            durationMillis = LiftInsightMotion.ShortDuration,
                            easing = LiftInsightMotion.ExitEasing
                        ),
                        targetOffsetY = { fullHeight -> -(fullHeight / 56) }
                    ))
        },
        label = "planPanels",
        modifier = modifier
    ) { route ->
        when (route) {
            PlanRoute.Overview -> {
                val plan = trainingPlan(planState.trainingPlans, planState.currentPlanId)

                PlanScreen(
                    currentPlan = plan,
                    workoutProgress = planState.workoutProgress,
                    workoutSession = planState.workoutSession,
                    todayMotions = plan?.let(::todaysPlanMotions).orEmpty(),
                    mergedTodayTargets = planState.mergedTodayTargets,
                    onEditPlan = { onPlanStateChange(planController.showPlanList(planState)) },
                    onStartNextWorkoutSet = {
                        coroutineScope.launch {
                            onPlanStateChange(planController.startNextWorkoutSet(planState))
                        }
                    },
                    onSkipWorkoutSet = {
                        coroutineScope.launch {
                            onPlanStateChange(planController.skipWorkoutSet(planState))
                        }
                    },
                    onFinishCurrentWorkoutSet = { performance ->
                        coroutineScope.launch {
                            onPlanStateChange(
                                planController.finishCurrentWorkoutSet(planState, performance)
                            )
                        }
                    },
                    onCameraClick = {
                        onPlanStateChange(planController.openCamera(planState))
                    },
                    pendingCameraVideo = planState.cameraVideoName,
                    onCameraVideoCleared = {
                        onPlanStateChange(planController.clearCameraVideo(planState))
                    },
                    onWorkoutFeelingSubmit = { feeling ->
                        coroutineScope.launch {
                            onPlanStateChange(
                                planController.submitWorkoutFeeling(planState, feeling)
                            )
                        }
                    },
                    modifier = Modifier.padding(contentPadding)
                )
            }

            PlanRoute.List -> {
                PlanPickerScreen(
                    plans = planState.trainingPlans,
                    currentPlanId = planState.currentPlanId,
                    onBack = {
                        coroutineScope.launch {
                            onPlanStateChange(planController.handlePlanBack(planState))
                        }
                    },
                    onSelectPlan = { planId ->
                        coroutineScope.launch {
                            onPlanStateChange(planController.selectPlan(planState, planId))
                        }
                    },
                    onSelectPlanDay = { planId, dayIndex ->
                        coroutineScope.launch {
                            onPlanStateChange(
                                planController.updatePlanCurrentDay(planState, planId, dayIndex)
                            )
                        }
                    },
                    onEditPlan = { planId ->
                        onPlanStateChange(planController.showPlanDetail(planState, planId))
                    },
                    modifier = Modifier.padding(contentPadding)
                )
            }

            PlanRoute.Editor -> {
                val editor = planState.planEditor

                if (editor == null) {
                    PlanPickerScreen(
                        plans = planState.trainingPlans,
                        currentPlanId = planState.currentPlanId,
                        onBack = {
                            coroutineScope.launch {
                                onPlanStateChange(planController.handlePlanBack(planState))
                            }
                        },
                        onSelectPlan = { planId ->
                            coroutineScope.launch {
                                onPlanStateChange(planController.selectPlan(planState, planId))
                            }
                        },
                        onSelectPlanDay = { planId, dayIndex ->
                            coroutineScope.launch {
                                onPlanStateChange(
                                    planController.updatePlanCurrentDay(planState, planId, dayIndex)
                                )
                            }
                        },
                        onEditPlan = { planId ->
                            onPlanStateChange(planController.showPlanDetail(planState, planId))
                        },
                        modifier = Modifier.padding(contentPadding)
                    )
                } else {
                    PlanEditorScreen(
                        editor = editor,
                        onBack = {
                            coroutineScope.launch {
                                onPlanStateChange(planController.handlePlanBack(planState))
                            }
                        },
                        onUpdateTitle = { newName ->
                            coroutineScope.launch {
                                onPlanStateChange(
                                    planController.updatePlanEditorTitle(planState, newName)
                                )
                            }
                        },
                        onUpdateCyclePeriod = { cyclePeriod ->
                            coroutineScope.launch {
                                onPlanStateChange(
                                    planController.updatePlanEditorCyclePeriod(planState, cyclePeriod)
                                )
                            }
                        },
                        onSelectDay = { dayIndex ->
                            onPlanStateChange(
                                planController.selectPlanEditorDay(planState, dayIndex)
                            )
                        },
                        onMoveMotionUp = { motionEntryId ->
                            coroutineScope.launch {
                                onPlanStateChange(
                                    planController.movePlanMotion(planState, motionEntryId, -1)
                                )
                            }
                        },
                        onMoveMotionDown = { motionEntryId ->
                            coroutineScope.launch {
                                onPlanStateChange(
                                    planController.movePlanMotion(planState, motionEntryId, 1)
                                )
                            }
                        },
                        onEditMotion = { motionEntryId ->
                            onPlanStateChange(
                                planController.showMotionDetail(planState, motionEntryId)
                            )
                        },
                        modifier = Modifier.padding(contentPadding)
                    )
                }
            }

            PlanRoute.MotionPicker -> {
                MotionScreen(
                    state = motionState,
                    onAddMotion = {
                        onMotionStateChange(motionController.openCreateMotion(motionState))
                    },
                    onEditMotion = { motionId ->
                        onMotionStateChange(motionController.openEditMotion(motionState, motionId))
                    },
                    onBackFromEditor = {
                        onMotionStateChange(motionController.closeEditor(motionState))
                    },
                    onMotionNameChange = { name ->
                        onMotionStateChange(motionController.updateEditorName(motionState, name))
                    },
                    onMotionTypeChange = { type ->
                        onMotionStateChange(motionController.updateEditorType(motionState, type))
                    },
                    onSubmitMotion = {
                        coroutineScope.launch {
                            val result = motionController.submitMotion(motionState)
                            onMotionStateChange(result.state)
                            if (result.didChangeData) {
                                onPlanStateChange(planController.refreshState(planState))
                            }
                        }
                    },
                    onDeleteMotion = {
                        coroutineScope.launch {
                            val result = motionController.deleteMotion(motionState)
                            onMotionStateChange(result.state)
                            if (result.didChangeData) {
                                onPlanStateChange(planController.refreshState(planState))
                            }
                        }
                    },
                    selectionTitle = stringResource(R.string.plan_add_motion_dialog_title),
                    onBackFromSelection = {
                        onPlanStateChange(planController.closeAddMotionPicker(planState))
                    },
                    onSelectMotion = { motionId ->
                        val selectedMotion = planState.availableMotions.firstOrNull { motion ->
                            motion.id == motionId
                        }
                        if (selectedMotion != null) {
                            coroutineScope.launch {
                                onPlanStateChange(
                                    planController.addMotionToPlan(planState, selectedMotion)
                                )
                            }
                        }
                    },
                    modifier = Modifier.padding(contentPadding)
                )
            }

            PlanRoute.WorkoutMotionPicker -> {
                MotionScreen(
                    state = motionState,
                    onAddMotion = {
                        onMotionStateChange(motionController.openCreateMotion(motionState))
                    },
                    onEditMotion = { motionId ->
                        onMotionStateChange(motionController.openEditMotion(motionState, motionId))
                    },
                    onBackFromEditor = {
                        onMotionStateChange(motionController.closeEditor(motionState))
                    },
                    onMotionNameChange = { name ->
                        onMotionStateChange(motionController.updateEditorName(motionState, name))
                    },
                    onMotionTypeChange = { type ->
                        onMotionStateChange(motionController.updateEditorType(motionState, type))
                    },
                    onSubmitMotion = {
                        coroutineScope.launch {
                            val result = motionController.submitMotion(motionState)
                            onMotionStateChange(result.state)
                            if (result.didChangeData) {
                                onPlanStateChange(planController.refreshState(planState))
                            }
                        }
                    },
                    onDeleteMotion = {
                        coroutineScope.launch {
                            val result = motionController.deleteMotion(motionState)
                            onMotionStateChange(result.state)
                            if (result.didChangeData) {
                                onPlanStateChange(planController.refreshState(planState))
                            }
                        }
                    },
                    selectionTitle = stringResource(R.string.plan_add_motion_dialog_title),
                    onBackFromSelection = {
                        onPlanStateChange(planController.closeWorkoutMotionPicker(planState))
                    },
                    onSelectMotion = { motionId ->
                        val selectedMotion = planState.availableMotions.firstOrNull { motion -> motion.id == motionId }
                        if (selectedMotion != null) {
                            coroutineScope.launch {
                                onPlanStateChange(planController.insertMotionIntoWorkout(planState, selectedMotion))
                            }
                        }
                    },
                    modifier = Modifier.padding(contentPadding)
                )
            }

            is PlanRoute.Camera -> {
                CameraScreen(
                    motionTitle = route.motionTitle,
                    motionId = route.motionId,
                    setIndex = route.setIndex,
                    onRecordingFinished = { videoName ->
                        coroutineScope.launch {
                            onPlanStateChange(
                                planController.closeCameraWithVideo(planState, videoName)
                            )
                        }
                    },
                    onBack = {
                        coroutineScope.launch {
                            onPlanStateChange(planController.closeCamera(planState))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            is PlanRoute.Motion -> {
                val editor = planState.planEditor
                val motion = editor?.motions?.firstOrNull { planMotion ->
                    planMotion.entryId == route.motionEntryId
                }

                if (editor == null || motion == null) {
                    PlanPickerScreen(
                        plans = planState.trainingPlans,
                        currentPlanId = planState.currentPlanId,
                        onBack = {
                            coroutineScope.launch {
                                onPlanStateChange(planController.handlePlanBack(planState))
                            }
                        },
                        onSelectPlan = { planId ->
                            coroutineScope.launch {
                                onPlanStateChange(planController.selectPlan(planState, planId))
                            }
                        },
                        onSelectPlanDay = { planId, dayIndex ->
                            coroutineScope.launch {
                                onPlanStateChange(
                                    planController.updatePlanCurrentDay(planState, planId, dayIndex)
                                )
                            }
                        },
                        onEditPlan = { planId ->
                            onPlanStateChange(planController.showPlanDetail(planState, planId))
                        },
                        modifier = Modifier.padding(contentPadding)
                    )
                } else {
                    MotionDetailScreen(
                        planName = editor.title.ifBlank {
                            stringResource(R.string.plan_title_placeholder)
                        },
                        motion = motion,
                        onBack = {
                            coroutineScope.launch {
                                onPlanStateChange(planController.handlePlanBack(planState))
                            }
                        },
                        onSubmitMotion = { sets, repsPerSet, weight ->
                            coroutineScope.launch {
                                var state = planState
                                state = planController.updateMotionSets(
                                    state = state,
                                    motionEntryId = route.motionEntryId,
                                    sets = sets
                                )
                                state = planController.updateMotionRepsPerSet(
                                    state = state,
                                    motionEntryId = route.motionEntryId,
                                    repsPerSet = repsPerSet
                                )
                                state = planController.updateMotionWeight(
                                    state = state,
                                    motionEntryId = route.motionEntryId,
                                    weight = weight
                                )
                                state = planController.handlePlanBack(state)
                                onPlanStateChange(state)
                            }
                        },
                        onDeleteMotion = {
                            onPlanStateChange(
                                planController.requestMotionDeletion(planState, route.motionEntryId)
                            )
                        },
                        modifier = Modifier.padding(contentPadding)
                    )
                }
            }
        }
    }

    PlanTabDialogs(
        planState = planState,
        onPlanStateChange = onPlanStateChange,
        planController = planController,
        coroutineScope = coroutineScope
    )
}

@Composable
internal fun PlanTabFloatingActionButton(
    planState: PlanState,
    onPlanStateChange: (PlanState) -> Unit,
    planController: PlanController,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    val editor = planState.planEditor

    when (planState.planRoute) {
        PlanRoute.Overview -> {
            PlanOverviewActionButtons(
                canStartWorkout = planState.trainingPlans.any { plan -> plan.id == planState.currentPlanId } &&
                    trainingPlan(planState.trainingPlans, planState.currentPlanId)?.let(::todaysPlanMotions).orEmpty().isNotEmpty(),
                workoutFinished = planState.workoutProgress?.isFinished == true,
                isWorkoutGoing = planState.workoutSession.isWorkoutGoing,
                isWorkoutPaused = planState.workoutSession.isPaused,
                isInBreak = planState.workoutProgress != null && planState.workoutProgress.activeSetIndex == null && planState.workoutSession.isWorkoutGoing && !planState.workoutSession.isPaused && !planState.workoutProgress.isFinished,
                onStartWorkout = {
                    coroutineScope.launch {
                        onPlanStateChange(planController.startWorkout(planState))
                    }
                },
                onToggleWorkoutPause = {
                    coroutineScope.launch {
                        onPlanStateChange(planController.toggleWorkoutPause(planState))
                    }
                },
                onRequestWorkoutStop = {
                    onPlanStateChange(planController.requestWorkoutStop(planState))
                },
                onOpenWorkoutMotionPicker = {
                    onPlanStateChange(planController.openWorkoutMotionPicker(planState))
                }
            )
        }

        PlanRoute.List -> {
            FloatingActionButton(onClick = {
                onPlanStateChange(planController.createPlan(planState))
            }) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.plan_add_plan_content_description)
                )
            }
        }

        PlanRoute.Editor -> {
            if (editor != null) {
                PlanEditorActionButtons(
                    showSubmitButton = editor.isNewPlan,
                    onSubmitPlan = {
                        coroutineScope.launch {
                            onPlanStateChange(planController.submitPlanEditor(planState))
                        }
                    },
                    onOpenAddMotionPicker = {
                        onPlanStateChange(planController.openAddMotionPicker(planState))
                    },
                    onRequestDeletePlan = editor.planId?.let { planId ->
                        {
                            onPlanStateChange(planController.requestPlanDeletion(planState, planId))
                        }
                    }
                )
            }
        }

        PlanRoute.MotionPicker -> Unit

        PlanRoute.WorkoutMotionPicker -> Unit

        is PlanRoute.Motion -> Unit

        is PlanRoute.Camera -> Unit
    }
}

@Composable
private fun PlanTabDialogs(
    planState: PlanState,
    onPlanStateChange: (PlanState) -> Unit,
    planController: PlanController,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    val pendingPlanDelete = planState.planIdPendingDelete?.let { planId ->
        trainingPlan(planState.trainingPlans, planId)
    }
    if (pendingPlanDelete != null) {
        AlertDialog(
            onDismissRequest = {
                onPlanStateChange(planController.cancelPlanDeletion(planState))
            },
            title = {
                Text(text = stringResource(R.string.plan_delete_dialog_title))
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.plan_delete_dialog_message,
                        pendingPlanDelete.name
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        onPlanStateChange(planController.confirmPlanDeletion(planState))
                    }
                }) {
                    Text(text = stringResource(R.string.plan_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onPlanStateChange(planController.cancelPlanDeletion(planState))
                }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    val pendingMotionDelete = planState.motionPendingDelete
    if (pendingMotionDelete != null) {
        val editor = planState.planEditor
        val motion = editor?.motions?.firstOrNull { planMotion ->
            planMotion.entryId == pendingMotionDelete.motionEntryId
        }

        if (editor != null && motion != null) {
            AlertDialog(
                onDismissRequest = {
                    onPlanStateChange(planController.cancelMotionDeletion(planState))
                },
                title = {
                    Text(text = stringResource(R.string.motion_delete_dialog_title))
                },
                text = {
                    Text(
                        text = stringResource(
                            R.string.motion_delete_dialog_message,
                            motion.title,
                            editor.title.ifBlank {
                                stringResource(R.string.plan_title_placeholder)
                            }
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            onPlanStateChange(planController.confirmMotionDeletion(planState))
                        }
                    }) {
                        Text(text = stringResource(R.string.plan_delete_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        onPlanStateChange(planController.cancelMotionDeletion(planState))
                    }) {
                        Text(text = stringResource(R.string.common_cancel))
                    }
                }
            )
        }
    }

    if (planState.workoutStopPendingConfirmation) {
        AlertDialog(
            onDismissRequest = {
                onPlanStateChange(planController.dismissWorkoutStop(planState))
            },
            title = {
                Text(text = stringResource(R.string.plan_workout_stop_dialog_title))
            },
            text = {
                Text(text = stringResource(R.string.plan_workout_stop_dialog_message))
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        onPlanStateChange(planController.confirmWorkoutStop(planState))
                    }
                }) {
                    Text(text = stringResource(R.string.plan_workout_stop_action))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onPlanStateChange(planController.dismissWorkoutStop(planState))
                }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun PlanOverviewActionButtons(
    canStartWorkout: Boolean,
    workoutFinished: Boolean,
    isWorkoutGoing: Boolean,
    isWorkoutPaused: Boolean,
    isInBreak: Boolean,
    onStartWorkout: () -> Unit,
    onToggleWorkoutPause: () -> Unit,
    onRequestWorkoutStop: () -> Unit,
    onOpenWorkoutMotionPicker: () -> Unit
) {
    if (workoutFinished) {
        return
    }

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isWorkoutGoing) {
            if (isInBreak) {
                SmallFloatingActionButton(onClick = onOpenWorkoutMotionPicker) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.plan_workout_add_motion_content_description)
                    )
                }
            }

            SmallFloatingActionButton(
                onClick = onRequestWorkoutStop,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = stringResource(R.string.plan_workout_stop_content_description)
                )
            }

            FloatingActionButton(onClick = onToggleWorkoutPause) {
                Icon(
                    imageVector = if (isWorkoutPaused) {
                        Icons.Rounded.PlayArrow
                    } else {
                        Icons.Rounded.Pause
                    },
                    contentDescription = if (isWorkoutPaused) {
                        stringResource(R.string.plan_workout_resume_content_description)
                    } else {
                        stringResource(R.string.plan_workout_pause_content_description)
                    }
                )
            }
        } else {
            val containerColor = if (canStartWorkout) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }

            val contentColor = if (canStartWorkout) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            FloatingActionButton(
                onClick = {
                    if (canStartWorkout) {
                        onStartWorkout()
                    }
                },
                containerColor = containerColor,
                contentColor = contentColor
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.plan_workout_start_content_description)
                )
            }
        }
    }
}

@Composable
private fun PlanEditorActionButtons(
    showSubmitButton: Boolean,
    onSubmitPlan: () -> Unit,
    onOpenAddMotionPicker: () -> Unit,
    onRequestDeletePlan: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showSubmitButton) {
            SmallFloatingActionButton(onClick = onSubmitPlan) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.plan_submit_plan_content_description)
                )
            }
        } else if (onRequestDeletePlan != null) {
            SmallFloatingActionButton(
                onClick = onRequestDeletePlan,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.plan_delete_plan_content_description)
                )
            }
        }

        FloatingActionButton(onClick = onOpenAddMotionPicker) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.plan_add_motion_content_description)
            )
        }
    }
}
