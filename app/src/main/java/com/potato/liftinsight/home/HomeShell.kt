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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.potato.liftinsight.body.controller.BodyController
import com.potato.liftinsight.body.model.BodyState
import com.potato.liftinsight.camera.CameraCaptureMode
import com.potato.liftinsight.common.BottomBarItem
import com.potato.liftinsight.common.LiftInsightBottomBar
import com.potato.liftinsight.home.route.MainTab
import com.potato.liftinsight.motion.MotionTabHost
import com.potato.liftinsight.motion.controller.MotionController
import com.potato.liftinsight.motion.model.MotionState
import com.potato.liftinsight.plan.PlanTabHost
import com.potato.liftinsight.plan.PlanTabFloatingActionButton
import com.potato.liftinsight.plan.controller.PlanController
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.plan.model.PlanState
import com.potato.liftinsight.plan.route.isPlanRouteFullScreen
import com.potato.liftinsight.record.RecordTabHost
import com.potato.liftinsight.record.controller.TrainingHistoryController
import com.potato.liftinsight.settings.SettingsScreen
import com.potato.liftinsight.ui.theme.AppThemeMode
import com.potato.liftinsight.ui.theme.LiftInsightMotion
import com.potato.liftinsight.video.VideoProcessor
import kotlinx.coroutines.launch

@Composable
internal fun HomeShell(
    planState: PlanState,
    onPlanStateChange: (PlanState) -> Unit,
    planController: PlanController,
    bodyState: BodyState,
    onBodyStateChange: (BodyState) -> Unit,
    onBodyMetricValueChange: (Int, String) -> Unit,
    bodyController: BodyController,
    trainingPlanStore: TrainingPlanStore,
    videoProcessor: VideoProcessor,
    trainingHistoryController: TrainingHistoryController,
    motionState: MotionState,
    onMotionStateChange: (MotionState) -> Unit,
    motionController: MotionController,
    selectedTab: MainTab,
    onStartTraining: () -> Unit,
    currentThemeMode: AppThemeMode,
    bottomBarItems: List<BottomBarItem>,
    onTabSelected: (Int) -> Unit,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    currentCleanupThresholdDays: Int = 30,
    onCleanupThresholdDaysChanged: (Int) -> Unit = {},
    currentCameraCaptureMode: CameraCaptureMode = CameraCaptureMode.Native,
    onCameraCaptureModeChanged: (CameraCaptureMode) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val isFullScreenRoute = isPlanRouteFullScreen(planState.planRoute)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (selectedTab == MainTab.Plan && !isFullScreenRoute) {
                PlanTabFloatingActionButton(
                    planState = planState,
                    onPlanStateChange = onPlanStateChange,
                    planController = planController,
                    coroutineScope = coroutineScope
                )
            }
        },
        bottomBar = {
            if (!isFullScreenRoute) {
                LiftInsightBottomBar(
                    items = bottomBarItems,
                    selectedTabIndex = selectedTab.ordinal,
                    onTabSelected = onTabSelected
                )
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedTab,
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
                MainTab.Home -> {
                    HomeScreen(
                        planState = planState,
                        onStartTraining = onStartTraining,
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                MainTab.Record -> {
                    RecordTabHost(
                        bodyState = bodyState,
                        onBodyStateChange = onBodyStateChange,
                        onBodyMetricValueChange = onBodyMetricValueChange,
                        bodyController = bodyController,
                        trainingPlanStore = trainingPlanStore,
                        videoProcessor = videoProcessor,
                        trainingHistoryController = trainingHistoryController,
                        contentPadding = innerPadding,
                        cameraCaptureMode = currentCameraCaptureMode
                    )
                }

                MainTab.Motion -> {
                    MotionTabHost(
                        motionState = motionState,
                        onMotionStateChange = onMotionStateChange,
                        motionController = motionController,
                        contentPadding = innerPadding,
                        onMotionLibraryChanged = {
                            onPlanStateChange(planController.refreshState(planState))
                        }
                    )
                }

                MainTab.Plan -> {
                    PlanTabHost(
                        planState = planState,
                        onPlanStateChange = onPlanStateChange,
                        planController = planController,
                        motionState = motionState,
                        onMotionStateChange = onMotionStateChange,
                        motionController = motionController,
                        contentPadding = innerPadding,
                        cameraCaptureMode = currentCameraCaptureMode
                    )
                }

                MainTab.Settings -> {
                    SettingsScreen(
                        currentThemeMode = currentThemeMode,
                        onThemeModeSelected = onThemeModeSelected,
                        currentCleanupThresholdDays = currentCleanupThresholdDays,
                        onCleanupThresholdDaysChanged = onCleanupThresholdDaysChanged,
                        currentCameraCaptureMode = currentCameraCaptureMode,
                        onCameraCaptureModeChanged = onCameraCaptureModeChanged,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
