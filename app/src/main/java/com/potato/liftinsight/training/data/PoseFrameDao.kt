package com.potato.liftinsight.training.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PoseFrameDao {
    @Insert
    fun insertAll(entities: List<PoseFrameEntity>)

    @Query(
        """
        SELECT * FROM metahistory_pose_frame
        WHERE metahistory_id = :metahistoryId
        ORDER BY timestamp_ms ASC
        """
    )
    fun getPoseFrames(metahistoryId: Int): List<PoseFrameEntity>

    @Query("DELETE FROM metahistory_pose_frame WHERE metahistory_id = :metahistoryId")
    fun deleteByMetaHistoryId(metahistoryId: Int): Int
}
