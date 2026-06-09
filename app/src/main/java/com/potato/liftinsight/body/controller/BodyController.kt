package com.potato.liftinsight.body.controller

import com.potato.liftinsight.body.data.BodyMetricStore
import com.potato.liftinsight.body.model.BodyMetricState
import com.potato.liftinsight.body.model.BodyState
import com.potato.liftinsight.body.model.defaultBodyMetrics
import com.potato.liftinsight.body.model.updateBodyMetric as applyBodyMetricUpdate
import com.potato.liftinsight.body.route.BodyRoute
import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.training.data.BodyMetricEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BodyController(
    private val bodyMetricStore: BodyMetricStore,
    private val logger: AppLogger = AndroidAppLogger
) {
    fun emptyState(): BodyState {
        return BodyState(bodyMetrics = defaultBodyMetrics())
    }

    suspend fun loadState(): BodyState {
        return withContext(Dispatchers.IO) {
            val defaults = defaultBodyMetrics()
            val stored = bodyMetricStore.loadMetrics()

            if (stored.isEmpty()) {
                return@withContext BodyState(bodyMetrics = defaults)
            }

            val storedMap = stored.associateBy { it.id }
            val merged = defaults.map { default ->
                val storedMetric = storedMap[default.id]
                if (storedMetric != null) {
                    default.copy(
                        value = storedMetric.value,
                        updatedAt = formatTimestamp(storedMetric.updatedAt)
                    )
                } else {
                    default
                }
            }

            BodyState(bodyMetrics = merged)
        }
    }

    fun saveBodyMetrics(state: BodyState): BodyState {
        val entities = state.bodyMetrics.map { metric ->
            BodyMetricEntity(
                id = metric.id,
                value = metric.value,
                updatedAt = System.currentTimeMillis()
            )
        }
        bodyMetricStore.saveMetrics(entities)
        logDebug("saveBodyMetrics: saved ${entities.size} metrics")
        return state
    }

    fun updateBodyMetric(
        state: BodyState,
        metricId: Int,
        newValue: String
    ): BodyState {
        logDebug("Updating body metric: metricId=$metricId")

        val updatedState = state.copy(
            bodyMetrics = applyBodyMetricUpdate(
                metrics = state.bodyMetrics,
                metricId = metricId,
                newValue = newValue
            )
        )

        return saveBodyMetrics(updatedState)
    }

    fun showBodyDetail(state: BodyState): BodyState {
        return state.copy(bodyRoute = BodyRoute.Body)
    }

    fun showTrainingHistory(state: BodyState): BodyState {
        return state.copy(bodyRoute = BodyRoute.Training)
    }

    fun closeBodyDetail(state: BodyState): BodyState {
        return state.copy(bodyRoute = BodyRoute.Overview)
    }

    private fun logDebug(message: String) {
        logger.debug(TAG, message)
    }

    private fun formatTimestamp(timestampMs: Long): String {
        if (timestampMs <= 0L) return "-"
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return formatter.format(java.util.Date(timestampMs))
    }

    companion object {
        private const val TAG = "BodyController"
    }
}
