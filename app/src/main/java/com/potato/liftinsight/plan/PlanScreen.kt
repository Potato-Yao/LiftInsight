package com.potato.liftinsight.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.potato.liftinsight.R
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.plan.model.WorkoutProgressState
import com.potato.liftinsight.plan.model.WorkoutSetFeeling
import com.potato.liftinsight.plan.model.WorkoutSetPerformanceInput
import com.potato.liftinsight.plan.model.WorkoutSetTargetState
import com.potato.liftinsight.plan.model.WorkoutSessionState
import com.potato.liftinsight.plan.model.completedWorkoutSetCount
import com.potato.liftinsight.plan.model.normalizedPlanCurrentIndex
import com.potato.liftinsight.plan.model.workoutCompletionPercentage
import com.potato.liftinsight.plan.model.workoutElapsedTimeMs
import com.potato.liftinsight.plan.model.workoutRemainingBreakMs
import com.potato.liftinsight.plan.model.workoutSetTargetsForDay
import com.potato.liftinsight.ui.theme.LiftInsightMotion
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun PlanScreen(
    currentPlan: TrainingPlanState?,
    workoutProgress: WorkoutProgressState?,
    workoutSession: WorkoutSessionState,
    todayMotions: List<PlanMotionState>,
    mergedTodayTargets: List<WorkoutSetTargetState> = emptyList(),
    onEditPlan: () -> Unit,
    onStartNextWorkoutSet: () -> Unit,
    onSkipWorkoutSet: () -> Unit,
    onFinishCurrentWorkoutSet: (WorkoutSetPerformanceInput) -> Unit,
    onCameraClick: (() -> Unit)? = null,
    pendingCameraVideo: String? = null,
    onCameraVideoCleared: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showContent by remember { mutableStateOf(false) }
    val workoutSetTargets = remember(todayMotions, mergedTodayTargets) {
        if (mergedTodayTargets.isNotEmpty()) mergedTodayTargets else workoutSetTargetsForDay(todayMotions)
    }
    val nowMs = rememberPlanCurrentTimeMs(
        shouldTick = workoutSession.isWorkoutGoing || workoutProgress?.isFinished == false
    )
    val sectionContent = remember(
        currentPlan,
        todayMotions,
        workoutProgress,
        workoutSetTargets,
        nowMs
    ) {
        buildPlanWorkoutSectionContent(
            currentPlan = currentPlan,
            todayMotions = todayMotions,
            workoutProgress = workoutProgress,
            workoutSetTargets = workoutSetTargets,
            nowMs = nowMs
        )
    }

    LaunchedEffect(Unit) {
        showContent = true
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 24.dp,
            top = 12.dp,
            end = 24.dp,
            bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "planTitle") {
            AnimatedVisibility(
                visible = showContent,
                enter = planSectionEnter(delayMillis = 0),
                exit = ExitTransition.None
            ) {
                Text(
                    text = stringResource(R.string.nav_plan),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        item(key = "currentPlan") {
            AnimatedVisibility(
                visible = showContent,
                enter = planSectionEnter(delayMillis = 50),
                exit = ExitTransition.None
            ) {
                CurrentPlanCard(
                    currentPlan = currentPlan,
                    onEditPlan = onEditPlan,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (workoutSession.isWorkoutGoing) {
            item(key = "workoutSession") {
                AnimatedVisibility(
                    visible = showContent,
                    enter = planSectionEnter(delayMillis = 85),
                    exit = ExitTransition.None
                ) {
                    WorkoutSessionCard(
                        workoutSession = workoutSession,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item(key = "todayTitle") {
            AnimatedVisibility(
                visible = showContent,
                enter = planSectionEnter(delayMillis = 100),
                exit = ExitTransition.None
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.plan_today_section_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = stringResource(R.string.plan_today_section_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item(key = "todayContent") {
            AnimatedVisibility(
                visible = showContent,
                enter = planSectionEnter(delayMillis = 135),
                exit = ExitTransition.None
            ) {
                TodayPlanSection(
                    content = sectionContent,
                    workoutSession = workoutSession,
                    onStartNextWorkoutSet = onStartNextWorkoutSet,
                    onSkipWorkoutSet = onSkipWorkoutSet,
                    onFinishCurrentWorkoutSet = onFinishCurrentWorkoutSet,
                    onCameraClick = onCameraClick,
                    pendingCameraVideo = pendingCameraVideo,
                    onCameraVideoCleared = onCameraVideoCleared,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private sealed interface PlanWorkoutSectionContent {
    data class Empty(
        val message: String
    ) : PlanWorkoutSectionContent

    data class Idle(
        val dayIndex: Int,
        val motions: List<PlanMotionState>
    ) : PlanWorkoutSectionContent

    data class Ready(
        val progress: WorkoutProgressState,
        val nextTarget: WorkoutSetTargetState,
        val remainingBreakMs: Long
    ) : PlanWorkoutSectionContent

    data class Active(
        val progress: WorkoutProgressState,
        val currentTarget: WorkoutSetTargetState
    ) : PlanWorkoutSectionContent

    data class Completed(
        val progress: WorkoutProgressState
    ) : PlanWorkoutSectionContent
}

private fun buildPlanWorkoutSectionContent(
    currentPlan: TrainingPlanState?,
    todayMotions: List<PlanMotionState>,
    workoutProgress: WorkoutProgressState?,
    workoutSetTargets: List<WorkoutSetTargetState>,
    nowMs: Long
): PlanWorkoutSectionContent {
    if (currentPlan == null) {
        return PlanWorkoutSectionContent.Empty(
            message = ""
        )
    }

    if (todayMotions.isEmpty()) {
        return PlanWorkoutSectionContent.Idle(
            dayIndex = normalizedPlanCurrentIndex(currentPlan),
            motions = emptyList()
        )
    }

    val progress = workoutProgress

    if (progress == null) {
        return PlanWorkoutSectionContent.Idle(
            dayIndex = normalizedPlanCurrentIndex(currentPlan),
            motions = todayMotions
        )
    }

    if (progress.isFinished) {
        return PlanWorkoutSectionContent.Completed(progress)
    }

    val activeTarget = progress.activeSetIndex?.let { setIndex ->
        workoutSetTargets.getOrNull(setIndex)
    }
    if (activeTarget != null) {
        return PlanWorkoutSectionContent.Active(
            progress = progress,
            currentTarget = activeTarget
        )
    }

    val nextTarget = workoutSetTargets.getOrNull(progress.nextSetIndex)
    if (nextTarget == null) {
        return PlanWorkoutSectionContent.Completed(
            progress = progress.copy(isFinished = true)
        )
    }

    return PlanWorkoutSectionContent.Ready(
        progress = progress,
        nextTarget = nextTarget,
        remainingBreakMs = workoutRemainingBreakMs(progress, nowMs)
    )
}

@Composable
private fun TodayPlanSection(
    content: PlanWorkoutSectionContent,
    workoutSession: WorkoutSessionState,
    onStartNextWorkoutSet: () -> Unit,
    onSkipWorkoutSet: () -> Unit,
    onFinishCurrentWorkoutSet: (WorkoutSetPerformanceInput) -> Unit,
    onCameraClick: (() -> Unit)? = null,
    pendingCameraVideo: String? = null,
    onCameraVideoCleared: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    when (content) {
        is PlanWorkoutSectionContent.Empty -> {
            EmptyPlanMessage(
                text = content.message.ifBlank {
                    stringResource(R.string.plan_no_current_plan_summary)
                },
                modifier = modifier
            )
        }

        is PlanWorkoutSectionContent.Idle -> {
            if (content.motions.isEmpty()) {
                EmptyPlanMessage(
                    text = stringResource(
                        R.string.plan_no_motion_for_day,
                        content.dayIndex
                    ),
                    modifier = modifier
                )
            } else {
                Column(
                    modifier = modifier,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    content.motions.forEach { motion ->
                        TodayMotionCard(
                            motion = motion,
                            dayIndex = content.dayIndex,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        is PlanWorkoutSectionContent.Ready -> {
            ReadyWorkoutSection(
                progress = content.progress,
                nextTarget = content.nextTarget,
                remainingBreakMs = content.remainingBreakMs,
                actionsEnabled = workoutSession.isWorkoutGoing && !workoutSession.isPaused,
                onSkipWorkoutSet = onSkipWorkoutSet,
                onStartNextWorkoutSet = onStartNextWorkoutSet,
                modifier = modifier
            )
        }

        is PlanWorkoutSectionContent.Active -> {
            ActiveWorkoutSection(
                progress = content.progress,
                currentTarget = content.currentTarget,
                actionsEnabled = workoutSession.isWorkoutGoing && !workoutSession.isPaused,
                onFinishCurrentWorkoutSet = onFinishCurrentWorkoutSet,
                onCameraClick = onCameraClick,
                pendingCameraVideo = pendingCameraVideo,
                onCameraVideoCleared = onCameraVideoCleared,
                modifier = modifier
            )
        }

        is PlanWorkoutSectionContent.Completed -> {
            CompletedWorkoutSection(
                progress = content.progress,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun ReadyWorkoutSection(
    progress: WorkoutProgressState,
    nextTarget: WorkoutSetTargetState,
    remainingBreakMs: Long,
    actionsEnabled: Boolean,
    onSkipWorkoutSet: () -> Unit,
    onStartNextWorkoutSet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WorkoutMetricCard(
            label = stringResource(R.string.plan_workout_percentage_label),
            value = stringResource(
                R.string.plan_workout_percentage_value,
                workoutCompletionPercentage(progress)
            ),
            supportingText = stringResource(
                R.string.plan_workout_sets_done_value,
                completedWorkoutSetCount(progress),
                progress.totalSetCount
            )
        )

        if (remainingBreakMs > 0L) {
            WorkoutMetricCard(
                label = stringResource(R.string.plan_workout_break_left_label),
                value = formatRemainingDuration(remainingBreakMs),
                supportingText = stringResource(R.string.plan_workout_break_left_supporting_text)
            )
        }

        WorkoutTargetCard(
            stageLabel = stringResource(R.string.plan_workout_next_motion_label),
            target = nextTarget,
            primaryActionLabel = stringResource(R.string.plan_workout_start_set_action),
            secondaryActionLabel = stringResource(R.string.plan_workout_skip_set_action),
            actionsEnabled = actionsEnabled,
            onSecondaryAction = onSkipWorkoutSet,
            onPrimaryAction = onStartNextWorkoutSet
        )
    }
}

@Composable
private fun ActiveWorkoutSection(
    progress: WorkoutProgressState,
    currentTarget: WorkoutSetTargetState,
    actionsEnabled: Boolean,
    onFinishCurrentWorkoutSet: (WorkoutSetPerformanceInput) -> Unit,
    onCameraClick: (() -> Unit)? = null,
    pendingCameraVideo: String? = null,
    onCameraVideoCleared: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showPerformanceDialog by remember(currentTarget.orderIndex) {
        mutableStateOf(pendingCameraVideo != null)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WorkoutMetricCard(
            label = stringResource(R.string.plan_workout_percentage_label),
            value = stringResource(
                R.string.plan_workout_percentage_value,
                workoutCompletionPercentage(progress)
            ),
            supportingText = stringResource(
                R.string.plan_workout_sets_done_value,
                completedWorkoutSetCount(progress),
                progress.totalSetCount
            )
        )

        WorkoutTargetCard(
            stageLabel = stringResource(R.string.plan_workout_current_motion_label),
            target = currentTarget,
            primaryActionLabel = stringResource(R.string.plan_workout_finish_set_action),
            actionsEnabled = actionsEnabled,
            onCameraClick = onCameraClick,
            onPrimaryAction = {
                showPerformanceDialog = true
            }
        )
    }

    if (showPerformanceDialog) {
        WorkoutPerformanceDialog(
            target = currentTarget,
            videoName = pendingCameraVideo,
            onDismiss = {
                showPerformanceDialog = false
                onCameraVideoCleared()
            },
            onConfirm = { performance ->
                showPerformanceDialog = false
                onCameraVideoCleared()
                onFinishCurrentWorkoutSet(performance)
            }
        )
    }
}

@Composable
private fun CompletedWorkoutSection(
    progress: WorkoutProgressState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.plan_workout_done_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Text(
                    text = stringResource(R.string.plan_workout_done_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center
                )
            }
        }

        WorkoutMetricCard(
            label = stringResource(R.string.plan_workout_time_cost_label),
            value = formatElapsedDuration(progress.completedElapsedTimeMs),
            supportingText = stringResource(R.string.plan_workout_time_cost_supporting_text)
        )

        WorkoutMetricCard(
            label = stringResource(R.string.plan_workout_workload_label),
            value = stringResource(R.string.plan_workout_workload_empty_value),
            supportingText = stringResource(R.string.plan_workout_workload_supporting_text)
        )
    }
}

@Composable
private fun WorkoutTargetCard(
    stageLabel: String,
    target: WorkoutSetTargetState,
    primaryActionLabel: String,
    actionsEnabled: Boolean,
    modifier: Modifier = Modifier,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    onCameraClick: (() -> Unit)? = null,
    onPrimaryAction: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stageLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FitnessCenter,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = target.motionTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(
                        R.string.plan_workout_set_detail_value,
                        formatWeightValue(target.weight),
                        target.reps,
                        target.setIndex,
                        target.setsInMotion
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    TextButton(
                        onClick = onSecondaryAction,
                        enabled = actionsEnabled
                    ) {
                        Text(text = secondaryActionLabel)
                    }
                }

                if (onCameraClick != null) {
                    FilledTonalIconButton(
                        onClick = onCameraClick,
                        enabled = actionsEnabled
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = stringResource(R.string.plan_workout_camera_action)
                        )
                    }
                }

                Button(
                    onClick = onPrimaryAction,
                    enabled = actionsEnabled
                ) {
                    Text(text = primaryActionLabel)
                }
            }
        }
    }
}

@Composable
private fun WorkoutMetricCard(
    label: String,
    value: String,
    supportingText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WorkoutPerformanceDialog(
    target: WorkoutSetTargetState,
    videoName: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (WorkoutSetPerformanceInput) -> Unit
) {
    var repsDone by remember(target.orderIndex) {
        mutableStateOf(target.reps.toString())
    }
    var weightDone by remember(target.orderIndex) {
        mutableStateOf(defaultWeightInput(target.weight))
    }
    var breakMinuteText by remember(target.orderIndex) {
        mutableStateOf("0")
    }
    var breakSecondText by remember(target.orderIndex) {
        mutableStateOf("0")
    }
    var selectedFeeling by remember(target.orderIndex) {
        mutableStateOf(WorkoutSetFeeling.Simple)
    }
    var showInputError by remember(target.orderIndex) {
        mutableStateOf(false)
    }

    fun parseBreakMinute(): Int = breakMinuteText.filter { it.isDigit() }.toIntOrNull() ?: 0
    fun parseBreakSecond(): Int = breakSecondText.filter { it.isDigit() }.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.plan_workout_mark_performance_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(
                        R.string.plan_workout_mark_performance_subtitle,
                        target.motionTitle,
                        target.setIndex,
                        target.setsInMotion
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = repsDone,
                    onValueChange = { value ->
                        repsDone = value.filter { character -> character.isDigit() }
                        showInputError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(text = stringResource(R.string.plan_workout_reps_done_label))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            val current = weightDone.toDoubleOrNull() ?: target.weight
                            weightDone = formatWeightInput(maxOf(0.0, current - 5.0))
                            showInputError = false
                        }
                    ) {
                        Icon(
                            Icons.Rounded.Remove,
                            contentDescription = stringResource(
                                R.string.plan_workout_decrease_weight_content_description
                            )
                        )
                    }

                    OutlinedTextField(
                        value = weightDone,
                        onValueChange = { value ->
                            weightDone = value.filter { character ->
                                character.isDigit() || character == '.'
                            }
                            showInputError = false
                        },
                        modifier = Modifier.weight(1f),
                        label = {
                            Text(text = stringResource(R.string.plan_workout_weight_done_label))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    FilledTonalIconButton(
                        onClick = {
                            val current = weightDone.toDoubleOrNull() ?: target.weight
                            weightDone = formatWeightInput(current + 5.0)
                            showInputError = false
                        }
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(
                                R.string.plan_workout_increase_weight_content_description
                            )
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.plan_workout_feeling_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    WorkoutSetFeeling.entries.forEach { feeling ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedFeeling == feeling,
                                onClick = {
                                    selectedFeeling = feeling
                                }
                            )

                            Text(
                                text = feelingLabel(feeling),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.plan_workout_break_time_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.plan_workout_break_min_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    breakMinuteText = maxOf(0, parseBreakMinute() - 1).toString()
                                    showInputError = false
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Remove,
                                    contentDescription = stringResource(
                                        R.string.plan_workout_subtract_minute_content_description
                                    ),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            OutlinedTextField(
                                value = breakMinuteText,
                                onValueChange = { value ->
                                    val filtered = value.filter { it.isDigit() }
                                    val intVal = filtered.toIntOrNull() ?: 0
                                    breakMinuteText = intVal.toString()
                                    showInputError = false
                                },
                                modifier = Modifier.width(64.dp),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    textAlign = TextAlign.Center
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )

                            FilledTonalIconButton(
                                onClick = {
                                    breakMinuteText = (parseBreakMinute() + 1).toString()
                                    showInputError = false
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = stringResource(
                                        R.string.plan_workout_add_minute_content_description
                                    ),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.plan_workout_break_sec_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    var sec = parseBreakSecond() - 30
                                    var min = parseBreakMinute()
                                    if (sec < 0) {
                                        min--
                                        sec += 60
                                    }
                                    if (min < 0) {
                                        min = 0
                                        sec = 0
                                    }
                                    breakMinuteText = min.toString()
                                    breakSecondText = sec.toString()
                                    showInputError = false
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Remove,
                                    contentDescription = stringResource(
                                        R.string.plan_workout_subtract_seconds_content_description
                                    ),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            OutlinedTextField(
                                value = breakSecondText,
                                onValueChange = { value ->
                                    val filtered = value.filter { it.isDigit() }
                                    var intVal = filtered.toIntOrNull() ?: 0

                                    if (intVal >= 60) {
                                        val carryMin = intVal / 60
                                        intVal %= 60
                                        breakMinuteText = (parseBreakMinute() + carryMin).toString()
                                    }

                                    breakSecondText = intVal.toString()
                                    showInputError = false
                                },
                                modifier = Modifier.width(64.dp),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    textAlign = TextAlign.Center
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )

                            FilledTonalIconButton(
                                onClick = {
                                    var sec = parseBreakSecond() + 30
                                    var min = parseBreakMinute()
                                    if (sec >= 60) {
                                        min++
                                        sec -= 60
                                    }
                                    breakMinuteText = min.toString()
                                    breakSecondText = sec.toString()
                                    showInputError = false
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = stringResource(
                                        R.string.plan_workout_add_seconds_content_description
                                    ),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                if (showInputError) {
                    Text(
                        text = stringResource(R.string.plan_workout_input_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedRepsDone = repsDone.toIntOrNull() ?: target.reps
                    val parsedWeightDone = if (weightDone.isBlank()) {
                        target.weight
                    } else {
                        weightDone.toDoubleOrNull()
                    }
                    val parsedBreakSeconds = parseBreakMinute() * 60 + parseBreakSecond()

                    if (parsedRepsDone < 0 || parsedWeightDone == null || parsedWeightDone < 0.0 || parsedBreakSeconds < 0) {
                        showInputError = true
                        return@TextButton
                    }

                    onConfirm(
                        WorkoutSetPerformanceInput(
                            repsDone = parsedRepsDone,
                            weightDone = parsedWeightDone,
                            feeling = selectedFeeling,
                            breakDurationSeconds = parsedBreakSeconds,
                            videoName = videoName
                        )
                    )
                }
            ) {
                Text(text = stringResource(R.string.plan_workout_done_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun WorkoutSessionCard(
    workoutSession: WorkoutSessionState,
    modifier: Modifier = Modifier
) {
    val elapsedTimeMs = rememberWorkoutElapsedTimeMs(workoutSession)
    val statusText = if (workoutSession.isPaused) {
        stringResource(R.string.plan_workout_paused_label)
    } else {
        stringResource(R.string.plan_workout_active_label)
    }
    val subtitleText = if (workoutSession.isPaused) {
        stringResource(R.string.plan_workout_paused_subtitle)
    } else {
        stringResource(R.string.plan_workout_active_subtitle)
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Text(
                text = formatElapsedDuration(elapsedTimeMs),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f)
            )
        }
    }
}

@Composable
private fun CurrentPlanCard(
    currentPlan: TrainingPlanState?,
    onEditPlan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val planName = currentPlan?.name ?: stringResource(R.string.plan_no_current_plan_selected)

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.plan_current_plan_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                )

                Text(
                    text = planName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                if (currentPlan != null) {
                    Text(
                        text = stringResource(
                            R.string.plan_day_of_cycle,
                            normalizedPlanCurrentIndex(currentPlan),
                            currentPlan.cyclePeriod
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                    )
                }
            }

            IconButton(onClick = onEditPlan) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.plan_open_editor_content_description),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun TodayMotionCard(
    motion: PlanMotionState,
    dayIndex: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FitnessCenter,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = motion.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = stringResource(
                        R.string.plan_motion_summary,
                        motion.sets,
                        motion.repsPerSet
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = stringResource(
                        R.string.plan_today_day_label,
                        dayIndex
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun EmptyPlanMessage(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun rememberWorkoutElapsedTimeMs(workoutSession: WorkoutSessionState): Long {
    var currentTimeMs by remember(
        workoutSession.isWorkoutGoing,
        workoutSession.isPaused,
        workoutSession.lastResumedAt,
        workoutSession.elapsedBeforePauseMs
    ) {
        mutableStateOf(System.currentTimeMillis())
    }

    LaunchedEffect(
        workoutSession.isWorkoutGoing,
        workoutSession.isPaused,
        workoutSession.lastResumedAt,
        workoutSession.elapsedBeforePauseMs
    ) {
        currentTimeMs = System.currentTimeMillis()

        if (!workoutSession.isWorkoutGoing || workoutSession.isPaused) {
            return@LaunchedEffect
        }

        while (true) {
            delay(1_000L)
            currentTimeMs = System.currentTimeMillis()
        }
    }

    return workoutElapsedTimeMs(workoutSession, currentTimeMs)
}

@Composable
private fun rememberPlanCurrentTimeMs(shouldTick: Boolean): Long {
    var currentTimeMs by remember(shouldTick) {
        mutableStateOf(System.currentTimeMillis())
    }

    LaunchedEffect(shouldTick) {
        currentTimeMs = System.currentTimeMillis()

        if (!shouldTick) {
            return@LaunchedEffect
        }

        while (true) {
            delay(1_000L)
            currentTimeMs = System.currentTimeMillis()
        }
    }

    return currentTimeMs
}

private fun formatElapsedDuration(elapsedTimeMs: Long): String {
    val totalSeconds = (elapsedTimeMs / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L

    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}

private fun formatRemainingDuration(remainingTimeMs: Long): String {
    val totalSeconds = (remainingTimeMs / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L

    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

@Composable
private fun formatWeightValue(weight: Double): String {
    if (weight <= 0.0) {
        return stringResource(R.string.plan_workout_unset_value)
    }

    return if (weight % 1.0 == 0.0) {
        String.format(Locale.getDefault(), "%.0f kg", weight)
    } else {
        String.format(Locale.getDefault(), "%.1f kg", weight)
    }
}

private fun defaultWeightInput(weight: Double): String {
    if (weight <= 0.0) {
        return ""
    }

    return formatWeightInput(weight)
}

private fun formatWeightInput(weight: Double): String {
    val clamped = weight.coerceAtLeast(0.0)
    return if (clamped % 1.0 == 0.0) {
        String.format(Locale.getDefault(), "%.0f", clamped)
    } else {
        String.format(Locale.getDefault(), "%.1f", clamped)
    }
}

@Composable
private fun feelingLabel(feeling: WorkoutSetFeeling): String {
    return when (feeling) {
        WorkoutSetFeeling.TooEasy -> stringResource(R.string.plan_workout_feeling_too_easy)
        WorkoutSetFeeling.Simple -> stringResource(R.string.plan_workout_feeling_simple)
        WorkoutSetFeeling.HardButControlled -> stringResource(R.string.plan_workout_feeling_hard_but_controlled)
        WorkoutSetFeeling.AlmostLostControl -> stringResource(R.string.plan_workout_feeling_almost_lost_control)
    }
}

private fun planSectionEnter(delayMillis: Int): EnterTransition {
    return fadeIn(
        animationSpec = tween(
            durationMillis = LiftInsightMotion.MediumDuration,
            delayMillis = delayMillis,
            easing = LiftInsightMotion.EnterEasing
        )
    ) + slideInVertically(
        animationSpec = tween(
            durationMillis = LiftInsightMotion.LongDuration,
            delayMillis = delayMillis,
            easing = LiftInsightMotion.EnterEasing
        ),
        initialOffsetY = { fullHeight -> fullHeight / 12 }
    )
}
