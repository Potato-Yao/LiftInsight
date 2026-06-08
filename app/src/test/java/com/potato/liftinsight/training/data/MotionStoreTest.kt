package com.potato.liftinsight.training.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.potato.liftinsight.common.logging.RecordingAppLogger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class MotionStoreTest {
    private lateinit var database: LiftInsightDatabase
    private lateinit var logger: RecordingAppLogger
    private lateinit var motionStore: MotionStore
    private lateinit var planStore: PlanStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LiftInsightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        logger = RecordingAppLogger()
        motionStore = MotionStore.fromDatabase(database, logger)
        planStore = PlanStore.fromDatabase(database, logger)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createMotion_persistsTrimmedName() {
        val motionId = motionStore.createMotion(
            CreateMotionRequest(
                name = "  Snatch Session  "
            )
        )

        val storedMotion = motionStore.getMotion(motionId)

        assertTrue(motionId > 0)
        assertEquals(motionId, storedMotion?.id)
        assertEquals("Snatch Session", storedMotion?.name)
        assertEquals(MotionType.BARBELL, storedMotion?.type)
    }

    @Test
    fun createMotion_withSpecificType() {
        val motionId = motionStore.createMotion(
            CreateMotionRequest(name = "Cable Fly", type = MotionType.MACHINE_COMPOUND)
        )
        val storedMotion = motionStore.getMotion(motionId)
        assertEquals(MotionType.MACHINE_COMPOUND, storedMotion?.type)
    }

    @Test
    fun getMotions_returnsNamesSortedAlphabetically() {
        motionStore.createMotion(
            CreateMotionRequest(
                name = "Morning Clean"
            )
        )
        motionStore.createMotion(
            CreateMotionRequest(
                name = "Evening Snatch"
            )
        )
        motionStore.createMotion(
            CreateMotionRequest(
                name = "clean pull"
            )
        )

        val motions = motionStore.getMotions()

        assertEquals(
            listOf("clean pull", "Evening Snatch", "Morning Clean"),
            motions.map { motion -> motion.name }
        )
    }

    @Test
    fun updateMotion_replacesName() {
        val motionId = motionStore.createMotion(
            CreateMotionRequest(
                name = "Clean Pull"
            )
        )

        val updated = motionStore.updateMotion(
            MotionRecord(
                id = motionId,
                name = "  Clean Pull Updated  ",
                type = MotionType.BARBELL
            )
        )

        val storedMotion = motionStore.getMotion(motionId)

        assertTrue(updated)
        assertEquals("Clean Pull Updated", storedMotion?.name)
    }

    @Test
    fun deleteMotion_removesUnreferencedMotion() {
        val motionId = motionStore.createMotion(
            CreateMotionRequest(
                name = "Front Squat"
            )
        )

        val deleted = motionStore.deleteMotion(motionId)

        assertTrue(deleted)
        assertNull(motionStore.getMotion(motionId))
        assertFalse(motionStore.deleteMotion(motionId))
    }

    @Test
    fun deleteMotion_returnsFalseWhenMotionIsReferencedByPlan() {
        val motionId = motionStore.createMotion(
            CreateMotionRequest(
                name = "Snatch"
            )
        )
        planStore.createPlan(
            CreatePlanRequest(
                name = "Competition Peak",
                cyclePeriod = 7,
                metaPlans = listOf(
                    CreateMetaPlanRequest(
                        motionId = motionId,
                        sets = 5,
                        reps = 2,
                        intensity = 0.8,
                        weight = 80.0,
                        orderIndex = 1
                    )
                )
            )
        )

        val deleted = motionStore.deleteMotion(motionId)

        assertFalse(deleted)
        assertNotNull(motionStore.getMotion(motionId))
    }

    @Test(expected = IllegalArgumentException::class)
    fun createMotion_rejectsDuplicateNames() {
        motionStore.createMotion(CreateMotionRequest(name = "Snatch"))

        motionStore.createMotion(
            CreateMotionRequest(
                name = "  Snatch  "
            )
        )
    }

    @Test
    fun crudOperations_emitTraceLogs() {
        val motionId = motionStore.createMotion(CreateMotionRequest(name = "Snatch"))
        motionStore.getMotion(motionId)
        motionStore.getMotions()
        motionStore.updateMotion(MotionRecord(id = motionId, name = "Power Snatch", type = MotionType.BARBELL))
        motionStore.deleteMotion(motionId)

        val traceMessages = logger.entries()
            .filter { entry -> entry.level == "trace" && entry.tag == "MotionStore" }
            .map { entry -> entry.message }

        assertTrue(traceMessages.any { message -> message.contains("createMotion start") })
        assertTrue(traceMessages.any { message -> message.contains("createMotion result") && message.contains("motionId=$motionId") })
        assertTrue(traceMessages.any { message -> message.contains("getMotion result") && message.contains("motionId=$motionId") })
        assertTrue(traceMessages.any { message -> message.contains("getMotions result") })
        assertTrue(traceMessages.any { message -> message.contains("updateMotion start") && message.contains("motionId=$motionId") })
        assertTrue(traceMessages.any { message -> message.contains("updateMotion result") && message.contains("updated=true") })
        assertTrue(traceMessages.any { message -> message.contains("deleteMotion start") && message.contains("motionId=$motionId") })
        assertTrue(traceMessages.any { message -> message.contains("deleteMotion result") && message.contains("deleted=true") })
    }
}



