package com.potato.liftinsight.home

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.potato.liftinsight.R
import com.potato.liftinsight.common.BottomBarItem
import com.potato.liftinsight.home.controller.HomeState
import com.potato.liftinsight.home.controller.HomeController
import com.potato.liftinsight.home.controller.MainTab
import com.potato.liftinsight.home.controller.PlanDestination
import com.potato.liftinsight.motion.MotionRouteActions
import com.potato.liftinsight.motion.buildMotionRouteActions
import com.potato.liftinsight.motion.controller.MotionController
import com.potato.liftinsight.motion.model.MotionState
import com.potato.liftinsight.plan.PlanRouteActions
import com.potato.liftinsight.plan.buildPlanRouteActions
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.plan.data.defaultTrainingPlanSeedCatalog
import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.training.data.MotionStore
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

    val planActions = buildPlanRouteActions(
        controller = controller,
        currentState = { state },
        updateState = { updatedState -> state = updatedState },
        coroutineScope = coroutineScope
    )

    val motionActions = buildMotionRouteActions(
        controller = motionController,
        homeController = controller,
        currentMotionState = { motionState },
        updateMotionState = { updatedState -> motionState = updatedState },
        currentHomeState = { state },
        updateHomeState = { updatedState -> state = updatedState },
        coroutineScope = coroutineScope
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
        planActions = planActions,
        motionActions = motionActions
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
            planActions = PlanRouteActions(
                onCreatePlan = {},
                onSelectPlan = {},
                onOpenPlanDetail = {},
                onOpenPlanList = {},
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
                onSubmitPlan = {}
            ),
            motionActions = MotionRouteActions(
                onAddMotion = {},
                onEditMotionLibraryEntry = {},
                onBackFromMotionEditor = {},
                onMotionNameChange = {},
                onSubmitMotion = {},
                onDeleteMotionFromLibrary = {}
            )
        )
    }
}
