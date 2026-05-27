package com.potato.liftinsight.body.controller

import com.potato.liftinsight.body.model.BodyMetricState
import com.potato.liftinsight.body.model.BodyState
import com.potato.liftinsight.body.model.defaultBodyMetrics
import com.potato.liftinsight.body.model.updateBodyMetric as applyBodyMetricUpdate
import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger

class BodyController(
    private val logger: AppLogger = AndroidAppLogger
) {
    fun emptyState(): BodyState {
        return BodyState(bodyMetrics = defaultBodyMetrics())
    }

    fun updateBodyMetric(
        state: BodyState,
        metricId: Int,
        newValue: String
    ): BodyState {
        logDebug("Updating body metric: metricId=$metricId")

        return state.copy(
            bodyMetrics = applyBodyMetricUpdate(
                metrics = state.bodyMetrics,
                metricId = metricId,
                newValue = newValue
            )
        )
    }

    private fun logDebug(message: String) {
        logger.debug(TAG, message)
    }

    companion object {
        private const val TAG = "BodyController"
    }
}
