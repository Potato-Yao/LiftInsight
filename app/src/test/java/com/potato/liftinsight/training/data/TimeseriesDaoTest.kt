package com.potato.liftinsight.training.data

import android.content.Context
import com.potato.liftinsight.training.data.TimeseriesMetric.BARBELL_X
import com.potato.liftinsight.training.data.TimeseriesMetric.KNEE_ANGLE
import com.potato.liftinsight.training.data.TimeseriesMetric.SPINE_ANGLE
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimeseriesDaoTest {
    private lateinit var database: LiftInsightDatabase
    private lateinit var timeseriesDao: TimeseriesDao
    private lateinit var planDao: PlanDao
    private lateinit var motionDao: MotionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LiftInsightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        timeseriesDao = database.timeseriesDao()
        planDao = database.planDao()
        motionDao = database.motionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAll_andGetTimeSeries_returnsInsertedPoints() {
        val metaHistoryId = createMetaHistory()

        val entities = listOf(
            MetahistoryTimeseriesEntity(metahistoryId = metaHistoryId, timestampMs = 0, metricName = KNEE_ANGLE, value = 145.0),
            MetahistoryTimeseriesEntity(metahistoryId = metaHistoryId, timestampMs = 100, metricName = KNEE_ANGLE, value = 130.0),
            MetahistoryTimeseriesEntity(metahistoryId = metaHistoryId, timestampMs = 200, metricName = KNEE_ANGLE, value = 110.0)
        )
        timeseriesDao.insertAll(entities)

        val points = timeseriesDao.getTimeSeries(metaHistoryId, KNEE_ANGLE)

        assertEquals(3, points.size)
        assertEquals(0L, points[0].timestampMs)
        assertEquals(145.0, points[0].value, 0.001)
        assertEquals(100L, points[1].timestampMs)
        assertEquals(130.0, points[1].value, 0.001)
        assertEquals(200L, points[2].timestampMs)
        assertEquals(110.0, points[2].value, 0.001)
    }

    @Test
    fun getTimeSeries_returnsEmptyForMissingMetric() {
        val metaHistoryId = createMetaHistory()

        val points = timeseriesDao.getTimeSeries(metaHistoryId, "nonexistent_metric")

        assertEquals(0, points.size)
    }

    @Test
    fun getAvailableMetrics_returnsDistinctMetricNames() {
        val metaHistoryId = createMetaHistory()

        val entities = listOf(
            MetahistoryTimeseriesEntity(metahistoryId = metaHistoryId, timestampMs = 0, metricName = KNEE_ANGLE, value = 145.0),
            MetahistoryTimeseriesEntity(metahistoryId = metaHistoryId, timestampMs = 0, metricName = SPINE_ANGLE, value = 170.0),
            MetahistoryTimeseriesEntity(metahistoryId = metaHistoryId, timestampMs = 100, metricName = KNEE_ANGLE, value = 130.0),
            MetahistoryTimeseriesEntity(metahistoryId = metaHistoryId, timestampMs = 100, metricName = BARBELL_X, value = 0.5)
        )
        timeseriesDao.insertAll(entities)

        val metrics = timeseriesDao.getAvailableMetrics(metaHistoryId)

        assertEquals(3, metrics.size)
        assertTrue(metrics.contains(KNEE_ANGLE))
        assertTrue(metrics.contains(SPINE_ANGLE))
        assertTrue(metrics.contains(BARBELL_X))
    }

    @Test
    fun deleteByMetaHistoryId_removesAllDataForMetaHistory() {
        val metaHistoryId = createMetaHistory()

        val entities = listOf(
            MetahistoryTimeseriesEntity(metahistoryId = metaHistoryId, timestampMs = 0, metricName = KNEE_ANGLE, value = 145.0),
            MetahistoryTimeseriesEntity(metahistoryId = metaHistoryId, timestampMs = 0, metricName = SPINE_ANGLE, value = 170.0)
        )
        timeseriesDao.insertAll(entities)

        val deleted = timeseriesDao.deleteByMetaHistoryId(metaHistoryId)

        assertEquals(2, deleted)
        assertEquals(0, timeseriesDao.countByMetaHistoryId(metaHistoryId))
    }

    @Test
    fun deleteByMetaHistoryIds_removesDataForMultipleMetaHistories() {
        val metaHistoryId1 = createMetaHistory("Squat")
        val metaHistoryId2 = createMetaHistory("Bench Press")

        timeseriesDao.insertAll(listOf(
            MetahistoryTimeseriesEntity(metahistoryId = metaHistoryId1, timestampMs = 0, metricName = KNEE_ANGLE, value = 145.0),
            MetahistoryTimeseriesEntity(metahistoryId = metaHistoryId2, timestampMs = 0, metricName = KNEE_ANGLE, value = 150.0)
        ))

        val deleted = timeseriesDao.deleteByMetaHistoryIds(listOf(metaHistoryId1, metaHistoryId2))

        assertEquals(2, deleted)
        assertEquals(0, timeseriesDao.countByMetaHistoryId(metaHistoryId1))
        assertEquals(0, timeseriesDao.countByMetaHistoryId(metaHistoryId2))
    }

    @Test
    fun countByMetaHistoryId_returnsCorrectCount() {
        val metaHistoryId = createMetaHistory()

        timeseriesDao.insertAll(listOf(
            MetahistoryTimeseriesEntity(metahistoryId = metaHistoryId, timestampMs = 0, metricName = KNEE_ANGLE, value = 145.0),
            MetahistoryTimeseriesEntity(metahistoryId = metaHistoryId, timestampMs = 100, metricName = KNEE_ANGLE, value = 130.0),
            MetahistoryTimeseriesEntity(metahistoryId = metaHistoryId, timestampMs = 0, metricName = SPINE_ANGLE, value = 170.0)
        ))

        assertEquals(3, timeseriesDao.countByMetaHistoryId(metaHistoryId))
    }

    private fun createMetaHistory(motionName: String = "Squat"): Int {
        val motionId = motionDao.insertMotionEntity(MotionEntity(name = motionName)).toInt()
        return planDao.insertMetaHistory(
            MetaHistoryEntity(date = "2024-01-01", rep = 5, rpe = 8, weight = 100.0, motionId = motionId)
        ).toInt()
    }

    @Test
    fun fkCascade_deletesTimeseriesWhenMetaHistoryDeleted() {
        val metaHistoryId = createMetaHistory()

        timeseriesDao.insertAll(listOf(
            MetahistoryTimeseriesEntity(metahistoryId = metaHistoryId, timestampMs = 0, metricName = KNEE_ANGLE, value = 145.0),
            MetahistoryTimeseriesEntity(metahistoryId = metaHistoryId, timestampMs = 100, metricName = KNEE_ANGLE, value = 130.0)
        ))
        assertEquals(2, timeseriesDao.countByMetaHistoryId(metaHistoryId))

        // Delete the parent metahistory - CASCADE should remove timeseries
        planDao.deleteMetaHistoryById(metaHistoryId)

        assertEquals(0, timeseriesDao.countByMetaHistoryId(metaHistoryId))
    }

    @Test
    fun deleteByMetaHistoryId_returnsZeroForNonExistentId() {
        val deleted = timeseriesDao.deleteByMetaHistoryId(999)
        assertEquals(0, deleted)
    }

    @Test
    fun softDeleteMetaHistory_preservesTimeseriesInBin() {
        val metaHistoryId = createMetaHistory()

        timeseriesDao.insertAll(listOf(
            MetahistoryTimeseriesEntity(metahistoryId = metaHistoryId, timestampMs = 0, metricName = KNEE_ANGLE, value = 145.0),
            MetahistoryTimeseriesEntity(metahistoryId = metaHistoryId, timestampMs = 100, metricName = SPINE_ANGLE, value = 170.0)
        ))

        val deleted = planDao.softDeleteMetaHistory(metaHistoryId)
        assertTrue(deleted)

        // Timeseries should be gone from main table
        assertEquals(0, timeseriesDao.countByMetaHistoryId(metaHistoryId))

        // But preserved in bin
        val binRows = planDao.getMetaHistoryBinRows()
        assertEquals(1, binRows.size)
        val binEntries = timeseriesDao.getBinEntries(binRows[0].id)
        assertEquals(2, binEntries.size)
        assertTrue(binEntries.any { it.metricName == KNEE_ANGLE && it.value == 145.0 })
        assertTrue(binEntries.any { it.metricName == SPINE_ANGLE && it.value == 170.0 })
    }

    @Test
    fun revertBinRecord_restoresTimeseriesFromBin() {
        val metaHistoryId = createMetaHistory()

        timeseriesDao.insertAll(listOf(
            MetahistoryTimeseriesEntity(metahistoryId = metaHistoryId, timestampMs = 0, metricName = KNEE_ANGLE, value = 145.0),
            MetahistoryTimeseriesEntity(metahistoryId = metaHistoryId, timestampMs = 100, metricName = SPINE_ANGLE, value = 170.0)
        ))

        planDao.softDeleteMetaHistory(metaHistoryId)

        val binRows = planDao.getMetaHistoryBinRows()
        assertEquals(1, binRows.size)

        val reverted = planDao.revertBinRecord(binRows[0].id)
        assertTrue(reverted)

        // Metahistory should be restored
        val metaHistoryRows = planDao.getMetaHistoryWithMotions()
        assertEquals(1, metaHistoryRows.size)
        val restoredId = metaHistoryRows[0].id

        // Timeseries should be restored with the new metahistory id
        val restoredPoints = timeseriesDao.getTimeSeries(restoredId, KNEE_ANGLE)
        assertEquals(1, restoredPoints.size)
        assertEquals(145.0, restoredPoints[0].value, 0.001)

        val restoredSpine = timeseriesDao.getTimeSeries(restoredId, SPINE_ANGLE)
        assertEquals(1, restoredSpine.size)
        assertEquals(170.0, restoredSpine[0].value, 0.001)

        // Bin should be cleaned up
        val binEntriesAfter = timeseriesDao.getBinEntries(binRows[0].id)
        assertEquals(0, binEntriesAfter.size)
    }
}
