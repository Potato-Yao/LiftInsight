package com.potato.liftinsight.home

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.potato.liftinsight.R
import com.potato.liftinsight.body.BodyScreen
import com.potato.liftinsight.body.model.defaultBodyMetrics
import com.potato.liftinsight.body.model.updateBodyMetric
import com.potato.liftinsight.common.BottomBarItem
import com.potato.liftinsight.common.LiftInsightBottomBar
import com.potato.liftinsight.plan.MotionDetailScreen
import com.potato.liftinsight.plan.PlanDetailScreen
import com.potato.liftinsight.plan.PlanScreen
import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.plan.model.addMotionToPlan
import com.potato.liftinsight.plan.model.createTrainingPlan
import com.potato.liftinsight.plan.model.deletePlanMotion
import com.potato.liftinsight.plan.model.deleteTrainingPlan
import com.potato.liftinsight.plan.model.movePlanMotion
import com.potato.liftinsight.plan.model.planMotion
import com.potato.liftinsight.plan.model.selectTrainingPlan
import com.potato.liftinsight.plan.model.trainingPlan
import com.potato.liftinsight.plan.model.updateMotionRepsPerSet
import com.potato.liftinsight.plan.model.updateMotionSets
import com.potato.liftinsight.plan.model.updateTrainingPlanName
import com.potato.liftinsight.ui.theme.LiftInsightMotion
import com.potato.liftinsight.ui.theme.LiftInsightTheme

