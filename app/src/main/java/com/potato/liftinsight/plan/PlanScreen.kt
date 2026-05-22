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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.potato.liftinsight.R
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.plan.model.WorkoutSessionState
import com.potato.liftinsight.plan.model.normalizedPlanCurrentIndex
import com.potato.liftinsight.plan.model.workoutElapsedTimeMs
import com.potato.liftinsight.ui.theme.LiftInsightMotion
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun PlanScreen(
    currentPlan: TrainingPlanState?,
    workoutSession: WorkoutSessionState,
    todayMotions: List<PlanMotionState>,
    onEditPlan: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showContent by remember { mutableStateOf(false) }

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

        if (currentPlan == null) {
            item(key = "emptyCurrentPlan") {
                AnimatedVisibility(
                    visible = showContent,
                    enter = planSectionEnter(delayMillis = 135),
                    exit = ExitTransition.None
                ) {
                    EmptyPlanMessage(
                        text = stringResource(R.string.plan_no_current_plan_summary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else if (todayMotions.isEmpty()) {
            item(key = "emptyToday") {
                AnimatedVisibility(
                    visible = showContent,
                    enter = planSectionEnter(delayMillis = 135),
                    exit = ExitTransition.None
                ) {
                    EmptyPlanMessage(
                        text = stringResource(
                            R.string.plan_no_motion_for_day,
                            normalizedPlanCurrentIndex(currentPlan)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            itemsIndexed(
                items = todayMotions,
                key = { _, motion -> motion.entryId }
            ) { index, motion ->
                AnimatedVisibility(
                    visible = showContent,
                    enter = planSectionEnter(delayMillis = 135 + (index * 35)),
                    exit = ExitTransition.None
                ) {
                    TodayMotionCard(
                        motion = motion,
                        dayIndex = normalizedPlanCurrentIndex(currentPlan),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
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

private fun formatElapsedDuration(elapsedTimeMs: Long): String {
    val totalSeconds = (elapsedTimeMs / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L

    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
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
