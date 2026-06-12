package com.potato.liftinsight.training.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface BarbellFrameDao {
    @Insert
    fun insertAll(entities: List<BarbellFrameEntity>)

    @Query(
        """
        SELECT * FROM metahistory_barbell_frame
        WHERE metahistory_id = :metahistoryId
        ORDER BY timestamp_ms ASC
        """
    )
    fun getBarbellFrames(metahistoryId: Int): List<BarbellFrameEntity>

    @Query("DELETE FROM metahistory_barbell_frame WHERE metahistory_id = :metahistoryId")
    fun deleteByMetaHistoryId(metahistoryId: Int): Int

    @Query(
        """
        UPDATE metahistory_barbell_frame
        SET x = :x, y = :y, is_manually_edited = 1
        WHERE metahistory_id = :metahistoryId AND timestamp_ms = :timestampMs
        """
    )
    fun updateManualPosition(metahistoryId: Int, timestampMs: Long, x: Float, y: Float): Int
}
