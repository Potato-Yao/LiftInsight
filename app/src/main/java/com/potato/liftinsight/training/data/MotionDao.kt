package com.potato.liftinsight.training.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface MotionDao {
    @Insert
    fun insertMotionEntity(motion: MotionEntity): Long

    @Update
    fun updateMotionEntity(motion: MotionEntity): Int

    @Query("DELETE FROM motion WHERE id = :motionId")
    fun deleteMotion(motionId: Int): Int

    @Query("SELECT * FROM motion WHERE id = :motionId")
    fun getMotionEntity(motionId: Int): MotionEntity?

    @Query("SELECT * FROM motion ORDER BY name COLLATE NOCASE ASC, id ASC")
    fun getMotionEntities(): List<MotionEntity>
}


