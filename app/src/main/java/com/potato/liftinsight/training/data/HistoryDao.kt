package com.potato.liftinsight.training.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface HistoryDao {
    @Insert
    fun insertHistory(history: HistoryEntity): Long

    @Update
    fun updateHistory(history: HistoryEntity): Int

    @Query("DELETE FROM history WHERE id = :historyId")
    fun deleteHistory(historyId: Int): Int

    @Query(
        """
        SELECT
            history.id,
            history.plan_id,
            plan.name AS plan_name,
            history.start_time,
            history.end_time,
            history.intensity
        FROM history
        INNER JOIN plan ON plan.id = history.plan_id
        WHERE history.id = :historyId
        """
    )
    fun getHistoryRowById(historyId: Int): HistoryRow?

    @Query(
        """
        SELECT
            history.id,
            history.plan_id,
            plan.name AS plan_name,
            history.start_time,
            history.end_time,
            history.intensity
        FROM history
        INNER JOIN plan ON plan.id = history.plan_id
        ORDER BY history.start_time DESC, history.id DESC
        """
    )
    fun getHistoryRows(): List<HistoryRow>

    @Query("UPDATE metahistory SET history_id = :historyId WHERE id IN (:metaHistoryIds)")
    fun attachMetaHistories(historyId: Int, metaHistoryIds: List<Int>): Int

    @Query("UPDATE metahistory SET history_id = NULL WHERE history_id = :historyId")
    fun detachMetaHistories(historyId: Int): Int

    @Query(
        """
        SELECT id FROM metahistory WHERE history_id = :historyId
        """
    )
    fun getAttachedMetaHistoryIds(historyId: Int): List<Int>
}
