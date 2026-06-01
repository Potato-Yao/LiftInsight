package com.potato.liftinsight.plan.controller

import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.video.VideoProcessor

internal class PlanControllerEnvironment(
    val trainingPlanStore: TrainingPlanStore,
    val shouldSeedDebugPlans: Boolean,
    val nowProvider: () -> Long,
    private val logger: AppLogger,
    val videoProcessor: VideoProcessor
) {
    fun logDebug(message: String) {
        logger.debug(TAG, message)
    }

    fun logWarn(message: String) {
        logger.warn(TAG, message)
    }

    companion object {
        private const val TAG = "PlanController"
    }
}
