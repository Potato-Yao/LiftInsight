package com.potato.liftinsight.record

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.potato.liftinsight.body.BodyScreen
import com.potato.liftinsight.body.controller.BodyController
import com.potato.liftinsight.body.model.BodyState
import com.potato.liftinsight.body.route.BodyRoute
import com.potato.liftinsight.body.route.bodyRouteDepth
import com.potato.liftinsight.camera.CameraCaptureMode
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.record.controller.TrainingHistoryController
import com.potato.liftinsight.ui.theme.LiftInsightMotion
import com.potato.liftinsight.video.VideoProcessor

@Composable
internal fun RecordTabHost(
    bodyState: BodyState,
    onBodyStateChange: (BodyState) -> Unit,
    onBodyMetricValueChange: (Int, String) -> Unit,
    bodyController: BodyController,
    trainingPlanStore: TrainingPlanStore,
    videoProcessor: VideoProcessor,
    trainingHistoryController: TrainingHistoryController,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    cameraCaptureMode: CameraCaptureMode = CameraCaptureMode.Native
) {
    val shouldHandleBodyBack = bodyState.bodyRoute != BodyRoute.Overview

    BackHandler(enabled = shouldHandleBodyBack) {
        onBodyStateChange(bodyController.closeBodyDetail(bodyState))
    }

    AnimatedContent(
        targetState = bodyState.bodyRoute,
        transitionSpec = {
            val direction = if (bodyRouteDepth(targetState) >= bodyRouteDepth(initialState)) 1 else -1

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
        modifier = modifier.padding(contentPadding)
    ) { route ->
        when (route) {
            BodyRoute.Overview -> {
                RecordScreen(
                    onOpenBody = {
                        onBodyStateChange(bodyController.showBodyDetail(bodyState))
                    },
                    onOpenTraining = {
                        onBodyStateChange(bodyController.showTrainingHistory(bodyState))
                    }
                )
            }

            BodyRoute.Body -> {
                BodyScreen(
                    metrics = bodyState.bodyMetrics,
                    onMetricValueChange = onBodyMetricValueChange,
                    onBack = {
                        onBodyStateChange(bodyController.closeBodyDetail(bodyState))
                    }
                )
            }

            BodyRoute.Training -> {
                TrainingHistoryScreen(
                    controller = trainingHistoryController,
                    trainingPlanStore = trainingPlanStore,
                    videoProcessor = videoProcessor,
                    onBack = {
                        onBodyStateChange(bodyController.closeBodyDetail(bodyState))
                    },
                    cameraCaptureMode = cameraCaptureMode
                )
            }
        }
    }
}
