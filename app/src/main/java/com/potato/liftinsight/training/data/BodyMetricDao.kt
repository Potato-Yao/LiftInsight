package com.potato.liftinsight.training.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BodyMetricDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(metrics: List<BodyMetricEntity>)

    @Query("SELECT * FROM body_metric")
    fun getAll(): List<BodyMetricEntity>

    @Query("DELETE FROM body_metric")
    fun clearAll(): Int
}
