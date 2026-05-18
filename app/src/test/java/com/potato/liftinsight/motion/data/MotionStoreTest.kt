package com.potato.liftinsight.motion.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MotionStoreTest {
    private lateinit var database: MotionDatabase
    private lateinit var motionDao: MotionDao
    private lateinit var motionStore: MotionStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MotionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        motionDao = database.motionDao()
        motionStore = MotionStore.fromDatabase(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createMotion_persistsMotionAndFrames() {
        val motionId = motionStore.createMotion(
            CreateMotionRequest(
                name = "  Snatch Session  ",
                recordedAt = " 2026-05-18T08:30:00Z ",
                notes = "  Smooth pull from the floor  ",
                frames = sampleFrames()
            )
        )

        val storedMotion = motionStore.getMotion(motionId)

        assertEquals("Snatch Session", storedMotion?.name)
        assertEquals("2026-05-18T08:30:00Z", storedMotion?.recordedAt)
        assertEquals("Smooth pull from the floor", storedMotion?.notes)
        assertEquals(listOf(0, 1, 2), storedMotion?.frames?.map { frame -> frame.frameIndex })
        assertEquals(3, motionDao.getFramesForMotion(motionId).size)
        assertEquals(91.5, storedMotion?.frames?.get(1)?.hipAngle ?: Double.NaN, 0.0)
        assertEquals(0.14, storedMotion?.frames?.get(2)?.barbellZ ?: Double.NaN, 0.0)
    }

    @Test
    fun getMotions_returnsNewestRecordedMotionFirst() {
        motionStore.createMotion(
            CreateMotionRequest(
                name = "Morning Clean",
                recordedAt = "2026-05-17T06:00:00Z",
                frames = listOf(frame(frameIndex = 0, time = 0.0))
            )
        )
        motionStore.createMotion(
            CreateMotionRequest(
                name = "Evening Snatch",
                recordedAt = "2026-05-18T19:15:00Z",
                frames = listOf(frame(frameIndex = 0, time = 0.0))
            )
        )

        val motions = motionStore.getMotions()

        assertEquals(listOf("Evening Snatch", "Morning Clean"), motions.map { motion -> motion.name })
    }

    @Test
    fun updateMotion_replacesMetadataAndFrames() {
        val motionId = motionStore.createMotion(
            CreateMotionRequest(
                name = "Clean Pull",
                recordedAt = "2026-05-17T09:00:00Z",
                notes = "First pass",
                frames = listOf(
                    frame(frameIndex = 0, time = 0.0, hipAngle = 110.0),
                    frame(frameIndex = 1, time = 0.04, hipAngle = 101.0)
                )
            )
        )

        val updated = motionStore.updateMotion(
            MotionRecord(
                id = motionId,
                name = "  Clean Pull Updated  ",
                recordedAt = " 2026-05-17T09:05:00Z ",
                notes = "  Better extension  ",
                frames = listOf(
                    frame(frameIndex = 0, time = 0.0, hipAngle = 109.0, trunkAngle = 42.0),
                    frame(frameIndex = 1, time = 0.05, hipAngle = 98.0, trunkAngle = 47.5),
                    frame(frameIndex = 2, time = 0.10, hipAngle = 90.0, trunkAngle = 55.0)
                )
            )
        )

        val storedMotion = motionStore.getMotion(motionId)

        assertTrue(updated)
        assertEquals("Clean Pull Updated", storedMotion?.name)
        assertEquals("2026-05-17T09:05:00Z", storedMotion?.recordedAt)
        assertEquals("Better extension", storedMotion?.notes)
        assertEquals(listOf(0, 1, 2), storedMotion?.frames?.map { frame -> frame.frameIndex })
        assertEquals(55.0, storedMotion?.frames?.last()?.trunkAngle ?: Double.NaN, 0.0)
    }

    @Test
    fun deleteMotion_removesMotionAndItsFrames() {
        val motionId = motionStore.createMotion(
            CreateMotionRequest(
                name = "Front Squat",
                recordedAt = "2026-05-18T12:00:00Z",
                frames = sampleFrames()
            )
        )

        val deleted = motionStore.deleteMotion(motionId)

        assertTrue(deleted)
        assertNull(motionStore.getMotion(motionId))
        assertTrue(motionDao.getFramesForMotion(motionId).isEmpty())
        assertFalse(motionStore.deleteMotion(motionId))
    }

    @Test(expected = IllegalArgumentException::class)
    fun createMotion_rejectsDuplicateFrameIndexes() {
        motionStore.createMotion(
            CreateMotionRequest(
                name = "Duplicate Frames",
                recordedAt = "2026-05-18T20:00:00Z",
                frames = listOf(
                    frame(frameIndex = 0, time = 0.0),
                    frame(frameIndex = 0, time = 0.05)
                )
            )
        )
    }

    private fun sampleFrames(): List<MotionFrameRecord> {
        return listOf(
            frame(
                frameIndex = 2,
                time = 0.08,
                hipAngle = 86.0,
                kneeAngle = 120.0,
                ankleAngle = 72.5,
                spineAngle = 38.0,
                elbowAngle = 176.0,
                trunkAngle = 53.0,
                barbellX = 0.32,
                barbellY = 1.18,
                barbellZ = 0.14
            ),
            frame(
                frameIndex = 0,
                time = 0.0,
                hipAngle = 103.0,
                kneeAngle = 131.0,
                ankleAngle = 79.5,
                spineAngle = 35.0,
                elbowAngle = 178.0,
                trunkAngle = 41.0,
                barbellX = 0.27,
                barbellY = 0.81,
                barbellZ = 0.09
            ),
            frame(
                frameIndex = 1,
                time = 0.04,
                hipAngle = 91.5,
                kneeAngle = 126.0,
                ankleAngle = 75.0,
                spineAngle = 36.5,
                elbowAngle = 177.0,
                trunkAngle = 46.0,
                barbellX = 0.29,
                barbellY = 0.98,
                barbellZ = 0.11
            )
        )
    }

    private fun frame(
        frameIndex: Int,
        time: Double,
        hipAngle: Double? = null,
        kneeAngle: Double? = null,
        ankleAngle: Double? = null,
        spineAngle: Double? = null,
        elbowAngle: Double? = null,
        trunkAngle: Double? = null,
        barbellX: Double? = null,
        barbellY: Double? = null,
        barbellZ: Double? = null
    ): MotionFrameRecord {
        return MotionFrameRecord(
            frameIndex = frameIndex,
            time = time,
            hipAngle = hipAngle,
            kneeAngle = kneeAngle,
            ankleAngle = ankleAngle,
            spineAngle = spineAngle,
            elbowAngle = elbowAngle,
            trunkAngle = trunkAngle,
            barbellX = barbellX,
            barbellY = barbellY,
            barbellZ = barbellZ
        )
    }
}

