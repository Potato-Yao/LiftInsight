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
import androidx.compose.ui.Modifier
import com.potato.liftinsight.body.BodyScreen
import com.potato.liftinsight.common.BottomBarItem
import com.potato.liftinsight.common.LiftInsightBottomBar
import com.potato.liftinsight.home.controller.HomeState
import com.potato.liftinsight.home.controller.MainTab
import com.potato.liftinsight.motion.MotionRouteActions
import com.potato.liftinsight.motion.MotionScreen
import com.potato.liftinsight.motion.model.MotionState
import com.potato.liftinsight.plan.PlanRouteActions
import com.potato.liftinsight.plan.PlanTabContent
import com.potato.liftinsight.plan.PlanTabDialogs
import com.potato.liftinsight.plan.PlanTabFloatingActionButton
import com.potato.liftinsight.settings.SettingsScreen
import com.potato.liftinsight.ui.theme.LiftInsightMotion

@Composable
internal fun HomeScaffold(
    state: HomeState,
    motionState: MotionState,
    bottomBarItems: List<BottomBarItem>,
    onTabSelected: (Int) -> Unit,
    onBodyMetricValueChange: (Int, String) -> Unit,
    planActions: PlanRouteActions,
    motionActions: MotionRouteActions
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (state.selectedTab == MainTab.Plan) {
                PlanTabFloatingActionButton(
                    state = state,
                    actions = planActions
                )
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
                    onAddMotion = motionActions.onAddMotion,
                    onEditMotion = motionActions.onEditMotionLibraryEntry,
                    onBackFromEditor = motionActions.onBackFromMotionEditor,
                    onMotionNameChange = motionActions.onMotionNameChange,
                    onSubmitMotion = motionActions.onSubmitMotion,
                    onDeleteMotion = motionActions.onDeleteMotionFromLibrary,
                    modifier = Modifier.padding(innerPadding)
                )

                MainTab.Plan -> PlanTabContent(
                    state = state,
                    motionState = motionState,
                    planActions = planActions,
                    motionActions = motionActions,
                    contentPadding = innerPadding
                )

                MainTab.Settings -> SettingsScreen(modifier = Modifier.padding(innerPadding))
            }
        }
    }

    PlanTabDialogs(
        state = state,
        actions = planActions
    )
}


