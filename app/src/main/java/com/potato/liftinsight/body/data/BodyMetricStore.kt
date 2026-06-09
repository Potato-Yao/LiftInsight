package com.potato.liftinsight.body.data

import android.content.Context
import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.training.data.BodyMetricDao
import com.potato.liftinsight.training.data.BodyMetricEntity
import com.potato.liftinsight.training.data.LiftInsightDatabase

class BodyMetricStore private constructor(
    private val bodyMetricDao: BodyMetricDao,
    private val logger: AppLogger
) {
    fun loadMetrics(): List<BodyMetricEntity> {
        val metrics = bodyMetricDao.getAll()
        logDebug("loadMetrics result: count=${metrics.size}")
        return metrics
    }

    fun saveMetrics(metrics: List<BodyMetricEntity>) {
        logDebug("saveMetrics: count=${metrics.size}")
        bodyMetricDao.upsertAll(metrics)
    }

    private fun logDebug(message: String) {
        logger.debug(TAG, message)
    }

    companion object {
        private const val TAG = "BodyMetricStore"

        fun from(context: Context): BodyMetricStore {
            return BodyMetricStore(
                bodyMetricDao = LiftInsightDatabase.from(context).bodyMetricDao(),
                logger = AndroidAppLogger
            )
        }

        internal fun fromDatabase(
            database: LiftInsightDatabase,
            logger: AppLogger = AndroidAppLogger
        ): BodyMetricStore {
            return BodyMetricStore(database.bodyMetricDao(), logger)
        }
    }
}
