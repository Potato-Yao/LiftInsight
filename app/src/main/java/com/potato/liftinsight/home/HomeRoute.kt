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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.potato.liftinsight.R
import com.potato.liftinsight.body.BodyScreen
import com.potato.liftinsight.common.BottomBarItem
import com.potato.liftinsight.common.LiftInsightBottomBar
import com.potato.liftinsight.home.controller.HomeController
import com.potato.liftinsight.home.controller.MainTab
import com.potato.liftinsight.home.controller.HomeState
import com.potato.liftinsight.home.controller.PlanDestination
import com.potato.liftinsight.home.controller.planDestinationDepth
import com.potato.liftinsight.motion.MotionScreen
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.plan.data.defaultTrainingPlanSeedCatalog
import com.potato.liftinsight.plan.MotionDetailScreen
import com.potato.liftinsight.plan.PlanDetailScreen
import com.potato.liftinsight.plan.PlanScreen
import com.potato.liftinsight.plan.model.planMotion
import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.plan.model.trainingPlan
import com.potato.liftinsight.settings.SettingsScreen
import com.potato.liftinsight.ui.theme.LiftInsightMotion
import com.potato.liftinsight.ui.theme.LiftInsightTheme
import kotlinx.coroutines.launch

@Composable
fun HomeRoute(
    trainingPlanStore: TrainingPlanStore,
    enableDebugPlanSeed: Boolean
) {
    val context = LocalContext.current
    val controller = remember(trainingPlanStore, enableDebugPlanSeed) {
        HomeController(
            trainingPlanStore = trainingPlanStore,
            shouldSeedDebugPlans = enableDebugPlanSeed
        )
    }
    val coroutineScope = rememberCoroutineScope()
    var state by remember(controller) {
        mutableStateOf(controller.emptyState())
    }

    LaunchedEffect(controller, context) {
        state = controller.loadState(defaultTrainingPlanSeedCatalog(context))
    }

    val nextPlanName = stringResource(R.string.plan_name_new_default, state.trainingPlans.size + 1)

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
            label = stringResource(R.string.nav_motion),
            icon = Icons.Outlined.Videocam,
            selectedIcon = Icons.Rounded.Videocam
        ),
        BottomBarItem(
            label = stringResource(R.string.nav_plan),
            icon = Icons.AutoMirrored.Outlined.Assignment,
            selectedIcon = Icons.AutoMirrored.Rounded.Assignment
        ),
        BottomBarItem(
            label = stringResource(R.string.nav_settings),
            icon = Icons.Outlined.Settings,
            selectedIcon = Icons.Rounded.Settings
        )
    )

    HomeScaffold(
        state = state,
        bottomBarItems = bottomBarItems,
        onTabSelected = { tabIndex ->
            state = controller.selectTab(state, tabIndex)
        },
        onBodyMetricValueChange = { metricId, newValue ->
            state = controller.updateBodyMetric(state, metricId, newValue)
        },
        onCreatePlan = {
            coroutineScope.launch {
                state = controller.createPlan(state, nextPlanName)
            }
        },
        onSelectPlan = { planId ->
            coroutineScope.launch {
                state = controller.selectPlan(state, planId)
            }
        },
        onOpenPlanDetail = { planId ->
            state = controller.showPlanDetail(state, planId)
        },
        onBackToPlanList = {
            state = controller.showPlanList(state)
        },
        onRenamePlan = { planId, newName ->
            coroutineScope.launch {
                state = controller.renamePlan(state, planId, newName)
            }
        },
        onMoveMotionUp = { planId, motionEntryId ->
            coroutineScope.launch {
                state = controller.movePlanMotion(
                    state = state,
                    planId = planId,
                    motionEntryId = motionEntryId,
                    direction = -1
                )
            }
        },
        onMoveMotionDown = { planId, motionEntryId ->
            coroutineScope.launch {
                state = controller.movePlanMotion(
                    state = state,
                    planId = planId,
                    motionEntryId = motionEntryId,
                    direction = 1
                )
            }
        },
        onOpenMotionDetail = { planId, motionEntryId ->
            state = controller.showMotionDetail(state, planId, motionEntryId)
        },
        onDecreaseMotionSets = { planId, motionEntryId, sets ->
            coroutineScope.launch {
                state = controller.updateMotionSets(
                    state = state,
                    planId = planId,
                    motionEntryId = motionEntryId,
                    sets = sets - 1
                )
            }
        },
        onIncreaseMotionSets = { planId, motionEntryId, sets ->
            coroutineScope.launch {
                state = controller.updateMotionSets(
                    state = state,
                    planId = planId,
                    motionEntryId = motionEntryId,
                    sets = sets + 1
                )
            }
        },
        onDecreaseMotionReps = { planId, motionEntryId, repsPerSet ->
            coroutineScope.launch {
                state = controller.updateMotionRepsPerSet(
                    state = state,
                    planId = planId,
                    motionEntryId = motionEntryId,
                    repsPerSet = repsPerSet - 1
                )
            }
        },
        onIncreaseMotionReps = { planId, motionEntryId, repsPerSet ->
            coroutineScope.launch {
                state = controller.updateMotionRepsPerSet(
                    state = state,
                    planId = planId,
                    motionEntryId = motionEntryId,
                    repsPerSet = repsPerSet + 1
                )
            }
        },
        onRequestPlanDeletion = { planId ->
            state = controller.requestPlanDeletion(state, planId)
        },
        onDismissPlanDeletion = {
            state = controller.cancelPlanDeletion(state)
        },
        onConfirmPlanDeletion = {
            coroutineScope.launch {
                state = controller.confirmPlanDeletion(state)
            }
        },
        onRequestMotionDeletion = { planId, motionEntryId ->
            state = controller.requestMotionDeletion(state, planId, motionEntryId)
        },
        onDismissMotionDeletion = {
            state = controller.cancelMotionDeletion(state)
        },
        onConfirmMotionDeletion = {
            coroutineScope.launch {
                state = controller.confirmMotionDeletion(state)
            }
        },
        onOpenAddMotionPicker = { planId ->
            state = controller.openAddMotionPicker(state, planId)
        },
        onDismissAddMotionPicker = {
            state = controller.closeAddMotionPicker(state)
        },
        onAddMotionToPlan = { planId, motion ->
            coroutineScope.launch {
                state = controller.addMotionToPlan(state, planId, motion)
            }
        }
    )
}

