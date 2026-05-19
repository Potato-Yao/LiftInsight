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
                    motions = listOf(
                        PlanMotionState(entryId = 1, motionId = 1, title = "Snatch", sets = 5, repsPerSet = 2, intensity = 0.82)
                    )
                ),
                TrainingPlanState(
                    id = 20,
                    name = "Competition Peak",
                    lastAppliedAt = 200L,
                    motions = listOf(
                        PlanMotionState(entryId = 1, motionId = 2, title = "Clean & Jerk", sets = 6, repsPerSet = 1, intensity = 0.9)
                    )
                )
            ),
            currentPlanId = 20
        )

        val plans = trainingPlanStore.getTrainingPlans()

        assertEquals(listOf("Competition Peak", "Strength Base"), plans.map { it.name })
        assertEquals("Clean & Jerk", plans.first().motions.single().title)
        assertEquals(0.9, plans.first().motions.single().intensity, 0.0)
        assertEquals(plans.first().id, trainingPlanStore.getCurrentPlanId())
    }

    @Test
    fun updateTrainingPlan_preservesMotionEntryIdsAcrossSaves() {
        trainingPlanStore.ensureAvailableMotions(
            listOf(
                AvailableMotionState(id = 1, title = "Snatch"),
                AvailableMotionState(id = 2, title = "Front Squat")
            )
        )
        val storedMotions = trainingPlanStore.getAvailableMotions().associateBy { motion -> motion.title }
        val createdPlanId = trainingPlanStore.createTrainingPlan("Strength Base", 300L)

        val initialPlan = TrainingPlanState(
            id = createdPlanId,
            name = "Strength Base",
            lastAppliedAt = 300L,
            motions = listOf(
                PlanMotionState(
                    entryId = 1,
                    motionId = storedMotions.getValue("Snatch").id,
                    title = "Snatch",
                    sets = 5,
                    repsPerSet = 2,
                    intensity = 0.8
                ),
                PlanMotionState(
                    entryId = 2,
                    motionId = storedMotions.getValue("Front Squat").id,
                    title = "Front Squat",
                    sets = 4,
                    repsPerSet = 3,
                    intensity = 0.78
                )
            )
        )
        trainingPlanStore.updateTrainingPlan(initialPlan)

        val updatedPlan = initialPlan.copy(
            motions = listOf(
                initialPlan.motions[1],
                initialPlan.motions[0].copy(sets = 6),
                PlanMotionState(
                    entryId = 3,
                    motionId = storedMotions.getValue("Snatch").id,
                    title = "Snatch",
                    sets = 3,
                    repsPerSet = 3,
                    intensity = 0.7
                )
            )
        )

        val updated = trainingPlanStore.updateTrainingPlan(updatedPlan)
        val reloadedPlan = trainingPlanStore.getTrainingPlan(createdPlanId)

        assertTrue(updated)
        assertEquals(listOf(2, 1, 3), reloadedPlan?.motions?.map { motion -> motion.entryId })
        assertEquals(6, reloadedPlan?.motions?.get(1)?.sets)
        assertEquals(0.7, reloadedPlan?.motions?.get(2)?.intensity ?: Double.NaN, 0.0)
    }
}

