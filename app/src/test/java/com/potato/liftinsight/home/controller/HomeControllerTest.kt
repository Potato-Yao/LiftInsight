package com.potato.liftinsight.home.controller

import com.potato.liftinsight.home.model.HomeLabels
import com.potato.liftinsight.home.model.defaultHomeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeControllerTest {
    private fun sampleLabels(): HomeLabels {
        return HomeLabels(
            strengthBaseName = "Strength Base",
            competitionPeakName = "Competition Peak",
            techniqueCycleName = "Technique Cycle",
            pullVolumeBlockName = "Pull Volume Block",
            snatchTitle = "Snatch",
            cleanAndJerkTitle = "Clean & Jerk",
            snatchPullTitle = "Snatch Pull",
            cleanPullTitle = "Clean Pull",
            pushPressTitle = "Push Press",
            frontSquatTitle = "Front Squat",
            backSquatTitle = "Back Squat"
        )
    }

    private fun initialState(controller: HomeController = HomeController()): HomeState {
        return controller.createInitialState(defaultHomeCatalog(sampleLabels()))
    }

    @Test
    fun createInitialState_loadsCatalogAndDefaults() {
        val state = initialState()

        assertEquals(0, state.selectedTabIndex)
        assertEquals(7, state.availableMotions.size)
        assertEquals(4, state.trainingPlans.size)
        assertEquals(2, state.currentPlanId)
        assertEquals(PlanDestination.List, state.planDestination)
        assertTrue(state.bodyMetrics.isNotEmpty())
    }

    @Test
    fun createPlan_appendsPlanAndOpensItsDetail() {
        val controller = HomeController(nowProvider = { 900L })
        val updatedState = controller.createPlan(initialState(controller), " New Plan ")
        val createdPlan = updatedState.trainingPlans.last()

        assertEquals("New Plan", createdPlan.name)
        assertEquals(900L, createdPlan.lastAppliedAt)
        assertEquals(5, updatedState.trainingPlans.size)
        assertEquals(PlanDestination.Detail(createdPlan.id), updatedState.planDestination)
    }

    @Test
    fun selectPlan_updatesCurrentPlanAndTimestamp() {
        val controller = HomeController(nowProvider = { 777L })
        val updatedState = controller.selectPlan(initialState(controller), 1)

        assertEquals(1, updatedState.currentPlanId)
        assertEquals(777L, updatedState.trainingPlans.first { it.id == 1 }.lastAppliedAt)
    }

    @Test
    fun confirmPlanDeletion_removesPlanClearsDialogAndReturnsToList() {
        val controller = HomeController()
        val state = controller.requestPlanDeletion(initialState(controller), 2)
        val updatedState = controller.confirmPlanDeletion(state)

        assertFalse(updatedState.trainingPlans.any { it.id == 2 })
        assertEquals(1, updatedState.currentPlanId)
        assertEquals(PlanDestination.List, updatedState.planDestination)
        assertEquals(null, updatedState.planIdPendingDelete)
    }

    @Test
    fun addMotionToPlan_closesPickerAndOpensMotionDetail() {
        val controller = HomeController()
        val state = controller.openAddMotionPicker(initialState(controller), 1)
        val updatedState = controller.addMotionToPlan(state, 1, state.availableMotions.first())
        val updatedPlan = updatedState.trainingPlans.first { it.id == 1 }

        assertEquals(4, updatedPlan.motions.size)
        assertEquals(null, updatedState.addMotionPlanId)
        assertEquals(PlanDestination.Motion(planId = 1, motionEntryId = 4), updatedState.planDestination)
    }

    @Test
    fun confirmMotionDeletion_removesMotionAndReturnsToDetail() {
        val controller = HomeController()
        val state = controller.requestMotionDeletion(initialState(controller), 1, 2)
        val updatedState = controller.confirmMotionDeletion(state)
        val updatedPlan = updatedState.trainingPlans.first { it.id == 1 }

        assertEquals(listOf(1, 3), updatedPlan.motions.map { it.entryId })
        assertEquals(PlanDestination.Detail(1), updatedState.planDestination)
        assertEquals(null, updatedState.motionPendingDelete)
    }
}

