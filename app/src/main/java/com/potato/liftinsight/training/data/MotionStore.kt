package com.potato.liftinsight.training.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger

class MotionStore private constructor(
    private val motionDao: MotionDao,
    private val logger: AppLogger
) {
    fun createMotion(request: CreateMotionRequest): Int {
        logTrace("createMotion start: requestedName=${request.name.orEmpty().trim()}")

        val motion = MotionEntity(
            name = normalizeRequiredText(request.name, "Motion name")
        )

        try {
            val motionId = motionDao.insertMotionEntity(motion).toInt()

            logTrace("createMotion result: motionId=$motionId, name=${motion.name}")

            return motionId
        } catch (_: SQLiteConstraintException) {
            logTrace("createMotion unique constraint violation: name=${motion.name}")
            throw IllegalArgumentException("Motion name must be unique.")
        }
    }

    fun getMotion(motionId: Int): MotionRecord? {
        if (motionId <= 0) {
            logTrace("getMotion skipped: motionId=$motionId")
            return null
        }

        val motion = motionDao.getMotionEntity(motionId)?.toRecord()

        logTrace("getMotion result: motionId=$motionId, found=${motion != null}")

        return motion
    }

    fun getMotions(): List<MotionRecord> {
        val motions = motionDao.getMotionEntities().map { motion -> motion.toRecord() }

        logTrace("getMotions result: count=${motions.size}")

        return motions
    }

    fun updateMotion(motion: MotionRecord): Boolean {
        logTrace("updateMotion start: motionId=${motion.id}, requestedName=${motion.name.trim()}")

        if (motion.id <= 0) {
            logTrace("updateMotion skipped: motionId=${motion.id}")
            return false
        }

        val updatedMotion = motion.copy(name = normalizeRequiredText(motion.name, "Motion name"))

        try {
            val updated = motionDao.updateMotionEntity(updatedMotion.toEntity()) > 0

            logTrace("updateMotion result: motionId=${motion.id}, updated=$updated")

            return updated
        } catch (_: SQLiteConstraintException) {
            logTrace("updateMotion unique constraint violation: motionId=${motion.id}, name=${updatedMotion.name}")
            throw IllegalArgumentException("Motion name must be unique.")
        }
    }

    fun deleteMotion(motionId: Int): Boolean {
        logTrace("deleteMotion start: motionId=$motionId")

        if (motionId <= 0) {
            logTrace("deleteMotion skipped: motionId=$motionId")
            return false
        }

        return try {
            val deleted = motionDao.deleteMotion(motionId) > 0

            logTrace("deleteMotion result: motionId=$motionId, deleted=$deleted")

            deleted
        } catch (_: SQLiteConstraintException) {
            logTrace("deleteMotion blocked by constraint: motionId=$motionId")
            false
        }
    }

    private fun logTrace(message: String) {
        logger.trace(TAG, message)
    }

    companion object {
        private const val TAG = "MotionStore"

        fun from(context: Context): MotionStore {
            return MotionStore(
                motionDao = LiftInsightDatabase.from(context).motionDao(),
                logger = AndroidAppLogger
            )
        }

        internal fun fromDatabase(
            database: LiftInsightDatabase,
            logger: AppLogger = AndroidAppLogger
        ): MotionStore {
            return MotionStore(database.motionDao(), logger)
        }
    }
}


internal fun normalizeRequiredText(
    value: String?,
    fieldName: String
): String {
    val trimmedValue = value?.trim().orEmpty()

    if (trimmedValue.isEmpty()) {
        throw IllegalArgumentException("$fieldName is required.")
    }

    return trimmedValue
}


