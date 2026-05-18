package com.potato.liftinsight.motion.data

import android.content.Context

class MotionStore private constructor(
    private val motionDao: MotionDao
) {
    fun createMotion(request: CreateMotionRequest): Int {
        val preparedFrames = prepareFrames(request.frames)
        val motion = MotionEntity(
            name = normalizeText(request.name),
            recordedAt = normalizeText(request.recordedAt),
            notes = normalizeText(request.notes)
        )

        return motionDao.createMotion(
            motion = motion,
            frames = preparedFrames.map { frame ->
                frame.toEntity(motionId = 0)
            }
        )
    }

    fun getMotion(motionId: Int): MotionRecord? {
        if (motionId <= 0) {
            return null
        }

        return motionDao.getMotionRecord(motionId)
    }

    fun getMotions(): List<MotionRecord> {
        return motionDao.getMotionRecords()
    }

    fun updateMotion(motion: MotionRecord): Boolean {
        if (motion.id <= 0) {
            return false
        }

        val preparedFrames = prepareFrames(motion.frames)

        return motionDao.updateMotion(
            motion = motion.copy(
                name = normalizeText(motion.name),
                recordedAt = normalizeText(motion.recordedAt),
                notes = normalizeText(motion.notes)
            ).toEntity(),
            frames = preparedFrames.map { frame ->
                frame.toEntity(motionId = motion.id)
            }
        )
    }

    fun deleteMotion(motionId: Int): Boolean {
        if (motionId <= 0) {
            return false
        }

        return motionDao.deleteMotion(motionId) > 0
    }

    companion object {
        fun from(context: Context): MotionStore {
            return MotionStore(MotionDatabase.from(context).motionDao())
        }

        internal fun fromDatabase(database: MotionDatabase): MotionStore {
            return MotionStore(database.motionDao())
        }
    }
}

private fun normalizeText(value: String?): String? {
    if (value == null) {
        return null
    }

    val trimmedValue = value.trim()

    if (trimmedValue.isEmpty()) {
        return null
    }

    return trimmedValue
}

private fun prepareFrames(frames: List<MotionFrameRecord>): List<MotionFrameRecord> {
    if (frames.isEmpty()) {
        return emptyList()
    }

    val sortedFrames = frames.sortedBy { frame -> frame.frameIndex }

    for (index in 1 until sortedFrames.size) {
        val previousFrame = sortedFrames[index - 1]
        val currentFrame = sortedFrames[index]

        if (previousFrame.frameIndex == currentFrame.frameIndex) {
            throw IllegalArgumentException("Frame indexes must be unique within one motion.")
        }
    }

    return sortedFrames
}

