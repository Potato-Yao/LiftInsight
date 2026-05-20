package com.potato.liftinsight.home

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
import androidx.compose.material.icons.rounded.Check
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
import com.potato.liftinsight.motion.controller.MotionController
import com.potato.liftinsight.motion.MotionScreen
import com.potato.liftinsight.motion.model.MotionState
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.plan.data.defaultTrainingPlanSeedCatalog
import com.potato.liftinsight.plan.MotionDetailScreen
import com.potato.liftinsight.plan.PlanEditorScreen
import com.potato.liftinsight.plan.PlanPickerScreen
import com.potato.liftinsight.plan.PlanScreen
import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.plan.model.todaysPlanMotions
import com.potato.liftinsight.plan.model.trainingPlan
import com.potato.liftinsight.training.data.MotionStore
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
    val motionController = remember(context) {
        MotionController(MotionStore.from(context))
    }
    val coroutineScope = rememberCoroutineScope()
    var state by remember(controller) {
        mutableStateOf(controller.emptyState())
    }
    var motionState by remember(motionController) {
        mutableStateOf(motionController.emptyState())
    }

    LaunchedEffect(controller, motionController, context) {
        state = controller.loadState(defaultTrainingPlanSeedCatalog(context))
        motionState = motionController.loadState()
    }

    val shouldHandlePlanBack =
        state.selectedTab == MainTab.Plan &&
            state.planDestination != PlanDestination.Overview &&
            state.planIdPendingDelete == null &&
            state.motionPendingDelete == null

    BackHandler(enabled = shouldHandlePlanBack) {
        coroutineScope.launch {
            state = controller.handlePlanBack(state)
        }
    }

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
        motionState = motionState,
        bottomBarItems = bottomBarItems,
        onTabSelected = { tabIndex ->
            state = controller.selectTab(state, tabIndex)
        },
        onBodyMetricValueChange = { metricId, newValue ->
            state = controller.updateBodyMetric(state, metricId, newValue)
        },
        onCreatePlan = {
            state = controller.createPlan(state)
        },
        onSelectPlan = { planId ->
            coroutineScope.launch {
                state = controller.selectPlan(state, planId)
            }
        },
        onOpenPlanDetail = { planId ->
            state = controller.showPlanDetail(state, planId)
        },
        onOpenPlanEditor = {
            state = controller.showPlanList(state)
        },
        onBackInPlan = {
            coroutineScope.launch {
                state = controller.handlePlanBack(state)
            }
        },
        onRenamePlan = { newName ->
            coroutineScope.launch {
                state = controller.updatePlanEditorTitle(state, newName)
            }
        },
        onUpdatePlanCyclePeriod = { cyclePeriod ->
            coroutineScope.launch {
                state = controller.updatePlanEditorCyclePeriod(state, cyclePeriod)
            }
        },
        onSelectPlanEditorDay = { dayIndex ->
            state = controller.selectPlanEditorDay(state, dayIndex)
        },
        onSelectPlanDay = { planId, dayIndex ->
            coroutineScope.launch {
                state = controller.updatePlanCurrentDay(
                    state = state,
                    planId = planId,
                    dayIndex = dayIndex
                )
            }
        },
        onMoveMotionUp = { motionEntryId ->
            coroutineScope.launch {
                state = controller.movePlanMotion(
                    state = state,
                    motionEntryId = motionEntryId,
                    direction = -1
                )
            }
        },
        onMoveMotionDown = { motionEntryId ->
            coroutineScope.launch {
                state = controller.movePlanMotion(
                    state = state,
                    motionEntryId = motionEntryId,
                    direction = 1
                )
            }
        },
        onOpenMotionDetail = { motionEntryId ->
            state = controller.showMotionDetail(state, motionEntryId)
        },
        onDecreaseMotionSets = { motionEntryId, sets ->
            coroutineScope.launch {
                state = controller.updateMotionSets(
                    state = state,
                    motionEntryId = motionEntryId,
                    sets = sets - 1
                )
            }
        },
        onIncreaseMotionSets = { motionEntryId, sets ->
            coroutineScope.launch {
                state = controller.updateMotionSets(
                    state = state,
                    motionEntryId = motionEntryId,
                    sets = sets + 1
                )
            }
        },
        onDecreaseMotionReps = { motionEntryId, repsPerSet ->
            coroutineScope.launch {
                state = controller.updateMotionRepsPerSet(
                    state = state,
                    motionEntryId = motionEntryId,
                    repsPerSet = repsPerSet - 1
                )
            }
        },
        onIncreaseMotionReps = { motionEntryId, repsPerSet ->
            coroutineScope.launch {
                state = controller.updateMotionRepsPerSet(
                    state = state,
                    motionEntryId = motionEntryId,
                    repsPerSet = repsPerSet + 1
                )
            }
        },
        onUpdateMotionWeight = { motionEntryId, weight ->
            coroutineScope.launch {
                state = controller.updateMotionWeight(
                    state = state,
                    motionEntryId = motionEntryId,
                    weight = weight
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
        onRequestMotionDeletion = { motionEntryId ->
            state = controller.requestMotionDeletion(state, motionEntryId)
        },
        onDismissMotionDeletion = {
            state = controller.cancelMotionDeletion(state)
        },
        onConfirmMotionDeletion = {
            coroutineScope.launch {
                state = controller.confirmMotionDeletion(state)
            }
        },
        onOpenAddMotionPicker = {
            state = controller.openAddMotionPicker(state)
        },
        onDismissAddMotionPicker = {
            state = controller.closeAddMotionPicker(state)
        },
        onAddMotionToPlan = { motion ->
            coroutineScope.launch {
                state = controller.addMotionToPlan(state, motion)
            }
        },
        onSubmitPlan = {
            coroutineScope.launch {
                state = controller.submitPlanEditor(state)
            }
        },
        onAddMotion = {
            motionState = motionController.openCreateMotion(motionState)
        },
        onEditMotionLibraryEntry = { motionId ->
            motionState = motionController.openEditMotion(motionState, motionId)
        },
        onBackFromMotionEditor = {
            motionState = motionController.closeEditor(motionState)
        },
        onMotionNameChange = { name ->
            motionState = motionController.updateEditorName(motionState, name)
        },
        onSubmitMotion = {
            coroutineScope.launch {
                val result = motionController.submitMotion(motionState)
                motionState = result.state

                if (result.didChangeData) {
                    state = controller.refreshState(state)
                }
            }
        },
        onDeleteMotionFromLibrary = {
            coroutineScope.launch {
                val result = motionController.deleteMotion(motionState)
                motionState = result.state

                if (result.didChangeData) {
                    state = controller.refreshState(state)
                }
            }
        }
    )
}

@Composable
private fun HomeScaffold(
    state: HomeState,
    motionState: MotionState,
    bottomBarItems: List<BottomBarItem>,
    onTabSelected: (Int) -> Unit,
    onBodyMetricValueChange: (Int, String) -> Unit,
    onCreatePlan: () -> Unit,
    onSelectPlan: (Int) -> Unit,
    onOpenPlanDetail: (Int) -> Unit,
    onOpenPlanEditor: () -> Unit,
    onBackInPlan: () -> Unit,
    onRenamePlan: (String) -> Unit,
    onUpdatePlanCyclePeriod: (Int?) -> Unit,
    onSelectPlanEditorDay: (Int) -> Unit,
    onSelectPlanDay: (Int, Int) -> Unit,
    onMoveMotionUp: (Int) -> Unit,
    onMoveMotionDown: (Int) -> Unit,
    onOpenMotionDetail: (Int) -> Unit,
    onDecreaseMotionSets: (Int, Int) -> Unit,
    onIncreaseMotionSets: (Int, Int) -> Unit,
    onDecreaseMotionReps: (Int, Int) -> Unit,
    onIncreaseMotionReps: (Int, Int) -> Unit,
    onUpdateMotionWeight: (Int, Double) -> Unit,
    onRequestPlanDeletion: (Int) -> Unit,
    onDismissPlanDeletion: () -> Unit,
    onConfirmPlanDeletion: () -> Unit,
    onRequestMotionDeletion: (Int) -> Unit,
    onDismissMotionDeletion: () -> Unit,
    onConfirmMotionDeletion: () -> Unit,
    onOpenAddMotionPicker: () -> Unit,
    onDismissAddMotionPicker: () -> Unit,
    onAddMotionToPlan: (AvailableMotionState) -> Unit,
    onSubmitPlan: () -> Unit,
    onAddMotion: () -> Unit,
    onEditMotionLibraryEntry: (Int) -> Unit,
    onBackFromMotionEditor: () -> Unit,
    onMotionNameChange: (String) -> Unit,
    onSubmitMotion: () -> Unit,
    onDeleteMotionFromLibrary: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (state.selectedTab == MainTab.Plan) {
                when (state.planDestination) {
                    PlanDestination.Overview -> Unit

                    PlanDestination.List -> {
                        FloatingActionButton(onClick = onCreatePlan) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.plan_add_plan_content_description)
                            )
                        }
                    }

                    PlanDestination.Editor -> {
                        val editor = state.planEditor

                        if (editor != null) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (editor.isNewPlan) {
                                    SmallFloatingActionButton(onClick = onSubmitPlan) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = stringResource(R.string.plan_submit_plan_content_description)
                                        )
                                    }
                                } else if (editor.planId != null) {
                                    SmallFloatingActionButton(
                                        onClick = { onRequestPlanDeletion(editor.planId) },
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = stringResource(R.string.plan_delete_plan_content_description)
                                        )
                                    }
                                }

                                FloatingActionButton(
                                    onClick = onOpenAddMotionPicker
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = stringResource(R.string.plan_add_motion_content_description)
                                    )
                                }
                            }
                        }
                    }

                    PlanDestination.MotionPicker,
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

                MainTab.Motion -> MotionScreen(
                    state = motionState,
                    onAddMotion = onAddMotion,
                    onEditMotion = onEditMotionLibraryEntry,
                    onBackFromEditor = onBackFromMotionEditor,
                    onMotionNameChange = onMotionNameChange,
                    onSubmitMotion = onSubmitMotion,
                    onDeleteMotion = onDeleteMotionFromLibrary,
                    modifier = Modifier.padding(innerPadding)
                )

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
                            PlanDestination.Overview -> {
                                val plan = trainingPlan(state.trainingPlans, state.currentPlanId)

                                PlanOverviewPanel(
                                    currentPlan = plan,
                                    todayMotions = plan?.let(::todaysPlanMotions).orEmpty(),
                                    onEditPlan = onOpenPlanEditor,
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }

                            PlanDestination.List -> {
                                PlanPickerPanel(
                                    trainingPlans = state.trainingPlans,
                                    currentPlanId = state.currentPlanId,
                                    onBack = onBackInPlan,
                                    onSelectPlan = onSelectPlan,
                                    onSelectPlanDay = onSelectPlanDay,
                                    onEditPlan = onOpenPlanDetail,
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }

                            PlanDestination.Editor -> {
                                val editor = state.planEditor

                                if (editor == null) {
                                    PlanPickerPanel(
                                        trainingPlans = state.trainingPlans,
                                        currentPlanId = state.currentPlanId,
                                        onBack = onBackInPlan,
                                        onSelectPlan = onSelectPlan,
                                        onSelectPlanDay = onSelectPlanDay,
                                        onEditPlan = onOpenPlanDetail,
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                } else {
                                    PlanEditorScreen(
                                        editor = editor,
                                        onBack = onBackInPlan,
                                        onUpdateTitle = onRenamePlan,
                                        onUpdateCyclePeriod = onUpdatePlanCyclePeriod,
                                        onSelectDay = onSelectPlanEditorDay,
                                        onMoveMotionUp = { motionEntryId ->
                                            onMoveMotionUp(motionEntryId)
                                        },
                                        onMoveMotionDown = { motionEntryId ->
                                            onMoveMotionDown(motionEntryId)
                                        },
                                        onEditMotion = { motionEntryId ->
                                            onOpenMotionDetail(motionEntryId)
                                        },
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                }
                            }

                            PlanDestination.MotionPicker -> {
                                MotionScreen(
                                    state = motionState,
                                    onAddMotion = onAddMotion,
                                    onEditMotion = onEditMotionLibraryEntry,
                                    onBackFromEditor = onBackFromMotionEditor,
                                    onMotionNameChange = onMotionNameChange,
                                    onSubmitMotion = onSubmitMotion,
                                    onDeleteMotion = onDeleteMotionFromLibrary,
                                    selectionTitle = stringResource(R.string.plan_add_motion_dialog_title),
                                    onBackFromSelection = onDismissAddMotionPicker,
                                    onSelectMotion = { motionId ->
                                        val selectedMotion = state.availableMotions.firstOrNull { motion ->
                                            motion.id == motionId
                                        }

                                        if (selectedMotion != null) {
                                            onAddMotionToPlan(selectedMotion)
                                        }
                                    },
                                    modifier = Modifier.padding(innerPadding)
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
                                        onBack = onBackInPlan,
                                        onSelectPlan = onSelectPlan,
                                        onSelectPlanDay = onSelectPlanDay,
                                        onEditPlan = onOpenPlanDetail,
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                } else {
                                    MotionDetailScreen(
                                        planName = editor.title.ifBlank {
                                            stringResource(R.string.plan_title_placeholder)
                                        },
                                        motion = motion,
                                        onBack = onBackInPlan,
                                        onDecreaseSets = {
                                            onDecreaseMotionSets(destination.motionEntryId, motion.sets)
                                        },
                                        onIncreaseSets = {
                                            onIncreaseMotionSets(destination.motionEntryId, motion.sets)
                                        },
                                        onDecreaseRepsPerSet = {
                                            onDecreaseMotionReps(destination.motionEntryId, motion.repsPerSet)
                                        },
                                        onIncreaseRepsPerSet = {
                                            onIncreaseMotionReps(destination.motionEntryId, motion.repsPerSet)
                                        },
                                        onUpdateWeight = { weight ->
                                            onUpdateMotionWeight(destination.motionEntryId, weight)
                                        },
                                        onDeleteMotion = {
                                            onRequestMotionDeletion(destination.motionEntryId)
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
        val editor = state.planEditor
        val motion = editor?.motions?.firstOrNull { planMotion ->
            planMotion.entryId == pendingMotionDelete.motionEntryId
        }

        if (editor != null && motion != null) {
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
                            editor.title.ifBlank {
                                stringResource(R.string.plan_title_placeholder)
                            }
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
}

@Composable
private fun PlanOverviewPanel(
    currentPlan: TrainingPlanState?,
    todayMotions: List<PlanMotionState>,
    onEditPlan: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlanScreen(
        currentPlan = currentPlan,
        todayMotions = todayMotions,
        onEditPlan = onEditPlan,
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

@Preview(showBackground = true)
@Composable
private fun HomeRoutePreview() {
    LiftInsightTheme {
        HomeScaffold(
            state = HomeState(
                bodyMetrics = emptyList(),
                selectedTab = MainTab.Motion,
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
                        currentIndex = 1,
                        motions = listOf(
                            PlanMotionState(
                                entryId = 1,
                                motionId = 1,
                                title = "Snatch",
                                dayIndex = 1,
                                sets = 5,
                                repsPerSet = 2,
                                intensity = 0.82,
                                orderIndex = 1
                            )
                        )
                    )
                ),
                currentPlanId = 1
            ),
            motionState = MotionState(),
            bottomBarItems = emptyList(),
            onTabSelected = {},
            onBodyMetricValueChange = { _, _ -> },
            onCreatePlan = {},
            onSelectPlan = {},
            onOpenPlanDetail = {},
            onOpenPlanEditor = {},
            onBackInPlan = {},
            onRenamePlan = {},
            onUpdatePlanCyclePeriod = {},
            onSelectPlanEditorDay = {},
            onSelectPlanDay = { _, _ -> },
            onMoveMotionUp = {},
            onMoveMotionDown = {},
            onOpenMotionDetail = {},
            onDecreaseMotionSets = { _, _ -> },
            onIncreaseMotionSets = { _, _ -> },
            onDecreaseMotionReps = { _, _ -> },
            onIncreaseMotionReps = { _, _ -> },
            onUpdateMotionWeight = { _, _ -> },
            onRequestPlanDeletion = {},
            onDismissPlanDeletion = {},
            onConfirmPlanDeletion = {},
            onRequestMotionDeletion = {},
            onDismissMotionDeletion = {},
            onConfirmMotionDeletion = {},
            onOpenAddMotionPicker = {},
            onDismissAddMotionPicker = {},
            onAddMotionToPlan = {},
            onSubmitPlan = {},
            onAddMotion = {},
            onEditMotionLibraryEntry = {},
            onBackFromMotionEditor = {},
            onMotionNameChange = {},
            onSubmitMotion = {},
            onDeleteMotionFromLibrary = {}
        )
    }
}
