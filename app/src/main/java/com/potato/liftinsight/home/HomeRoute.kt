package com.potato.liftinsight.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.potato.liftinsight.R
import com.potato.liftinsight.body.controller.BodyController
import com.potato.liftinsight.body.data.BodyMetricStore
import com.potato.liftinsight.body.model.BodyState
import com.potato.liftinsight.common.BottomBarItem
import com.potato.liftinsight.home.route.MainTab
import com.potato.liftinsight.motion.controller.MotionController
import com.potato.liftinsight.motion.model.MotionState
import com.potato.liftinsight.plan.controller.PlanController
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.plan.data.defaultTrainingPlanSeedCatalog
import com.potato.liftinsight.plan.model.PlanState
import com.potato.liftinsight.training.data.MotionStore
import com.potato.liftinsight.ui.theme.AppThemeMode
import com.potato.liftinsight.video.VideoProcessor

@Composable
fun HomeRoute(
    trainingPlanStore: TrainingPlanStore,
    videoProcessor: VideoProcessor,
    enableDebugPlanSeed: Boolean,
    currentThemeMode: AppThemeMode,
    onThemeModeSelected: (AppThemeMode) -> Unit
) {
    val context = LocalContext.current

    val planController = remember(trainingPlanStore, enableDebugPlanSeed, videoProcessor) {
        PlanController(
            trainingPlanStore = trainingPlanStore,
            shouldSeedDebugPlans = enableDebugPlanSeed,
            videoProcessor = videoProcessor
        )
    }
    val bodyMetricStore = remember(context) { BodyMetricStore.from(context) }
    val bodyController = remember(bodyMetricStore) { BodyController(bodyMetricStore = bodyMetricStore) }
    val motionController = remember(context) {
        MotionController(MotionStore.from(context))
    }

    var planState by remember(planController) {
        mutableStateOf(planController.emptyState())
    }
    var bodyState by remember(bodyController) {
        mutableStateOf(bodyController.emptyState())
    }
    var motionState by remember(motionController) {
        mutableStateOf(motionController.emptyState())
    }
    var selectedTab by remember { mutableStateOf(MainTab.Home) }

    LaunchedEffect(planController, motionController, context) {
        planState = planController.loadState(defaultTrainingPlanSeedCatalog(context))
        motionState = motionController.loadState()
    }

    val bottomBarItems = listOf(
        BottomBarItem(
            label = stringResource(R.string.nav_home),
            icon = Icons.Outlined.Home,
            selectedIcon = Icons.Rounded.Home
        ),
        BottomBarItem(
            label = stringResource(R.string.nav_record),
            icon = Icons.Outlined.FitnessCenter,
            selectedIcon = Icons.Rounded.FitnessCenter
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

    HomeShell(
        planState = planState,
        onPlanStateChange = { planState = it },
        planController = planController,
        bodyState = bodyState,
        onBodyStateChange = { bodyState = it },
        bodyController = bodyController,
        trainingPlanStore = trainingPlanStore,
        videoProcessor = videoProcessor,
        motionState = motionState,
        onMotionStateChange = { motionState = it },
        motionController = motionController,
        selectedTab = selectedTab,
        onStartTraining = { selectedTab = MainTab.Plan },
        currentThemeMode = currentThemeMode,
        bottomBarItems = bottomBarItems,
        onTabSelected = { selectedTab = MainTab.fromIndex(it) },
        onThemeModeSelected = onThemeModeSelected
    )
}
