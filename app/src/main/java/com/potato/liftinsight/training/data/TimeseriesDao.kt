package com.potato.liftinsight.training.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TimeseriesDao {
    @Insert
    fun insertAll(entities: List<MetahistoryTimeseriesEntity>)

    @Query(
        """
        SELECT timestamp_ms, value
        FROM metahistory_timeseries
        WHERE metahistory_id = :metahistoryId AND metric_name = :metricName
        ORDER BY timestamp_ms ASC
        """
    )
    fun getTimeSeries(metahistoryId: Int, metricName: String): List<TimeseriesPoint>

    @Query(
        """
        SELECT DISTINCT metric_name
        FROM metahistory_timeseries
        WHERE metahistory_id = :metahistoryId
        """
    )
    fun getAvailableMetrics(metahistoryId: Int): List<String>

    @Query("DELETE FROM metahistory_timeseries WHERE metahistory_id = :metahistoryId")
    fun deleteByMetaHistoryId(metahistoryId: Int): Int

    @Query("DELETE FROM metahistory_timeseries WHERE metahistory_id IN (:metahistoryIds)")
    fun deleteByMetaHistoryIds(metahistoryIds: List<Int>): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM metahistory_timeseries
        WHERE metahistory_id = :metahistoryId
        """
    )
    fun countByMetaHistoryId(metahistoryId: Int): Int

    @Insert
    fun insertAllBin(entities: List<MetahistoryTimeseriesBinEntity>)

    @Query(
        """
        SELECT timestamp_ms, metric_name, value
        FROM metahistory_timeseries_bin
        WHERE original_metahistory_id = :originalMetahistoryId
        """
    )
    fun getBinEntries(originalMetahistoryId: Int): List<BinTimeseriesEntry>

    @Query("DELETE FROM metahistory_timeseries_bin WHERE original_metahistory_id = :originalMetahistoryId")
    fun deleteBinByOriginalMetaHistoryId(originalMetahistoryId: Int): Int
}

data class TimeseriesPoint(
    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,
    val value: Double
)

data class BinTimeseriesEntry(
    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,
    @ColumnInfo(name = "metric_name")
    val metricName: String,
    val value: Double
)