@Composable
private fun HomeScaffold(
    state: HomeState,
    bottomBarItems: List<BottomBarItem>,
    onTabSelected: (Int) -> Unit,
    onBodyMetricValueChange: (Int, String) -> Unit,
    onCreatePlan: () -> Unit,
    onSelectPlan: (Int) -> Unit,
    onOpenPlanDetail: (Int) -> Unit,
    onBackToPlanList: () -> Unit,
    onRenamePlan: (Int, String) -> Unit,
    onMoveMotionUp: (Int, Int) -> Unit,
    onMoveMotionDown: (Int, Int) -> Unit,
    onOpenMotionDetail: (Int, Int) -> Unit,
    onDecreaseMotionSets: (Int, Int, Int) -> Unit,
    onIncreaseMotionSets: (Int, Int, Int) -> Unit,
    onDecreaseMotionReps: (Int, Int, Int) -> Unit,
    onIncreaseMotionReps: (Int, Int, Int) -> Unit,
    onRequestPlanDeletion: (Int) -> Unit,
    onDismissPlanDeletion: () -> Unit,
    onConfirmPlanDeletion: () -> Unit,
    onRequestMotionDeletion: (Int, Int) -> Unit,
    onDismissMotionDeletion: () -> Unit,
    onConfirmMotionDeletion: () -> Unit,
    onOpenAddMotionPicker: (Int) -> Unit,
    onDismissAddMotionPicker: () -> Unit,
    onAddMotionToPlan: (Int, AvailableMotionState) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (state.selectedTab == MainTab.Plan) {
                when (val destination = state.planDestination) {
                    PlanDestination.List -> {
                        FloatingActionButton(onClick = onCreatePlan) {
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
                                onClick = { onRequestPlanDeletion(destination.planId) },
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.plan_delete_plan_content_description)
                                )
                            }

                            FloatingActionButton(
                                onClick = { onOpenAddMotionPicker(destination.planId) }
                            ) {
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
                selectedTabIndex = state.selectedTabIndex,
                onTabSelected = onTabSelected
            )
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = state.selectedTab,
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
        ) { tab ->
            when (tab) {
                MainTab.Home -> HomeScreen(modifier = Modifier.padding(innerPadding))

                MainTab.Body -> BodyScreen(
                    metrics = state.bodyMetrics,
                    onMetricValueChange = onBodyMetricValueChange,
                    modifier = Modifier.padding(innerPadding)
                )

                MainTab.Motion -> MotionScreen(modifier = Modifier.padding(innerPadding))

                MainTab.Plan -> {
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
                        label = "planPanels"
                    ) { destination ->
                        when (destination) {
                            PlanDestination.List -> {
                                PlanListPanel(
                                    trainingPlans = state.trainingPlans,
                                    currentPlanId = state.currentPlanId,
                                    onSelectPlan = onSelectPlan,
                                    onEditPlan = onOpenPlanDetail,
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }

                            is PlanDestination.Detail -> {
                                val plan = trainingPlan(state.trainingPlans, destination.planId)

                                if (plan == null) {
                                    PlanListPanel(
                                        trainingPlans = state.trainingPlans,
                                        currentPlanId = state.currentPlanId,
                                        onSelectPlan = onSelectPlan,
                                        onEditPlan = onOpenPlanDetail,
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                } else {
                                    PlanDetailScreen(
                                        plan = plan,
                                        onBack = onBackToPlanList,
                                        onRenamePlan = { updatedName ->
                                            onRenamePlan(destination.planId, updatedName)
                                        },
                                        onMoveMotionUp = { motionEntryId ->
                                            onMoveMotionUp(destination.planId, motionEntryId)
                                        },
                                        onMoveMotionDown = { motionEntryId ->
                                            onMoveMotionDown(destination.planId, motionEntryId)
                                        },
                                        onEditMotion = { motionEntryId ->
                                            onOpenMotionDetail(destination.planId, motionEntryId)
                                        },
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                }
                            }

                            is PlanDestination.Motion -> {
                                val plan = trainingPlan(state.trainingPlans, destination.planId)
                                val motion = plan?.let { planMotion(it, destination.motionEntryId) }

                                if (plan == null || motion == null) {
                                    PlanListPanel(
                                        trainingPlans = state.trainingPlans,
                                        currentPlanId = state.currentPlanId,
                                        onSelectPlan = onSelectPlan,
                                        onEditPlan = onOpenPlanDetail,
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                } else {
                                    MotionDetailScreen(
                                        planName = plan.name,
                                        motion = motion,
                                        onBack = {
                                            onOpenPlanDetail(destination.planId)
                                        },
                                        onDecreaseSets = {
                                            onDecreaseMotionSets(
                                                destination.planId,
                                                destination.motionEntryId,
                                                motion.sets
                                            )
                                        },
                                        onIncreaseSets = {
                                            onIncreaseMotionSets(
                                                destination.planId,
                                                destination.motionEntryId,
                                                motion.sets
                                            )
                                        },
                                        onDecreaseRepsPerSet = {
                                            onDecreaseMotionReps(
                                                destination.planId,
                                                destination.motionEntryId,
                                                motion.repsPerSet
                                            )
                                        },
                                        onIncreaseRepsPerSet = {
                                            onIncreaseMotionReps(
                                                destination.planId,
                                                destination.motionEntryId,
                                                motion.repsPerSet
                                            )
                                        },
                                        onDeleteMotion = {
                                            onRequestMotionDeletion(
                                                destination.planId,
                                                destination.motionEntryId
                                            )
                                        },
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                }
                            }
                        }
                    }
                }

                MainTab.Settings -> SettingsScreen(modifier = Modifier.padding(innerPadding))
            }
        }
    }

    val pendingPlanDelete = state.planIdPendingDelete?.let { planId ->
        trainingPlan(state.trainingPlans, planId)
    }
    if (pendingPlanDelete != null) {
        AlertDialog(
            onDismissRequest = onDismissPlanDeletion,
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
                TextButton(onClick = onConfirmPlanDeletion) {
                    Text(text = stringResource(R.string.plan_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPlanDeletion) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    val pendingMotionDelete = state.motionPendingDelete
    if (pendingMotionDelete != null) {
        val plan = trainingPlan(state.trainingPlans, pendingMotionDelete.planId)
        val motion = plan?.let { planMotion(it, pendingMotionDelete.motionEntryId) }

        if (plan != null && motion != null) {
            AlertDialog(
                onDismissRequest = onDismissMotionDeletion,
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
                    TextButton(onClick = onConfirmMotionDeletion) {
                        Text(text = stringResource(R.string.plan_delete_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissMotionDeletion) {
                        Text(text = stringResource(R.string.common_cancel))
                    }
                }
            )
        }
    }

    val motionPickerPlan = state.addMotionPlanId?.let { planId ->
        trainingPlan(state.trainingPlans, planId)
    }
    if (motionPickerPlan != null) {
        AlertDialog(
            onDismissRequest = onDismissAddMotionPicker,
            title = {
                Text(text = stringResource(R.string.plan_add_motion_dialog_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.availableMotions.forEach { motion ->
                        TextButton(
                            onClick = {
                                onAddMotionToPlan(motionPickerPlan.id, motion)
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
                TextButton(onClick = onDismissAddMotionPicker) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun PlanListPanel(
    trainingPlans: List<TrainingPlanState>,
    currentPlanId: Int,
    onSelectPlan: (Int) -> Unit,
    onEditPlan: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    PlanScreen(
        plans = trainingPlans,
        currentPlanId = currentPlanId,
        onSelectPlan = onSelectPlan,
        onEditPlan = onEditPlan,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeRoutePreview() {
    LiftInsightTheme {
        HomeScaffold(
            state = HomeState(
                bodyMetrics = emptyList(),
                availableMotions = listOf(
                    AvailableMotionState(
                        id = 1,
                        title = "Snatch"
                    )
                ),
                trainingPlans = listOf(
                    TrainingPlanState(
                        id = 1,
                        name = "Strength Base",
                        lastAppliedAt = 1715600000000,
                        motions = listOf(
                            PlanMotionState(
                                entryId = 1,
                                motionId = 1,
                                title = "Snatch",
                                sets = 5,
                                repsPerSet = 2,
                                intensity = 0.82
                            )
                        )
                    )
                ),
                currentPlanId = 1
            ),
            bottomBarItems = emptyList(),
            onTabSelected = {},
            onBodyMetricValueChange = { _, _ -> },
            onCreatePlan = {},
            onSelectPlan = {},
            onOpenPlanDetail = {},
            onBackToPlanList = {},
            onRenamePlan = { _, _ -> },
            onMoveMotionUp = { _, _ -> },
            onMoveMotionDown = { _, _ -> },
            onOpenMotionDetail = { _, _ -> },
            onDecreaseMotionSets = { _, _, _ -> },
            onIncreaseMotionSets = { _, _, _ -> },
            onDecreaseMotionReps = { _, _, _ -> },
            onIncreaseMotionReps = { _, _, _ -> },
            onRequestPlanDeletion = {},
            onDismissPlanDeletion = {},
            onConfirmPlanDeletion = {},
            onRequestMotionDeletion = { _, _ -> },
            onDismissMotionDeletion = {},
            onConfirmMotionDeletion = {},
            onOpenAddMotionPicker = {},
            onDismissAddMotionPicker = {},
            onAddMotionToPlan = { _, _ -> }
        )
    }
}
