package com.potato.liftinsight.training.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PoseFrameDaoTest {
    private lateinit var database: LiftInsightDatabase
    private lateinit var poseFrameDao: PoseFrameDao
    private lateinit var planDao: PlanDao
    private lateinit var motionDao: MotionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LiftInsightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        poseFrameDao = database.poseFrameDao()
        planDao = database.planDao()
        motionDao = database.motionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAll_andGetPoseFrames_returnsInsertedFrames() {
        val metaHistoryId = createMetaHistory()

        val frames = listOf(
            PoseFrameEntity(metahistoryId = metaHistoryId, timestampMs = 0, landmarksJson = "[{\"t\":0,\"x\":0.5,\"y\":0.3,\"v\":0.9}]"),
            PoseFrameEntity(metahistoryId = metaHistoryId, timestampMs = 100, landmarksJson = "[{\"t\":0,\"x\":0.6,\"y\":0.4,\"v\":0.8}]"),
            PoseFrameEntity(metahistoryId = metaHistoryId, timestampMs = 200, landmarksJson = "[{\"t\":0,\"x\":0.7,\"y\":0.5,\"v\":0.7}]")
        )
        poseFrameDao.insertAll(frames)

        val result = poseFrameDao.getPoseFrames(metaHistoryId)

        assertEquals(3, result.size)
        assertEquals(0L, result[0].timestampMs)
        assertEquals("[{\"t\":0,\"x\":0.5,\"y\":0.3,\"v\":0.9}]", result[0].landmarksJson)
        assertEquals(100L, result[1].timestampMs)
        assertEquals("[{\"t\":0,\"x\":0.6,\"y\":0.4,\"v\":0.8}]", result[1].landmarksJson)
        assertEquals(200L, result[2].timestampMs)
        assertEquals("[{\"t\":0,\"x\":0.7,\"y\":0.5,\"v\":0.7}]", result[2].landmarksJson)
    }

    @Test
    fun getPoseFrames_returnsOrderedByTimestampAsc() {
        val metaHistoryId = createMetaHistory()

        val frames = listOf(
            PoseFrameEntity(metahistoryId = metaHistoryId, timestampMs = 300, landmarksJson = "c"),
            PoseFrameEntity(metahistoryId = metaHistoryId, timestampMs = 100, landmarksJson = "a"),
            PoseFrameEntity(metahistoryId = metaHistoryId, timestampMs = 200, landmarksJson = "b")
        )
        poseFrameDao.insertAll(frames)

        val result = poseFrameDao.getPoseFrames(metaHistoryId)

        assertEquals(3, result.size)
        assertEquals(100L, result[0].timestampMs)
        assertEquals(200L, result[1].timestampMs)
        assertEquals(300L, result[2].timestampMs)
    }

    @Test
    fun deleteByMetaHistoryId_removesAllFrames() {
        val metaHistoryId = createMetaHistory()

        val frames = listOf(
            PoseFrameEntity(metahistoryId = metaHistoryId, timestampMs = 0, landmarksJson = "x"),
            PoseFrameEntity(metahistoryId = metaHistoryId, timestampMs = 100, landmarksJson = "y")
        )
        poseFrameDao.insertAll(frames)

        val deleted = poseFrameDao.deleteByMetaHistoryId(metaHistoryId)

        assertEquals(2, deleted)
        assertEquals(0, poseFrameDao.getPoseFrames(metaHistoryId).size)
    }

    @Test
    fun deleteByMetaHistoryId_onlyDeletesTarget() {
        val metaHistoryId1 = createMetaHistory("Squat")
        val metaHistoryId2 = createMetaHistory("Bench Press")

        poseFrameDao.insertAll(listOf(
            PoseFrameEntity(metahistoryId = metaHistoryId1, timestampMs = 0, landmarksJson = "a")
        ))
        poseFrameDao.insertAll(listOf(
            PoseFrameEntity(metahistoryId = metaHistoryId2, timestampMs = 100, landmarksJson = "b")
        ))

        poseFrameDao.deleteByMetaHistoryId(metaHistoryId1)

        assertEquals(0, poseFrameDao.getPoseFrames(metaHistoryId1).size)
        assertEquals(1, poseFrameDao.getPoseFrames(metaHistoryId2).size)
    }

    private fun createMetaHistory(motionName: String = "Squat"): Int {
        val motionId = motionDao.insertMotionEntity(MotionEntity(name = motionName)).toInt()
        return planDao.insertMetaHistory(
            MetaHistoryEntity(date = "2024-01-01", rep = 5, rpe = 8, weight = 100.0, motionId = motionId)
        ).toInt()
    }
}
