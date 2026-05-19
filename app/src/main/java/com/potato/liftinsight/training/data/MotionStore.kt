package com.potato.liftinsight.training.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException

class MotionStore private constructor(
    private val motionDao: MotionDao
) {
    fun createMotion(request: CreateMotionRequest): Int {
        val motion = MotionEntity(name = normalizeRequiredText(request.name, "Motion name"))

        try {
            return motionDao.insertMotionEntity(motion).toInt()
        } catch (_: SQLiteConstraintException) {
            throw IllegalArgumentException("Motion name must be unique.")
        }
    }

    fun getMotion(motionId: Int): MotionRecord? {
        if (motionId <= 0) {
            return null
        }

        return motionDao.getMotionEntity(motionId)?.toRecord()
    }

    fun getMotions(): List<MotionRecord> {
        return motionDao.getMotionEntities().map { motion -> motion.toRecord() }
    }

    fun updateMotion(motion: MotionRecord): Boolean {
        if (motion.id <= 0) {
            return false
        }

        val updatedMotion = motion.copy(name = normalizeRequiredText(motion.name, "Motion name"))

        try {
            return motionDao.updateMotionEntity(updatedMotion.toEntity()) > 0
        } catch (_: SQLiteConstraintException) {
            throw IllegalArgumentException("Motion name must be unique.")
        }
    }

    fun deleteMotion(motionId: Int): Boolean {
        if (motionId <= 0) {
            return false
        }

        return try {
            motionDao.deleteMotion(motionId) > 0
        } catch (_: SQLiteConstraintException) {
            false
        }
    }

    companion object {
        fun from(context: Context): MotionStore {
            return MotionStore(LiftInsightDatabase.from(context).motionDao())
        }

        internal fun fromDatabase(database: LiftInsightDatabase): MotionStore {
            return MotionStore(database.motionDao())
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


