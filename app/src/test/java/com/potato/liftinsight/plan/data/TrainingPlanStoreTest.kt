package com.potato.liftinsight.plan.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.potato.liftinsight.common.logging.RecordingAppLogger
import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.training.data.LiftInsightDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrainingPlanStoreTest {
    private lateinit var database: LiftInsightDatabase
    private lateinit var logger: RecordingAppLogger
    private lateinit var trainingPlanStore: TrainingPlanStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LiftInsightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        logger = RecordingAppLogger()
        trainingPlanStore = TrainingPlanStore.fromDatabase(database, logger)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ensureAvailableMotions_persistsMotionTitlesForPlanPicker() {
        trainingPlanStore.ensureAvailableMotions(
            listOf(
                AvailableMotionState(
                    id = 1,
                    title = "Snatch"
                ),
                AvailableMotionState(
                    id = 2,
                    title = "Front Squat"
                )
            )
        )

        val motions = trainingPlanStore.getAvailableMotions()

        assertEquals(listOf("Front Squat", "Snatch"), motions.map { motion -> motion.title })
    }

    @Test
    fun seedPlansIfEmpty_createsPlansAndStoresCurrentSelection() {
        trainingPlanStore.ensureAvailableMotions(
            listOf(
                AvailableMotionState(id = 1, title = "Snatch"),
                AvailableMotionState(id = 2, title = "Clean & Jerk")
            )
        )

        trainingPlanStore.seedPlansIfEmpty(
            plans = listOf(
                TrainingPlanState(
                    id = 10,
                    name = "Strength Base",
                    lastAppliedAt = 100L,
                    currentIndex = 1,
                    motions = listOf(
                        PlanMotionState(entryId = 1, motionId = 1, title = "Snatch", dayIndex = 1, sets = 5, repsPerSet = 2, intensity = 0.82, orderIndex = 1)
                    )
                ),
                TrainingPlanState(
                    id = 20,
                    name = "Competition Peak",
                    lastAppliedAt = 200L,
                    currentIndex = 1,
                    motions = listOf(
                        PlanMotionState(entryId = 1, motionId = 2, title = "Clean & Jerk", dayIndex = 1, sets = 6, repsPerSet = 1, intensity = 0.9, orderIndex = 1)
                    )
                )
            ),
            currentPlanId = 20
        )

        val plans = trainingPlanStore.getTrainingPlans()

        assertEquals(listOf("Competition Peak", "Strength Base"), plans.map { it.name })
        assertEquals("Clean & Jerk", plans.first().motions.single().title)
        assertEquals(0.9, plans.first().motions.single().intensity, 0.0)
        assertEquals(0.0, plans.first().motions.single().weight, 0.0)
        assertEquals(7, plans.first().cyclePeriod)
        assertEquals(1, plans.first().currentIndex)
        assertEquals(1, plans.first().motions.single().orderIndex)
        assertEquals(plans.first().id, trainingPlanStore.getCurrentPlanId())
    }

    @Test
    fun updateTrainingPlan_usesDatabaseGeneratedMotionEntryIds() {
        trainingPlanStore.ensureAvailableMotions(
            listOf(
                AvailableMotionState(id = 1, title = "Snatch"),
                AvailableMotionState(id = 2, title = "Front Squat")
            )
        )
        val storedMotions = trainingPlanStore.getAvailableMotions().associateBy { motion -> motion.title }
        val createdPlanId = trainingPlanStore.createTrainingPlan(
            TrainingPlanState(
                id = 0,
                name = "Strength Base",
                lastAppliedAt = 300L,
                cyclePeriod = 10,
                currentIndex = 2
            )
        )

        val initialPlan = TrainingPlanState(
            id = createdPlanId,
            name = "Strength Base",
            lastAppliedAt = 300L,
            cyclePeriod = 10,
            currentIndex = 2,
            motions = listOf(
                PlanMotionState(
                    entryId = 101,
                    motionId = storedMotions.getValue("Snatch").id,
                    title = "Snatch",
                    dayIndex = 1,
                    sets = 5,
                    repsPerSet = 2,
                    intensity = 0.8,
                    weight = 82.5,
                    orderIndex = 1
                ),
                PlanMotionState(
                    entryId = 202,
                    motionId = storedMotions.getValue("Front Squat").id,
                    title = "Front Squat",
                    dayIndex = 1,
                    sets = 4,
                    repsPerSet = 3,
                    intensity = 0.78,
                    weight = 105.0,
                    orderIndex = 2
                )
            )
        )
        trainingPlanStore.updateTrainingPlan(initialPlan)

        val updatedPlan = initialPlan.copy(
            motions = listOf(
                initialPlan.motions[1],
                initialPlan.motions[0].copy(sets = 6),
                PlanMotionState(
                    entryId = 303,
                    motionId = storedMotions.getValue("Snatch").id,
                    title = "Snatch",
                    dayIndex = 1,
                    sets = 3,
                    repsPerSet = 3,
                    intensity = 0.7,
                    weight = 90.0,
                    orderIndex = 3
                )
            )
        )

        val updated = trainingPlanStore.updateTrainingPlan(updatedPlan)
        val reloadedPlan = trainingPlanStore.getTrainingPlan(createdPlanId)

        assertTrue(updated)
        assertEquals(3, reloadedPlan?.motions?.size)
        assertTrue(reloadedPlan?.motions?.all { motion -> motion.entryId > 0 } == true)
        assertFalse(reloadedPlan?.motions?.any { motion -> motion.entryId == 101 || motion.entryId == 202 || motion.entryId == 303 } == true)
        assertEquals(listOf(1, 2, 3), reloadedPlan?.motions?.map { motion -> motion.orderIndex })
        assertEquals(10, reloadedPlan?.cyclePeriod)
        assertEquals(2, reloadedPlan?.currentIndex)
        assertEquals(listOf("Snatch", "Front Squat", "Snatch"), reloadedPlan?.motions?.map { motion -> motion.title })
        assertEquals(listOf(6, 4, 3), reloadedPlan?.motions?.map { motion -> motion.sets })
        assertEquals(listOf(82.5, 105.0, 90.0), reloadedPlan?.motions?.map { motion -> motion.weight })
        assertEquals(listOf(0.8, 0.78, 0.7), reloadedPlan?.motions?.map { motion -> motion.intensity })
    }

    @Test
    fun currentPlanSelection_emitsTraceAndInfoLogs() {
        trainingPlanStore.ensureAvailableMotions(listOf(AvailableMotionState(id = 1, title = "Snatch")))
        val snatch = trainingPlanStore.getAvailableMotions().single()
        val planId = trainingPlanStore.createTrainingPlan(
            TrainingPlanState(
                id = 0,
                name = "Selection Test",
                lastAppliedAt = 0L,
                cyclePeriod = 7,
                currentIndex = 1,
                motions = listOf(
                    PlanMotionState(
                        entryId = 0,
                        motionId = snatch.id,
                        title = snatch.title,
                        dayIndex = 1,
                        sets = 5,
                        repsPerSet = 2,
                        intensity = 0.8,
                        weight = 80.0,
                        orderIndex = 1
                    )
                )
            )
        )

        assertTrue(trainingPlanStore.setCurrentPlan(planId))
        assertEquals(planId, trainingPlanStore.getCurrentPlanId())

        trainingPlanStore.clearCurrentPlan()

        assertEquals(-1, trainingPlanStore.getCurrentPlanId())

        val entries = logger.entries().filter { entry -> entry.tag == "TrainingPlanStore" }

        assertTrue(entries.any { entry -> entry.level == "trace" && entry.message.contains("setCurrentPlan start") && entry.message.contains("planId=$planId") })
        assertTrue(entries.any { entry -> entry.level == "trace" && entry.message.contains("setCurrentPlan result") && entry.message.contains("updated=true") })
        assertTrue(entries.any { entry -> entry.level == "trace" && entry.message.contains("getCurrentPlanId result") && entry.message.contains("currentPlanId=$planId") })
        assertTrue(entries.any { entry -> entry.level == "trace" && entry.message.contains("clearCurrentPlan start") })
        assertTrue(entries.any { entry -> entry.level == "trace" && entry.message.contains("clearCurrentPlan result") })
        assertTrue(entries.any { entry -> entry.level == "trace" && entry.message.contains("getCurrentPlanId result") && entry.message.contains("currentPlanId=-1") })
        assertTrue(entries.any { entry -> entry.level == "info" && entry.message.contains("Updated current training plan selection") })
        assertTrue(entries.any { entry -> entry.level == "info" && entry.message.contains("Cleared current training plan selection") })
    }

    @Test
    fun workoutSession_persistsStartPauseResumeAndStop() {
        val initialSession = trainingPlanStore.getWorkoutSession()

        assertFalse(initialSession.isWorkoutGoing)
        assertFalse(initialSession.isPaused)
        assertEquals(0L, initialSession.elapsedBeforePauseMs)

        trainingPlanStore.startWorkout(startedAt = 1_000L)

        val startedSession = trainingPlanStore.getWorkoutSession()

        assertTrue(startedSession.isWorkoutGoing)
        assertFalse(startedSession.isPaused)
        assertEquals(1_000L, startedSession.startedAt)
        assertEquals(1_000L, startedSession.lastResumedAt)
        assertEquals(0L, startedSession.elapsedBeforePauseMs)

        trainingPlanStore.pauseWorkout(pausedAt = 4_500L)

        val pausedSession = trainingPlanStore.getWorkoutSession()

        assertTrue(pausedSession.isWorkoutGoing)
        assertTrue(pausedSession.isPaused)
        assertEquals(1_000L, pausedSession.startedAt)
        assertEquals(0L, pausedSession.lastResumedAt)
        assertEquals(3_500L, pausedSession.elapsedBeforePauseMs)

        trainingPlanStore.resumeWorkout(resumedAt = 7_000L)

        val resumedSession = trainingPlanStore.getWorkoutSession()

        assertTrue(resumedSession.isWorkoutGoing)
        assertFalse(resumedSession.isPaused)
        assertEquals(1_000L, resumedSession.startedAt)
        assertEquals(7_000L, resumedSession.lastResumedAt)
        assertEquals(3_500L, resumedSession.elapsedBeforePauseMs)

        trainingPlanStore.stopWorkout()

        val stoppedSession = trainingPlanStore.getWorkoutSession()

        assertFalse(stoppedSession.isWorkoutGoing)
        assertFalse(stoppedSession.isPaused)
        assertEquals(0L, stoppedSession.startedAt)
        assertEquals(0L, stoppedSession.lastResumedAt)
        assertEquals(0L, stoppedSession.elapsedBeforePauseMs)
    }
}

