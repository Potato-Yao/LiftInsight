package com.potato.liftinsight.training.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryDaoTest {
    private lateinit var database: LiftInsightDatabase
    private lateinit var historyDao: HistoryDao
    private lateinit var planDao: PlanDao
    private lateinit var motionDao: MotionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LiftInsightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        historyDao = database.historyDao()
        planDao = database.planDao()
        motionDao = database.motionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertHistory_returnsPositiveId() {
        val planId = createPlan("Test Plan")

        val historyId = historyDao.insertHistory(
            HistoryEntity(
                planId = planId,
                startTime = 1000L,
                endTime = 2000L,
                intensity = 7
            )
        )

        assertTrue(historyId > 0)
    }

    @Test
    fun getHistoryRowById_returnsInsertedHistory() {
        val planId = createPlan("Strength Cycle")
        val historyId = historyDao.insertHistory(
            HistoryEntity(
                planId = planId,
                startTime = 5000L,
                endTime = 8000L,
                intensity = 8
            )
        ).toInt()

        val row = historyDao.getHistoryRowById(historyId)

        assertNotNull(row)
        assertEquals(historyId, row!!.id)
        assertEquals(planId, row.planId)
        assertEquals("Strength Cycle", row.planName)
        assertEquals(5000L, row.startTime)
        assertEquals(8000L, row.endTime)
        assertEquals(8, row.intensity)
    }

    @Test
    fun getHistoryRowById_returnsNullForMissingId() {
        val row = historyDao.getHistoryRowById(999)
        assertNull(row)
    }

    @Test
    fun getHistoryRows_returnsAllRowsOrderedByStartTime() {
        val planId = createPlan("Test Plan")
        historyDao.insertHistory(
            HistoryEntity(planId = planId, startTime = 3000L, endTime = 4000L, intensity = 6)
        )
        historyDao.insertHistory(
            HistoryEntity(planId = planId, startTime = 1000L, endTime = 2000L, intensity = 7)
        )
        historyDao.insertHistory(
            HistoryEntity(planId = planId, startTime = 5000L, endTime = 6000L, intensity = 8)
        )

        val rows = historyDao.getHistoryRows()

        assertEquals(3, rows.size)
        assertEquals(5000L, rows[0].startTime)
        assertEquals(3000L, rows[1].startTime)
        assertEquals(1000L, rows[2].startTime)
    }

    @Test
    fun getHistoryRows_returnsEmptyListWhenNoHistory() {
        val rows = historyDao.getHistoryRows()
        assertEquals(0, rows.size)
    }

    @Test
    fun updateHistory_modifiesExistingRecord() {
        val planId = createPlan("Test Plan")
        val historyId = historyDao.insertHistory(
            HistoryEntity(planId = planId, startTime = 1000L, endTime = 2000L, intensity = 5)
        ).toInt()

        val updated = historyDao.updateHistory(
            HistoryEntity(id = historyId, planId = planId, startTime = 1500L, endTime = 2500L, intensity = 9)
        )

        assertEquals(1, updated)
        val row = historyDao.getHistoryRowById(historyId)
        assertNotNull(row)
        assertEquals(1500L, row!!.startTime)
        assertEquals(2500L, row.endTime)
        assertEquals(9, row.intensity)
    }

    @Test
    fun deleteHistory_removesRecord() {
        val planId = createPlan("Test Plan")
        val historyId = historyDao.insertHistory(
            HistoryEntity(planId = planId, startTime = 1000L, endTime = 2000L, intensity = 5)
        ).toInt()

        val deleted = historyDao.deleteHistory(historyId)

        assertEquals(1, deleted)
        assertNull(historyDao.getHistoryRowById(historyId))
    }

    @Test
    fun deleteHistory_returnsZeroForMissingId() {
        val deleted = historyDao.deleteHistory(999)
        assertEquals(0, deleted)
    }

    @Test
    fun attachMetaHistories_linksMetaHistoriesToHistory() {
        val motionId = createMotion("Snatch")
        val planId = createPlan("Test Plan")
        val historyId = historyDao.insertHistory(
            HistoryEntity(planId = planId, startTime = 1000L, endTime = 2000L, intensity = 5)
        ).toInt()

        val metaId1 = planDao.insertMetaHistory(
            MetaHistoryEntity(date = "2024-01-01", rep = 5, rpe = 8, weight = 80.0, motionId = motionId)
        ).toInt()
        val metaId2 = planDao.insertMetaHistory(
            MetaHistoryEntity(date = "2024-01-01", rep = 3, rpe = 9, weight = 90.0, motionId = motionId)
        ).toInt()

        val attached = historyDao.attachMetaHistories(historyId, listOf(metaId1, metaId2))

        assertEquals(2, attached)

        val rows = planDao.getMetaHistoryWithMotions()
        val attached1 = rows.first { it.id == metaId1 }
        val attached2 = rows.first { it.id == metaId2 }
        assertEquals(historyId, attached1.historyId)
        assertEquals(historyId, attached2.historyId)
    }

    @Test
    fun detachMetaHistories_unlinksMetaHistoriesFromHistory() {
        val motionId = createMotion("Snatch")
        val planId = createPlan("Test Plan")
        val historyId = historyDao.insertHistory(
            HistoryEntity(planId = planId, startTime = 1000L, endTime = 2000L, intensity = 5)
        ).toInt()

        val metaId = planDao.insertMetaHistory(
            MetaHistoryEntity(date = "2024-01-01", rep = 5, rpe = 8, weight = 80.0, motionId = motionId, historyId = historyId)
        ).toInt()

        val detached = historyDao.detachMetaHistories(historyId)

        assertEquals(1, detached)

        val row = planDao.getMetaHistoryWithMotions().first { it.id == metaId }
        assertEquals(null, row.historyId)
    }

    @Test
    fun getAttachedMetaHistoryIds_returnsLinkedIds() {
        val motionId = createMotion("Snatch")
        val planId = createPlan("Test Plan")
        val historyId = historyDao.insertHistory(
            HistoryEntity(planId = planId, startTime = 1000L, endTime = 2000L, intensity = 5)
        ).toInt()

        val metaId1 = planDao.insertMetaHistory(
            MetaHistoryEntity(date = "2024-01-01", rep = 5, rpe = 8, weight = 80.0, motionId = motionId, historyId = historyId)
        ).toInt()
        val metaId2 = planDao.insertMetaHistory(
            MetaHistoryEntity(date = "2024-01-02", rep = 3, rpe = 7, weight = 70.0, motionId = motionId, historyId = historyId)
        ).toInt()
        planDao.insertMetaHistory(
            MetaHistoryEntity(date = "2024-01-03", rep = 4, rpe = 6, weight = 60.0, motionId = motionId)
        )

        val attachedIds = historyDao.getAttachedMetaHistoryIds(historyId)

        assertEquals(2, attachedIds.size)
        assertTrue(attachedIds.contains(metaId1))
        assertTrue(attachedIds.contains(metaId2))
    }

    @Test
    fun softDeleteMetaHistory_preservesHistoryIdInBin() {
        val motionId = createMotion("Snatch")
        val planId = createPlan("Test Plan")
        val historyId = historyDao.insertHistory(
            HistoryEntity(planId = planId, startTime = 1000L, endTime = 2000L, intensity = 5)
        ).toInt()

        val metaId = planDao.insertMetaHistory(
            MetaHistoryEntity(
                date = "2024-01-01",
                rep = 5,
                rpe = 8,
                weight = 80.0,
                motionId = motionId,
                historyId = historyId
            )
        ).toInt()

        val deleted = planDao.softDeleteMetaHistory(metaId)
        assertTrue(deleted)

        val binRows = planDao.getMetaHistoryBinRows()
        assertEquals(1, binRows.size)
        assertEquals(historyId, binRows[0].historyId)

        val metaHistoryRows = planDao.getMetaHistoryWithMotions()
        assertEquals(0, metaHistoryRows.size)
    }

    @Test
    fun revertBinRecord_restoresHistoryId() {
        val motionId = createMotion("Snatch")
        val planId = createPlan("Test Plan")
        val historyId = historyDao.insertHistory(
            HistoryEntity(planId = planId, startTime = 1000L, endTime = 2000L, intensity = 5)
        ).toInt()

        planDao.insertMetaHistoryBin(
            MetaHistoryBinEntity(
                date = "2024-01-01",
                rep = 5,
                rpe = 8,
                weight = 80.0,
                motionId = motionId,
                motionName = "Snatch",
                historyId = historyId
            )
        )
        val binId = planDao.getMetaHistoryBinRows()[0].id

        val reverted = planDao.revertBinRecord(binId)
        assertTrue(reverted)

        val metaHistoryRows = planDao.getMetaHistoryWithMotions()
        assertEquals(1, metaHistoryRows.size)
        assertEquals(historyId, metaHistoryRows[0].historyId)

        val binRows = planDao.getMetaHistoryBinRows()
        assertEquals(0, binRows.size)
    }

    private fun createPlan(name: String): Int {
        return planDao.createPlan(
            PlanEntity(name = name, cyclePeriod = 7, currentIndex = 1),
            emptyList()
        )
    }

    private fun createMotion(name: String): Int {
        return motionDao.insertMotionEntity(MotionEntity(name = name)).toInt()
    }
}
