package com.potato.liftinsight.plan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.potato.liftinsight.R
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.plan.model.currentPlanName
import com.potato.liftinsight.plan.model.sortPlansByLastApplied
import com.potato.liftinsight.ui.theme.LiftInsightMotion

@Composable
internal fun PlanScreen(
    plans: List<TrainingPlanState>,
    currentPlanId: Int,
    onSelectPlan: (planId: Int) -> Unit,
    onEditPlan: (planId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showContent by remember { mutableStateOf(false) }
    val displayPlans = remember(plans, currentPlanId) {
        val sortedPlans = sortPlansByLastApplied(plans)
        val currentPlan = sortedPlans.firstOrNull { it.id == currentPlanId }

        if (currentPlan == null) {
            sortedPlans
        } else {
            buildList {
                add(currentPlan)
                addAll(sortedPlans.filterNot { it.id == currentPlanId })
            }
        }
    }
    val selectedPlanName = currentPlanName(plans, currentPlanId)
        ?: stringResource(R.string.plan_no_current_plan_selected)

    LaunchedEffect(Unit) {
        showContent = true
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
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
                CurrentPlanRow(
                    currentPlanName = selectedPlanName,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (displayPlans.isEmpty()) {
            item(key = "emptyPlans") {
                AnimatedVisibility(
                    visible = showContent,
                    enter = planSectionEnter(delayMillis = 100),
                    exit = ExitTransition.None
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(28.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.plan_no_plans_available),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            itemsIndexed(
                items = displayPlans,
                key = { _, plan -> plan.id }
            ) { index, plan ->
                AnimatedVisibility(
                    visible = showContent,
                    enter = planSectionEnter(delayMillis = 100 + (index * 35)),
                    exit = ExitTransition.None
                ) {
                    PlanRow(
                        plan = plan,
                        isCurrent = plan.id == currentPlanId,
                        onSelectPlan = { onSelectPlan(plan.id) },
                        onEditPlan = { onEditPlan(plan.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                placementSpec = tween(
                                    durationMillis = LiftInsightMotion.LongDuration,
                                    easing = LiftInsightMotion.EnterEasing
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentPlanRow(
    currentPlanName: String,
    modifier: Modifier = Modifier
) {
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.plan_current_plan_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            AnimatedContent(
                targetState = currentPlanName,
                transitionSpec = {
                    (fadeIn(
                        animationSpec = tween(
                            durationMillis = LiftInsightMotion.MediumDuration,
                            easing = LiftInsightMotion.EnterEasing
                        )
                    ) + slideInVertically(
                        animationSpec = tween(
                            durationMillis = LiftInsightMotion.MediumDuration,
                            easing = LiftInsightMotion.EnterEasing
                        ),
                        initialOffsetY = { fullHeight -> fullHeight / 3 }
                    )) togetherWith
                        (fadeOut(
                            animationSpec = tween(
                                durationMillis = LiftInsightMotion.ShortDuration,
                                easing = LiftInsightMotion.ExitEasing
                            )
                        ) + slideOutVertically(
                            animationSpec = tween(
                                durationMillis = LiftInsightMotion.ShortDuration,
                                easing = LiftInsightMotion.ExitEasing
                            ),
                            targetOffsetY = { fullHeight -> -(fullHeight / 4) }
                        ))
                },
                label = "currentPlanName"
            ) { animatedPlanName ->
                Text(
                    text = animatedPlanName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun PlanRow(
    plan: TrainingPlanState,
    isCurrent: Boolean,
    onSelectPlan: () -> Unit,
    onEditPlan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isCurrent) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(
            durationMillis = LiftInsightMotion.MediumDuration,
            easing = LiftInsightMotion.EnterEasing
        ),
        label = "planRowContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isCurrent) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        },
        animationSpec = tween(
            durationMillis = LiftInsightMotion.MediumDuration,
            easing = LiftInsightMotion.EnterEasing
        ),
        label = "planRowBorder"
    )
    val titleColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(
            durationMillis = LiftInsightMotion.MediumDuration,
            easing = LiftInsightMotion.EnterEasing
        ),
        label = "planRowTitle"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isCurrent) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(
            durationMillis = LiftInsightMotion.MediumDuration,
            easing = LiftInsightMotion.EnterEasing
        ),
        label = "planRowIconTint"
    )
    val tonalElevation by animateDpAsState(
        targetValue = if (isCurrent) 4.dp else 0.dp,
        animationSpec = tween(
            durationMillis = LiftInsightMotion.MediumDuration,
            easing = LiftInsightMotion.EnterEasing
        ),
        label = "planRowElevation"
    )
    val liftProgress by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0f,
        animationSpec = tween(
            durationMillis = LiftInsightMotion.MediumDuration,
            easing = LiftInsightMotion.EnterEasing
        ),
        label = "planRowLiftProgress"
    )

    Surface(
        modifier = modifier
            .zIndex(if (isCurrent) 1f else 0f)
            .graphicsLayer {
                translationY = -12f * liftProgress
                scaleX = 1f + (0.015f * liftProgress)
                scaleY = 1f + (0.015f * liftProgress)
            }
            .clickable(onClick = onSelectPlan),
        color = containerColor,
        tonalElevation = tonalElevation,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            width = 1.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor
                )

                AnimatedVisibility(
                    visible = isCurrent,
                    enter = fadeIn(
                        animationSpec = tween(
                            durationMillis = LiftInsightMotion.MediumDuration,
                            easing = LiftInsightMotion.EnterEasing
                        )
                    ) + slideInVertically(
                        animationSpec = tween(
                            durationMillis = LiftInsightMotion.MediumDuration,
                            easing = LiftInsightMotion.EnterEasing
                        ),
                        initialOffsetY = { fullHeight -> fullHeight / 3 }
                    ),
                    exit = fadeOut(
                        animationSpec = tween(
                            durationMillis = LiftInsightMotion.ShortDuration,
                            easing = LiftInsightMotion.ExitEasing
                        )
                    )
                ) {
                    Text(
                        text = stringResource(R.string.plan_current_badge),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(onClick = onEditPlan) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(
                        R.string.plan_edit_content_description,
                        plan.name
                    ),
                    tint = iconTint
                )
            }
        }
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
