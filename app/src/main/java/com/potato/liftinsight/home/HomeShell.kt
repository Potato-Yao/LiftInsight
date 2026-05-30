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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.potato.liftinsight.body.BodyScreen
import com.potato.liftinsight.body.controller.BodyController
import com.potato.liftinsight.body.model.BodyState
import com.potato.liftinsight.body.route.BodyRoute
import com.potato.liftinsight.body.route.bodyRouteDepth
import com.potato.liftinsight.common.BottomBarItem
import com.potato.liftinsight.common.LiftInsightBottomBar
import com.potato.liftinsight.home.route.MainTab
import com.potato.liftinsight.motion.MotionScreen
import com.potato.liftinsight.motion.controller.MotionController
import com.potato.liftinsight.motion.model.MotionState
import com.potato.liftinsight.plan.PlanTabHost
import com.potato.liftinsight.plan.PlanTabFloatingActionButton
import com.potato.liftinsight.plan.controller.PlanController
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.plan.model.PlanState
import com.potato.liftinsight.plan.route.isPlanRouteFullScreen
import com.potato.liftinsight.record.RecordScreen
import com.potato.liftinsight.record.TrainingHistoryScreen
import com.potato.liftinsight.settings.SettingsScreen
import com.potato.liftinsight.ui.theme.AppThemeMode
import com.potato.liftinsight.ui.theme.LiftInsightMotion
import kotlinx.coroutines.launch

@Composable
internal fun HomeShell(
    planState: PlanState,
    onPlanStateChange: (PlanState) -> Unit,
    planController: PlanController,
    bodyState: BodyState,
    onBodyStateChange: (BodyState) -> Unit,
    bodyController: BodyController,
    trainingPlanStore: TrainingPlanStore,
    motionState: MotionState,
    onMotionStateChange: (MotionState) -> Unit,
    motionController: MotionController,
    selectedTab: MainTab,
    onStartTraining: () -> Unit,
    currentThemeMode: AppThemeMode,
    bottomBarItems: List<BottomBarItem>,
    onTabSelected: (Int) -> Unit,
    onThemeModeSelected: (AppThemeMode) -> Unit
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
                    val shouldHandleBodyBack = bodyState.bodyRoute != BodyRoute.Overview

                    BackHandler(enabled = shouldHandleBodyBack) {
                        onBodyStateChange(bodyController.closeBodyDetail(bodyState))
                    }

                    AnimatedContent(
                        targetState = bodyState.bodyRoute,
                        transitionSpec = {
                            val direction = if (
                                bodyRouteDepth(targetState) >= bodyRouteDepth(initialState)
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
                        label = "recordPanels",
                        modifier = Modifier.padding(innerPadding)
                    ) { route ->
                        when (route) {
                            BodyRoute.Overview -> {
                                RecordScreen(
                                    onOpenBody = {
                                        onBodyStateChange(
                                            bodyController.showBodyDetail(bodyState)
                                        )
                                    },
                                    onOpenTraining = {
                                        onBodyStateChange(
                                            bodyController.showTrainingHistory(bodyState)
                                        )
                                    }
                                )
                            }

                            BodyRoute.Body -> {
                                BodyScreen(
                                    metrics = bodyState.bodyMetrics,
                                    onMetricValueChange = { metricId, newValue ->
                                        onBodyStateChange(
                                            bodyController.updateBodyMetric(bodyState, metricId, newValue)
                                        )
                                    },
                                    onBack = {
                                        onBodyStateChange(
                                            bodyController.closeBodyDetail(bodyState)
                                        )
                                    }
                                )
                            }

                            BodyRoute.Training -> {
                                TrainingHistoryScreen(
                                    trainingPlanStore = trainingPlanStore,
                                    onBack = {
                                        onBodyStateChange(
                                            bodyController.closeBodyDetail(bodyState)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                MainTab.Motion -> {
                    MotionScreen(
                        state = motionState,
                        onAddMotion = {
                            onMotionStateChange(motionController.openCreateMotion(motionState))
                        },
                        onEditMotion = { motionId ->
                            onMotionStateChange(motionController.openEditMotion(motionState, motionId))
                        },
                        onBackFromEditor = {
                            onMotionStateChange(motionController.closeEditor(motionState))
                        },
                        onMotionNameChange = { name ->
                            onMotionStateChange(motionController.updateEditorName(motionState, name))
                        },
                        onSubmitMotion = {
                            coroutineScope.launch {
                                val result = motionController.submitMotion(motionState)
                                onMotionStateChange(result.state)
                                if (result.didChangeData) {
                                    onPlanStateChange(planController.refreshState(planState))
                                }
                            }
                        },
                        onDeleteMotion = {
                            coroutineScope.launch {
                                val result = motionController.deleteMotion(motionState)
                                onMotionStateChange(result.state)
                                if (result.didChangeData) {
                                    onPlanStateChange(planController.refreshState(planState))
                                }
                            }
                        },
                        modifier = Modifier.padding(innerPadding)
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
                        contentPadding = innerPadding
                    )
                }

                MainTab.Settings -> {
                    SettingsScreen(
                        currentThemeMode = currentThemeMode,
                        onThemeModeSelected = onThemeModeSelected,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
