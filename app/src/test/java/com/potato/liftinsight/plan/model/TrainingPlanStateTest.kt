package com.potato.liftinsight.plan.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test

class TrainingPlanStateTest {
    private val basePlans = listOf(
        TrainingPlanState(
            id = 1,
            name = "Base",
            lastAppliedAt = 100L,
            motions = listOf(
                PlanMotionState(entryId = 1, motionId = 10, title = "Snatch", sets = 5, repsPerSet = 2, intensity = 0.82),
                PlanMotionState(entryId = 2, motionId = 11, title = "Front Squat", sets = 4, repsPerSet = 3, intensity = 0.78)
            )
        ),
        TrainingPlanState(
            id = 2,
            name = "Peak",
            lastAppliedAt = 200L,
            motions = listOf(
                PlanMotionState(entryId = 1, motionId = 12, title = "Clean & Jerk", sets = 6, repsPerSet = 1, intensity = 0.9)
            )
        )
    )

    @Test
    fun sortPlansByLastApplied_ordersNewestFirst_thenByName() {
        val plans = listOf(
            TrainingPlanState(id = 1, name = "Technique", lastAppliedAt = 100L),
            TrainingPlanState(id = 2, name = "Base", lastAppliedAt = 200L),
            TrainingPlanState(id = 3, name = "Competition", lastAppliedAt = 200L)
        )

        val sortedPlans = sortPlansByLastApplied(plans)

        assertEquals(listOf(2, 3, 1), sortedPlans.map { it.id })
    }

    @Test
    fun selectTrainingPlan_updatesCurrentPlanAndTimestamp() {
        val result = selectTrainingPlan(
            plans = basePlans,
            currentPlanId = 2,
            planId = 1,
            selectedAt = 500L
        )

        assertEquals(1, result.currentPlanId)
        assertEquals(500L, result.plans.first { it.id == 1 }.lastAppliedAt)
        assertEquals(listOf(1, 2), sortPlansByLastApplied(result.plans).map { it.id })
    }

    @Test
    fun selectTrainingPlan_keepsExistingStateWhenPlanDoesNotExist() {
        val result = selectTrainingPlan(
            plans = basePlans,
            currentPlanId = 2,
            planId = 9,
            selectedAt = 500L
        )

        assertEquals(2, result.currentPlanId)
        assertSame(basePlans, result.plans)
    }

    @Test
    fun createTrainingPlan_appendsPlanAndReturnsCreatedId() {
        val result = createTrainingPlan(
            plans = basePlans,
            name = "Technique",
            createdAt = 300L
        )

        assertEquals(3, result.createdPlanId)
        assertEquals(3, result.plans.size)
        assertEquals("Technique", result.plans.last().name)
        assertEquals(300L, result.plans.last().lastAppliedAt)
    }

    @Test
    fun updateTrainingPlanName_updatesOnlyRequestedPlan() {
        val updatedPlans = updateTrainingPlanName(
            plans = basePlans,
            planId = 2,
            newName = "Competition Peak"
        )

        assertEquals("Base", updatedPlans.first { it.id == 1 }.name)
        assertEquals("Competition Peak", updatedPlans.first { it.id == 2 }.name)
    }

    @Test
    fun deleteTrainingPlan_removesRequestedPlanAndFallsBackToNewestRemainingPlan() {
        val result = deleteTrainingPlan(
            plans = basePlans,
            currentPlanId = 2,
            planId = 2
        )

        assertEquals(listOf(1), result.plans.map { it.id })
        assertEquals(1, result.currentPlanId)
    }

    @Test
    fun addMotionToPlan_appendsMotionWithPlanDefaults() {
        val result = addMotionToPlan(
            plans = basePlans,
            planId = 1,
            motion = AvailableMotionState(
                id = 20,
                title = "Push Press"
            )
        )

        val addedMotion = result.plans.first { it.id == 1 }.motions.last()

        assertEquals(3, result.motionEntryId)
        assertEquals("Push Press", addedMotion.title)
        assertEquals(1, addedMotion.sets)
        assertEquals(1, addedMotion.repsPerSet)
        assertEquals(0.0, addedMotion.intensity, 0.0)
    }

    @Test
    fun movePlanMotion_swapsMotionWithPreviousPosition() {
        val updatedPlans = movePlanMotion(
            plans = basePlans,
            planId = 1,
            motionEntryId = 2,
            direction = -1
        )

        assertEquals(
            listOf(2, 1),
            updatedPlans.first { it.id == 1 }.motions.map { it.entryId }
        )
    }

    @Test
    fun updateMotionHelpers_coerceValuesToAtLeastOne() {
        val plansWithUpdatedSets = updateMotionSets(
            plans = basePlans,
            planId = 1,
            motionEntryId = 1,
            sets = 0
        )
        val plansWithUpdatedReps = updateMotionRepsPerSet(
            plans = plansWithUpdatedSets,
            planId = 1,
            motionEntryId = 1,
            repsPerSet = -4
        )
        val updatedMotion = plansWithUpdatedReps.first { it.id == 1 }.motions.first()

        assertEquals(1, updatedMotion.sets)
        assertEquals(1, updatedMotion.repsPerSet)
    }

    @Test
    fun deletePlanMotion_removesMotionAndLookupHelpersReflectTheChange() {
        val updatedPlans = deletePlanMotion(
            plans = basePlans,
            planId = 1,
            motionEntryId = 2
        )
        val updatedPlan = trainingPlan(updatedPlans, 1)

        assertEquals(listOf(1), updatedPlan?.motions?.map { it.entryId })
        assertNull(updatedPlan?.let { planMotion(it, 2) })
    }
}