@Composable
fun LiftInsightHomeRoute() {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val bodyMetrics = remember { mutableStateListOf(*defaultBodyMetrics().toTypedArray()) }

    val strengthBaseName = stringResource(R.string.plan_name_strength_base)
    val competitionPeakName = stringResource(R.string.plan_name_competition_peak)
    val techniqueCycleName = stringResource(R.string.plan_name_technique_cycle)
    val pullVolumeBlockName = stringResource(R.string.plan_name_pull_volume_block)
    val snatchTitle = stringResource(R.string.motion_name_snatch)
    val cleanAndJerkTitle = stringResource(R.string.motion_name_clean_and_jerk)
    val snatchPullTitle = stringResource(R.string.motion_name_snatch_pull)
    val cleanPullTitle = stringResource(R.string.motion_name_clean_pull)
    val pushPressTitle = stringResource(R.string.motion_name_push_press)
    val frontSquatTitle = stringResource(R.string.body_front_squat)
    val backSquatTitle = stringResource(R.string.body_back_squat)

    val availableMotions = remember(
        snatchTitle,
        cleanAndJerkTitle,
        snatchPullTitle,
        cleanPullTitle,
        pushPressTitle,
        frontSquatTitle,
        backSquatTitle
    ) {
        listOf(
            AvailableMotionState(id = 1, title = snatchTitle, defaultSets = 5, defaultRepsPerSet = 2),
            AvailableMotionState(id = 2, title = cleanAndJerkTitle, defaultSets = 5, defaultRepsPerSet = 1),
            AvailableMotionState(id = 3, title = snatchPullTitle, defaultSets = 4, defaultRepsPerSet = 3),
            AvailableMotionState(id = 4, title = cleanPullTitle, defaultSets = 4, defaultRepsPerSet = 3),
            AvailableMotionState(id = 5, title = pushPressTitle, defaultSets = 4, defaultRepsPerSet = 4),
            AvailableMotionState(id = 6, title = frontSquatTitle, defaultSets = 5, defaultRepsPerSet = 3),
            AvailableMotionState(id = 7, title = backSquatTitle, defaultSets = 5, defaultRepsPerSet = 5)
        )
    }

    var trainingPlans by remember(
        strengthBaseName,
        competitionPeakName,
        techniqueCycleName,
        pullVolumeBlockName,
        snatchTitle,
        cleanAndJerkTitle,
        snatchPullTitle,
        cleanPullTitle,
        pushPressTitle,
        frontSquatTitle,
        backSquatTitle
    ) {
        mutableStateOf(
            listOf(
                TrainingPlanState(
                    id = 1,
                    name = strengthBaseName,
                    lastAppliedAt = 1715600000000,
                    motions = listOf(
                        PlanMotionState(entryId = 1, motionId = 1, title = snatchTitle, sets = 5, repsPerSet = 2),
                        PlanMotionState(entryId = 2, motionId = 6, title = frontSquatTitle, sets = 5, repsPerSet = 3),
                        PlanMotionState(entryId = 3, motionId = 3, title = snatchPullTitle, sets = 4, repsPerSet = 3)
                    )
                ),
                TrainingPlanState(
                    id = 2,
                    name = competitionPeakName,
                    lastAppliedAt = 1715800000000,
                    motions = listOf(
                        PlanMotionState(entryId = 1, motionId = 2, title = cleanAndJerkTitle, sets = 6, repsPerSet = 1),
                        PlanMotionState(entryId = 2, motionId = 1, title = snatchTitle, sets = 5, repsPerSet = 1),
                        PlanMotionState(entryId = 3, motionId = 5, title = pushPressTitle, sets = 4, repsPerSet = 3)
                    )
                ),
                TrainingPlanState(
                    id = 3,
                    name = techniqueCycleName,
                    lastAppliedAt = 1715400000000,
                    motions = listOf(
                        PlanMotionState(entryId = 1, motionId = 1, title = snatchTitle, sets = 6, repsPerSet = 2),
                        PlanMotionState(entryId = 2, motionId = 2, title = cleanAndJerkTitle, sets = 5, repsPerSet = 2),
                        PlanMotionState(entryId = 3, motionId = 4, title = cleanPullTitle, sets = 4, repsPerSet = 3)
                    )
                ),
                TrainingPlanState(
                    id = 4,
                    name = pullVolumeBlockName,
                    lastAppliedAt = 1715200000000,
                    motions = listOf(
                        PlanMotionState(entryId = 1, motionId = 4, title = cleanPullTitle, sets = 5, repsPerSet = 3),
                        PlanMotionState(entryId = 2, motionId = 7, title = backSquatTitle, sets = 5, repsPerSet = 5),
                        PlanMotionState(entryId = 3, motionId = 5, title = pushPressTitle, sets = 4, repsPerSet = 4)
                    )
                )
            )
        )
    }

    var currentPlanId by remember { mutableIntStateOf(2) }
    var planDestination by remember { mutableStateOf<PlanDestination>(PlanDestination.List) }
    var planIdPendingDelete by remember { mutableStateOf<Int?>(null) }
    var motionPendingDelete by remember { mutableStateOf<MotionDeleteTarget?>(null) }
    var addMotionPlanId by remember { mutableStateOf<Int?>(null) }
    val nextPlanName = stringResource(R.string.plan_name_new_default, trainingPlans.size + 1)

    val bottomBarItems = listOf(
        BottomBarItem(
            label = stringResource(R.string.nav_home),
            icon = Icons.Outlined.Home,
            selectedIcon = Icons.Rounded.Home
        ),
        BottomBarItem(
            label = stringResource(R.string.nav_body),
            icon = Icons.Outlined.AccessibilityNew,
            selectedIcon = Icons.Rounded.AccessibilityNew
        ),
        BottomBarItem(
            label = stringResource(R.string.nav_plan),
            icon = Icons.AutoMirrored.Outlined.Assignment,
            selectedIcon = Icons.AutoMirrored.Rounded.Assignment
        )
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (selectedTabIndex == 2) {
                when (val destination = planDestination) {
                    PlanDestination.List -> {
                        FloatingActionButton(
                            onClick = {
                                val createResult = createTrainingPlan(
                                    plans = trainingPlans,
                                    name = nextPlanName,
                                    createdAt = System.currentTimeMillis()
                                )

                                trainingPlans = createResult.plans
                                planDestination = PlanDestination.Detail(createResult.createdPlanId)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.plan_add_plan_content_description)
                            )
                        }
                    }

                    is PlanDestination.Detail -> {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SmallFloatingActionButton(
                                onClick = { planIdPendingDelete = destination.planId },
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.plan_delete_plan_content_description)
                                )
                            }

                            FloatingActionButton(onClick = { addMotionPlanId = destination.planId }) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.plan_add_motion_content_description)
                                )
                            }
                        }
                    }

                    is PlanDestination.Motion -> Unit
                }
            }
        },
        bottomBar = {
            LiftInsightBottomBar(
                items = bottomBarItems,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it }
            )
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedTabIndex,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1

                (fadeIn(
                    animationSpec = tween(
                        durationMillis = LiftInsightMotion.MediumDuration,
                        delayMillis = 50,
                        easing = LiftInsightMotion.EnterEasing
                    )
                ) +
                    slideInHorizontally(
                        animationSpec = tween(
                            durationMillis = LiftInsightMotion.LongDuration,
                            easing = LiftInsightMotion.EnterEasing
                        ),
                        initialOffsetX = { fullWidth -> direction * (fullWidth / 14) }
                    ) +
                    slideInVertically(
                        animationSpec = tween(
                            durationMillis = LiftInsightMotion.MediumDuration,
                            easing = LiftInsightMotion.EnterEasing
                        ),
                        initialOffsetY = { fullHeight -> fullHeight / 40 }
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
                            targetOffsetX = { fullWidth -> -direction * (fullWidth / 18) }
                        ) +
                        slideOutVertically(
                            animationSpec = tween(
                                durationMillis = LiftInsightMotion.ShortDuration,
                                easing = LiftInsightMotion.ExitEasing
                            ),
                            targetOffsetY = { fullHeight -> -(fullHeight / 48) }
                        ))
            },
            label = "mainTabs"
        ) { tabIndex ->
            when (tabIndex) {
                0 -> HomeScreen(modifier = Modifier.padding(innerPadding))

                1 -> BodyScreen(
                    metrics = bodyMetrics,
                    onMetricValueChange = { metricId, newValue ->
                        bodyMetrics.apply {
                            clear()
                            addAll(
                                updateBodyMetric(
                                    metrics = this,
                                    metricId = metricId,
                                    newValue = newValue
                                )
                            )
                        }
                    },
                    modifier = Modifier.padding(innerPadding)
                )

                else -> {
                    when (val destination = planDestination) {
                        PlanDestination.List -> {
                            PlanScreen(
                                plans = trainingPlans,
                                currentPlanId = currentPlanId,
                                onSelectPlan = { planId ->
                                    val selectionResult = selectTrainingPlan(
                                        plans = trainingPlans,
                                        currentPlanId = currentPlanId,
                                        planId = planId,
                                        selectedAt = System.currentTimeMillis()
                                    )

                                    trainingPlans = selectionResult.plans
                                    currentPlanId = selectionResult.currentPlanId
                                },
                                onEditPlan = { planId ->
                                    planDestination = PlanDestination.Detail(planId)
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }

                        is PlanDestination.Detail -> {
                            val plan = trainingPlan(trainingPlans, destination.planId)

                            if (plan == null) {
                                PlanScreen(
                                    plans = trainingPlans,
                                    currentPlanId = currentPlanId,
                                    onSelectPlan = { planId ->
                                        val selectionResult = selectTrainingPlan(
                                            plans = trainingPlans,
                                            currentPlanId = currentPlanId,
                                            planId = planId,
                                            selectedAt = System.currentTimeMillis()
                                        )

                                        trainingPlans = selectionResult.plans
                                        currentPlanId = selectionResult.currentPlanId
                                    },
                                    onEditPlan = { planId ->
                                        planDestination = PlanDestination.Detail(planId)
                                    },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            } else {
                                PlanDetailScreen(
                                    plan = plan,
                                    onBack = { planDestination = PlanDestination.List },
                                    onRenamePlan = { updatedName ->
                                        trainingPlans = updateTrainingPlanName(
                                            plans = trainingPlans,
                                            planId = destination.planId,
                                            newName = updatedName
                                        )
                                    },
                                    onMoveMotionUp = { motionEntryId ->
                                        trainingPlans = movePlanMotion(
                                            plans = trainingPlans,
                                            planId = destination.planId,
                                            motionEntryId = motionEntryId,
                                            direction = -1
                                        )
                                    },
                                    onMoveMotionDown = { motionEntryId ->
                                        trainingPlans = movePlanMotion(
                                            plans = trainingPlans,
                                            planId = destination.planId,
                                            motionEntryId = motionEntryId,
                                            direction = 1
                                        )
                                    },
                                    onEditMotion = { motionEntryId ->
                                        planDestination = PlanDestination.Motion(
                                            planId = destination.planId,
                                            motionEntryId = motionEntryId
                                        )
                                    },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                        }

                        is PlanDestination.Motion -> {
                            val plan = trainingPlan(trainingPlans, destination.planId)
                            val motion = plan?.let { planMotion(it, destination.motionEntryId) }

                            if (plan == null || motion == null) {
                                PlanScreen(
                                    plans = trainingPlans,
                                    currentPlanId = currentPlanId,
                                    onSelectPlan = { planId ->
                                        val selectionResult = selectTrainingPlan(
                                            plans = trainingPlans,
                                            currentPlanId = currentPlanId,
                                            planId = planId,
                                            selectedAt = System.currentTimeMillis()
                                        )

                                        trainingPlans = selectionResult.plans
                                        currentPlanId = selectionResult.currentPlanId
                                        planDestination = PlanDestination.List
                                    },
                                    onEditPlan = { planId ->
                                        planDestination = PlanDestination.Detail(planId)
                                    },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            } else {
                                MotionDetailScreen(
                                    planName = plan.name,
                                    motion = motion,
                                    onBack = {
                                        planDestination = PlanDestination.Detail(destination.planId)
                                    },
                                    onDecreaseSets = {
                                        trainingPlans = updateMotionSets(
                                            plans = trainingPlans,
                                            planId = destination.planId,
                                            motionEntryId = destination.motionEntryId,
                                            sets = motion.sets - 1
                                        )
                                    },
                                    onIncreaseSets = {
                                        trainingPlans = updateMotionSets(
                                            plans = trainingPlans,
                                            planId = destination.planId,
                                            motionEntryId = destination.motionEntryId,
                                            sets = motion.sets + 1
                                        )
                                    },
                                    onDecreaseRepsPerSet = {
                                        trainingPlans = updateMotionRepsPerSet(
                                            plans = trainingPlans,
                                            planId = destination.planId,
                                            motionEntryId = destination.motionEntryId,
                                            repsPerSet = motion.repsPerSet - 1
                                        )
                                    },
                                    onIncreaseRepsPerSet = {
                                        trainingPlans = updateMotionRepsPerSet(
                                            plans = trainingPlans,
                                            planId = destination.planId,
                                            motionEntryId = destination.motionEntryId,
                                            repsPerSet = motion.repsPerSet + 1
                                        )
                                    },
                                    onDeleteMotion = {
                                        motionPendingDelete = MotionDeleteTarget(
                                            planId = destination.planId,
                                            motionEntryId = destination.motionEntryId
                                        )
                                    },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val pendingPlanDelete = planIdPendingDelete?.let { trainingPlan(trainingPlans, it) }
    if (pendingPlanDelete != null) {
        AlertDialog(
            onDismissRequest = { planIdPendingDelete = null },
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
                TextButton(
                    onClick = {
                        val deleteResult = deleteTrainingPlan(
                            plans = trainingPlans,
                            currentPlanId = currentPlanId,
                            planId = pendingPlanDelete.id
                        )

                        trainingPlans = deleteResult.plans
                        currentPlanId = deleteResult.currentPlanId
                        planDestination = PlanDestination.List
                        planIdPendingDelete = null
                    }
                ) {
                    Text(text = stringResource(R.string.plan_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { planIdPendingDelete = null }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    val pendingMotionDelete = motionPendingDelete
    if (pendingMotionDelete != null) {
        val plan = trainingPlan(trainingPlans, pendingMotionDelete.planId)
        val motion = plan?.let { planMotion(it, pendingMotionDelete.motionEntryId) }

        if (plan != null && motion != null) {
            AlertDialog(
                onDismissRequest = { motionPendingDelete = null },
                title = {
                    Text(text = stringResource(R.string.motion_delete_dialog_title))
                },
                text = {
                    Text(
                        text = stringResource(
                            R.string.motion_delete_dialog_message,
                            motion.title,
                            plan.name
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            trainingPlans = deletePlanMotion(
                                plans = trainingPlans,
                                planId = pendingMotionDelete.planId,
                                motionEntryId = pendingMotionDelete.motionEntryId
                            )
                            planDestination = PlanDestination.Detail(pendingMotionDelete.planId)
                            motionPendingDelete = null
                        }
                    ) {
                        Text(text = stringResource(R.string.plan_delete_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { motionPendingDelete = null }) {
                        Text(text = stringResource(R.string.common_cancel))
                    }
                }
            )
        }
    }

    val motionPickerPlan = addMotionPlanId?.let { trainingPlan(trainingPlans, it) }
    if (motionPickerPlan != null) {
        AlertDialog(
            onDismissRequest = { addMotionPlanId = null },
            title = {
                Text(text = stringResource(R.string.plan_add_motion_dialog_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableMotions.forEach { motion ->
                        TextButton(
                            onClick = {
                                val addResult = addMotionToPlan(
                                    plans = trainingPlans,
                                    planId = motionPickerPlan.id,
                                    motion = motion
                                )

                                trainingPlans = addResult.plans
                                addMotionPlanId = null

                                if (addResult.motionEntryId != -1) {
                                    planDestination = PlanDestination.Motion(
                                        planId = motionPickerPlan.id,
                                        motionEntryId = addResult.motionEntryId
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = motion.title)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { addMotionPlanId = null }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

private sealed interface PlanDestination {
    data object List : PlanDestination

    data class Detail(val planId: Int) : PlanDestination

    data class Motion(val planId: Int, val motionEntryId: Int) : PlanDestination
}

private data class MotionDeleteTarget(
    val planId: Int,
    val motionEntryId: Int
)

@Preview(showBackground = true)
@Composable
private fun LiftInsightHomeRoutePreview() {
    LiftInsightTheme {
        LiftInsightHomeRoute()
    }
}
