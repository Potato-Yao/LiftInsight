package com.potato.liftinsight.plan.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
    private lateinit var trainingPlanStore: TrainingPlanStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LiftInsightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        trainingPlanStore = TrainingPlanStore.fromDatabase(database)
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
}

