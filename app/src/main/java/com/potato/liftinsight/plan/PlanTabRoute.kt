package com.potato.liftinsight.plan

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.potato.liftinsight.R
import com.potato.liftinsight.home.controller.HomeState
import com.potato.liftinsight.home.controller.PlanDestination
import com.potato.liftinsight.home.controller.planDestinationDepth
import com.potato.liftinsight.motion.MotionRouteActions
import com.potato.liftinsight.motion.MotionScreen
import com.potato.liftinsight.motion.model.MotionState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.plan.model.todaysPlanMotions
import com.potato.liftinsight.plan.model.trainingPlan
import com.potato.liftinsight.ui.theme.LiftInsightMotion

@Composable
internal fun PlanTabContent(
    state: HomeState,
    motionState: MotionState,
    planActions: PlanRouteActions,
    motionActions: MotionRouteActions,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = state.planDestination,
        transitionSpec = {
            val direction = if (
                planDestinationDepth(targetState) >= planDestinationDepth(initialState)
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
    ) { destination ->
        when (destination) {
            PlanDestination.Overview -> {
                val plan = trainingPlan(state.trainingPlans, state.currentPlanId)

                PlanOverviewPanel(
                    currentPlan = plan,
                    workoutSession = state.workoutSession,
                    todayMotions = plan?.let(::todaysPlanMotions).orEmpty(),
                    onOpenPlanList = planActions.onOpenPlanList,
                    modifier = Modifier.padding(contentPadding)
                )
            }

            PlanDestination.List -> {
                PlanPickerPanel(
                    trainingPlans = state.trainingPlans,
                    currentPlanId = state.currentPlanId,
                    onBack = planActions.onBackInPlan,
                    onSelectPlan = planActions.onSelectPlan,
                    onSelectPlanDay = planActions.onSelectPlanDay,
                    onEditPlan = planActions.onOpenPlanDetail,
                    modifier = Modifier.padding(contentPadding)
                )
            }

            PlanDestination.Editor -> {
                val editor = state.planEditor

                if (editor == null) {
                    PlanPickerPanel(
                        trainingPlans = state.trainingPlans,
                        currentPlanId = state.currentPlanId,
                        onBack = planActions.onBackInPlan,
                        onSelectPlan = planActions.onSelectPlan,
                        onSelectPlanDay = planActions.onSelectPlanDay,
                        onEditPlan = planActions.onOpenPlanDetail,
                        modifier = Modifier.padding(contentPadding)
                    )
                } else {
                    PlanEditorScreen(
                        editor = editor,
                        onBack = planActions.onBackInPlan,
                        onUpdateTitle = planActions.onRenamePlan,
                        onUpdateCyclePeriod = planActions.onUpdatePlanCyclePeriod,
                        onSelectDay = planActions.onSelectPlanEditorDay,
                        onMoveMotionUp = planActions.onMoveMotionUp,
                        onMoveMotionDown = planActions.onMoveMotionDown,
                        onEditMotion = planActions.onOpenMotionDetail,
                        modifier = Modifier.padding(contentPadding)
                    )
                }
            }

            PlanDestination.MotionPicker -> {
                MotionScreen(
                    state = motionState,
                    onAddMotion = motionActions.onAddMotion,
                    onEditMotion = motionActions.onEditMotionLibraryEntry,
                    onBackFromEditor = motionActions.onBackFromMotionEditor,
                    onMotionNameChange = motionActions.onMotionNameChange,
                    onSubmitMotion = motionActions.onSubmitMotion,
                    onDeleteMotion = motionActions.onDeleteMotionFromLibrary,
                    selectionTitle = stringResource(R.string.plan_add_motion_dialog_title),
                    onBackFromSelection = planActions.onDismissAddMotionPicker,
                    onSelectMotion = { motionId ->
                        val selectedMotion = state.availableMotions.firstOrNull { motion ->
                            motion.id == motionId
                        }

                        if (selectedMotion != null) {
                            planActions.onAddMotionToPlan(selectedMotion)
                        }
                    },
                    modifier = Modifier.padding(contentPadding)
                )
            }

            is PlanDestination.Motion -> {
                val editor = state.planEditor
                val motion = editor?.motions?.firstOrNull { planMotion ->
                    planMotion.entryId == destination.motionEntryId
                }

                if (editor == null || motion == null) {
                    PlanPickerPanel(
                        trainingPlans = state.trainingPlans,
                        currentPlanId = state.currentPlanId,
                        onBack = planActions.onBackInPlan,
                        onSelectPlan = planActions.onSelectPlan,
                        onSelectPlanDay = planActions.onSelectPlanDay,
                        onEditPlan = planActions.onOpenPlanDetail,
                        modifier = Modifier.padding(contentPadding)
                    )
                } else {
                    MotionDetailScreen(
                        planName = editor.title.ifBlank {
                            stringResource(R.string.plan_title_placeholder)
                        },
                        motion = motion,
                        onBack = planActions.onBackInPlan,
                        onDecreaseSets = {
                            planActions.onDecreaseMotionSets(destination.motionEntryId, motion.sets)
                        },
                        onIncreaseSets = {
                            planActions.onIncreaseMotionSets(destination.motionEntryId, motion.sets)
                        },
                        onDecreaseRepsPerSet = {
                            planActions.onDecreaseMotionReps(destination.motionEntryId, motion.repsPerSet)
                        },
                        onIncreaseRepsPerSet = {
                            planActions.onIncreaseMotionReps(destination.motionEntryId, motion.repsPerSet)
                        },
                        onUpdateWeight = { weight ->
                            planActions.onUpdateMotionWeight(destination.motionEntryId, weight)
                        },
                        onDeleteMotion = {
                            planActions.onRequestMotionDeletion(destination.motionEntryId)
                        },
                        modifier = Modifier.padding(contentPadding)
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlanTabFloatingActionButton(
    state: HomeState,
    actions: PlanRouteActions
) {
    val editor = state.planEditor

    when (state.planDestination) {
        PlanDestination.Overview -> {
            PlanOverviewActionButtons(
                isWorkoutGoing = state.workoutSession.isWorkoutGoing,
                isWorkoutPaused = state.workoutSession.isPaused,
                onStartWorkout = actions.onStartWorkout,
                onToggleWorkoutPause = actions.onToggleWorkoutPause,
                onRequestWorkoutStop = actions.onRequestWorkoutStop
            )
        }

        PlanDestination.List -> {
            FloatingActionButton(onClick = actions.onCreatePlan) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.plan_add_plan_content_description)
                )
            }
        }

        PlanDestination.Editor -> {
            if (editor != null) {
                PlanEditorActionButtons(
                    showSubmitButton = editor.isNewPlan,
                    onSubmitPlan = actions.onSubmitPlan,
                    onOpenAddMotionPicker = actions.onOpenAddMotionPicker,
                    onRequestDeletePlan = editor.planId?.let { planId ->
                        { actions.onRequestPlanDeletion(planId) }
                    }
                )
            }
        }

        PlanDestination.MotionPicker -> Unit

        is PlanDestination.Motion -> {
            val motionEntryId = state.planDestination.motionEntryId

            if (editor != null) {
                PlanMotionDetailActionButtons(
                    showSubmitButton = editor.isNewPlan,
                    onSubmitPlan = actions.onSubmitPlan,
                    onOpenAddMotionPicker = actions.onOpenAddMotionPicker,
                    onRequestDeleteMotion = {
                        actions.onRequestMotionDeletion(motionEntryId)
                    }
                )
            }
        }
    }
}

@Composable
internal fun PlanTabDialogs(
    state: HomeState,
    actions: PlanRouteActions
) {
    val pendingPlanDelete = state.planIdPendingDelete?.let { planId ->
        trainingPlan(state.trainingPlans, planId)
    }
    if (pendingPlanDelete != null) {
        AlertDialog(
            onDismissRequest = actions.onDismissPlanDeletion,
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
                TextButton(onClick = actions.onConfirmPlanDeletion) {
                    Text(text = stringResource(R.string.plan_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = actions.onDismissPlanDeletion) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    val pendingMotionDelete = state.motionPendingDelete
    if (pendingMotionDelete != null) {
        val editor = state.planEditor
        val motion = editor?.motions?.firstOrNull { planMotion ->
            planMotion.entryId == pendingMotionDelete.motionEntryId
        }

        if (editor != null && motion != null) {
            AlertDialog(
                onDismissRequest = actions.onDismissMotionDeletion,
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
                    TextButton(onClick = actions.onConfirmMotionDeletion) {
                        Text(text = stringResource(R.string.plan_delete_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = actions.onDismissMotionDeletion) {
                        Text(text = stringResource(R.string.common_cancel))
                    }
                }
            )
        }
    }

    if (state.workoutStopPendingConfirmation) {
        AlertDialog(
            onDismissRequest = actions.onDismissWorkoutStop,
            title = {
                Text(text = stringResource(R.string.plan_workout_stop_dialog_title))
            },
            text = {
                Text(text = stringResource(R.string.plan_workout_stop_dialog_message))
            },
            confirmButton = {
                TextButton(onClick = actions.onConfirmWorkoutStop) {
                    Text(text = stringResource(R.string.plan_workout_stop_action))
                }
            },
            dismissButton = {
                TextButton(onClick = actions.onDismissWorkoutStop) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun PlanOverviewActionButtons(
    isWorkoutGoing: Boolean,
    isWorkoutPaused: Boolean,
    onStartWorkout: () -> Unit,
    onToggleWorkoutPause: () -> Unit,
    onRequestWorkoutStop: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isWorkoutGoing) {
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
            FloatingActionButton(onClick = onStartWorkout) {
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

@Composable
private fun PlanMotionDetailActionButtons(
    showSubmitButton: Boolean,
    onSubmitPlan: () -> Unit,
    onOpenAddMotionPicker: () -> Unit,
    onRequestDeleteMotion: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SmallFloatingActionButton(
            onClick = onRequestDeleteMotion,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.motion_detail_delete_label)
            )
        }

        if (showSubmitButton) {
            SmallFloatingActionButton(onClick = onSubmitPlan) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.plan_submit_plan_content_description)
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

@Composable
private fun PlanOverviewPanel(
    currentPlan: TrainingPlanState?,
    workoutSession: com.potato.liftinsight.plan.model.WorkoutSessionState,
    todayMotions: List<PlanMotionState>,
    onOpenPlanList: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlanScreen(
        currentPlan = currentPlan,
        workoutSession = workoutSession,
        todayMotions = todayMotions,
        onEditPlan = onOpenPlanList,
        modifier = modifier
    )
}

@Composable
private fun PlanPickerPanel(
    trainingPlans: List<TrainingPlanState>,
    currentPlanId: Int,
    onBack: () -> Unit,
    onSelectPlan: (Int) -> Unit,
    onSelectPlanDay: (Int, Int) -> Unit,
    onEditPlan: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    PlanPickerScreen(
        plans = trainingPlans,
        currentPlanId = currentPlanId,
        onBack = onBack,
        onSelectPlan = onSelectPlan,
        onSelectPlanDay = onSelectPlanDay,
        onEditPlan = onEditPlan,
        modifier = modifier
    )
}


