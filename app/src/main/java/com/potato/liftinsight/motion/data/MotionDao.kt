package com.potato.liftinsight.motion.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
abstract class MotionDao {
    @Insert
    protected abstract fun insertMotionEntity(motion: MotionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract fun insertFrameEntities(frames: List<FrameEntity>)

    @Update
    protected abstract fun updateMotionEntity(motion: MotionEntity): Int

    @Query("DELETE FROM motions WHERE id = :motionId")
    abstract fun deleteMotion(motionId: Int): Int

    @Query("DELETE FROM frames WHERE motion_id = :motionId")
    protected abstract fun deleteFramesForMotion(motionId: Int): Int

    @Query("SELECT * FROM motions WHERE id = :motionId")
    protected abstract fun getMotionEntity(motionId: Int): MotionEntity?

    @Query("SELECT * FROM motions ORDER BY recorded_at DESC, id DESC")
    protected abstract fun getMotionEntities(): List<MotionEntity>

    @Query("SELECT * FROM frames WHERE motion_id = :motionId ORDER BY frame_index ASC")
    abstract fun getFramesForMotion(motionId: Int): List<FrameEntity>

    @Query(
        "SELECT * FROM frames WHERE motion_id IN (:motionIds) ORDER BY motion_id ASC, frame_index ASC"
    )
    protected abstract fun getFramesForMotions(motionIds: List<Int>): List<FrameEntity>

    @Transaction
    open fun createMotion(
        motion: MotionEntity,
        frames: List<FrameEntity>
    ): Int {
        val motionId = insertMotionEntity(motion).toInt()

        if (frames.isNotEmpty()) {
            insertFrameEntities(frames.map { frame ->
                frame.copy(motionId = motionId)
            })
        }

        return motionId
    }

    @Transaction
    open fun updateMotion(
        motion: MotionEntity,
        frames: List<FrameEntity>
    ): Boolean {
        val updatedRows = updateMotionEntity(motion)

        if (updatedRows == 0) {
            return false
        }

        deleteFramesForMotion(motion.id)

        if (frames.isNotEmpty()) {
            insertFrameEntities(frames.map { frame ->
                frame.copy(motionId = motion.id)
            })
        }

        return true
    }

    @Transaction
    open fun getMotionRecord(motionId: Int): MotionRecord? {
        val motion = getMotionEntity(motionId) ?: return null
        val frames = getFramesForMotion(motionId)

        return motion.toRecord(frames)
    }

    @Transaction
    open fun getMotionRecords(): List<MotionRecord> {
        val motions = getMotionEntities()

        if (motions.isEmpty()) {
            return emptyList()
        }

        val framesByMotionId = getFramesForMotions(motions.map { motion -> motion.id })
            .groupBy { frame -> frame.motionId }

        return motions.map { motion ->
            motion.toRecord(framesByMotionId[motion.id].orEmpty())
        }
    }
}

