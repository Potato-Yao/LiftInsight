package com.potato.liftinsight.motion.controller

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.potato.liftinsight.common.logging.RecordingAppLogger
import com.potato.liftinsight.motion.model.MotionEditorMessage
import com.potato.liftinsight.training.data.CreateMetaPlanRequest
import com.potato.liftinsight.training.data.CreateMotionRequest
import com.potato.liftinsight.training.data.CreatePlanRequest
import com.potato.liftinsight.training.data.LiftInsightDatabase
import com.potato.liftinsight.training.data.MotionStore
import com.potato.liftinsight.training.data.PlanStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MotionControllerTest {
    private lateinit var database: LiftInsightDatabase
    private lateinit var motionStore: MotionStore
    private lateinit var planStore: PlanStore
    private lateinit var logger: RecordingAppLogger
    private lateinit var controller: MotionController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LiftInsightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        motionStore = MotionStore.fromDatabase(database)
        planStore = PlanStore.fromDatabase(database)
        logger = RecordingAppLogger()
        controller = MotionController(motionStore, logger)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun loadState_groupsMotionsByLeadingLetter() = runBlocking {
        motionStore.createMotion(CreateMotionRequest(name = "clean pull"))
        motionStore.createMotion(CreateMotionRequest(name = "Back Squat"))
        motionStore.createMotion(CreateMotionRequest(name = "Bench Press"))
        motionStore.createMotion(CreateMotionRequest(name = "Snatch"))

        val state = controller.loadState()

        assertEquals(listOf("B", "C", "S"), state.sections.map { section -> section.label })
        assertEquals(
            listOf("Back Squat", "Bench Press"),
            state.sections.first().motions.map { motion -> motion.name }
        )
        assertEquals(
            listOf("Back Squat", "Bench Press", "clean pull", "Snatch"),
            state.motions.map { motion -> motion.name }
        )
    }

    @Test
    fun submitMotion_createsNewMotionAndReturnsToList() = runBlocking {
        val createState = controller.openCreateMotion(controller.loadState())
        val editedState = controller.updateEditorName(createState, "  Snatch  ")

        val result = controller.submitMotion(editedState)

        assertTrue(result.didChangeData)
        assertNull(result.state.editor)
        assertEquals(listOf("Snatch"), result.state.motions.map { motion -> motion.name })
        assertEquals(listOf("S"), result.state.sections.map { section -> section.label })
    }

    @Test
    fun submitMotion_updatesExistingMotionName() = runBlocking {
        val motionId = motionStore.createMotion(CreateMotionRequest(name = "Clean Pull"))
        val state = controller.loadState()
        val openEditorState = controller.openEditMotion(state, motionId)
        val renamedState = controller.updateEditorName(openEditorState, "Power Clean")

        val result = controller.submitMotion(renamedState)

        assertTrue(result.didChangeData)
        assertEquals(listOf("Power Clean"), result.state.motions.map { motion -> motion.name })
        assertEquals(listOf("P"), result.state.sections.map { section -> section.label })
    }

    @Test
    fun submitMotion_withDuplicateNameKeepsEditorOpenWithError() = runBlocking {
        motionStore.createMotion(CreateMotionRequest(name = "Snatch"))
        val cleanId = motionStore.createMotion(CreateMotionRequest(name = "Clean"))
        val state = controller.loadState()
        val openEditorState = controller.openEditMotion(state, cleanId)
        val duplicateState = controller.updateEditorName(openEditorState, " Snatch ")

        val result = controller.submitMotion(duplicateState)

        assertFalse(result.didChangeData)
        assertEquals(MotionEditorMessage.DuplicateName, result.state.editor?.message)
        assertEquals(" Snatch ", result.state.editor?.name)
        assertEquals(listOf("Clean", "Snatch"), state.motions.map { motion -> motion.name })
        assertTrue(
            logger.entries().any { entry ->
                entry.level == "error" &&
                    entry.tag == "MotionController" &&
                    entry.message.contains("failed validation") &&
                    entry.throwable is IllegalArgumentException
            }
        )
    }

    @Test
    fun deleteMotion_removesUnreferencedMotionAndReturnsToList() = runBlocking {
        val motionId = motionStore.createMotion(CreateMotionRequest(name = "Front Squat"))
        val state = controller.openEditMotion(controller.loadState(), motionId)

        val result = controller.deleteMotion(state)

        assertTrue(result.didChangeData)
        assertNull(result.state.editor)
        assertTrue(result.state.motions.isEmpty())
    }

    @Test
    fun deleteMotion_whenMotionIsReferencedKeepsEditorOpenWithError() = runBlocking {
        val motionId = motionStore.createMotion(CreateMotionRequest(name = "Snatch"))
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
        val state = controller.openEditMotion(controller.loadState(), motionId)

        val result = controller.deleteMotion(state)

        assertFalse(result.didChangeData)
        assertEquals(MotionEditorMessage.DeleteBlocked, result.state.editor?.message)
        assertEquals(listOf("Snatch"), result.state.motions.map { motion -> motion.name })
    }
}

