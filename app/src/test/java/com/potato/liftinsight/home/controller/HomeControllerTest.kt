package com.potato.liftinsight.home.controller

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.potato.liftinsight.plan.data.TrainingPlanSeedCatalog
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.training.data.LiftInsightDatabase
import kotlinx.coroutines.runBlocking
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
class HomeControllerTest {
    private lateinit var database: LiftInsightDatabase
    private lateinit var trainingPlanStore: TrainingPlanStore
    private lateinit var seedCatalog: TrainingPlanSeedCatalog

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LiftInsightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        trainingPlanStore = TrainingPlanStore.fromDatabase(database)
        seedCatalog = TrainingPlanSeedCatalog(
            availableMotions = listOf(
                AvailableMotionState(id = 1, title = "Snatch"),
                AvailableMotionState(id = 2, title = "Clean & Jerk"),
                AvailableMotionState(id = 3, title = "Snatch Pull"),
                AvailableMotionState(id = 4, title = "Clean Pull"),
                AvailableMotionState(id = 5, title = "Push Press"),
                AvailableMotionState(id = 6, title = "Front Squat"),
                AvailableMotionState(id = 7, title = "Back Squat")
            ),
            debugPlans = listOf(
                TrainingPlanState(
                    id = 1,
                    name = "Strength Base",
                    lastAppliedAt = 1715600000000,
                    currentIndex = 1,
                    motions = listOf(
                        PlanMotionState(entryId = 1, motionId = 1, title = "Snatch", sets = 5, repsPerSet = 2, intensity = 0.82, orderIndex = 1),
                        PlanMotionState(entryId = 2, motionId = 6, title = "Front Squat", sets = 5, repsPerSet = 3, intensity = 0.78, orderIndex = 2),
                        PlanMotionState(entryId = 3, motionId = 3, title = "Snatch Pull", sets = 4, repsPerSet = 3, intensity = 0.9, orderIndex = 3)
                    )
                ),
                TrainingPlanState(
                    id = 2,
                    name = "Competition Peak",
                    lastAppliedAt = 1715800000000,
                    currentIndex = 1,
                    motions = listOf(
                        PlanMotionState(entryId = 1, motionId = 2, title = "Clean & Jerk", sets = 6, repsPerSet = 1, intensity = 0.92, orderIndex = 1),
                        PlanMotionState(entryId = 2, motionId = 1, title = "Snatch", sets = 5, repsPerSet = 1, intensity = 0.88, orderIndex = 2),
                        PlanMotionState(entryId = 3, motionId = 5, title = "Push Press", sets = 4, repsPerSet = 3, intensity = 0.8, orderIndex = 3)
                    )
                ),
                TrainingPlanState(
                    id = 3,
                    name = "Technique Cycle",
                    lastAppliedAt = 1715400000000,
                    currentIndex = 1,
                    motions = listOf(
                        PlanMotionState(entryId = 1, motionId = 1, title = "Snatch", sets = 6, repsPerSet = 2, intensity = 0.74, orderIndex = 1),
                        PlanMotionState(entryId = 2, motionId = 2, title = "Clean & Jerk", sets = 5, repsPerSet = 2, intensity = 0.76, orderIndex = 2),
                        PlanMotionState(entryId = 3, motionId = 4, title = "Clean Pull", sets = 4, repsPerSet = 3, intensity = 0.84, orderIndex = 3)
                    )
                ),
                TrainingPlanState(
                    id = 4,
                    name = "Pull Volume Block",
                    lastAppliedAt = 1715200000000,
                    currentIndex = 1,
                    motions = listOf(
                        PlanMotionState(entryId = 1, motionId = 4, title = "Clean Pull", sets = 5, repsPerSet = 3, intensity = 0.86, orderIndex = 1),
                        PlanMotionState(entryId = 2, motionId = 7, title = "Back Squat", sets = 5, repsPerSet = 5, intensity = 0.8, orderIndex = 2),
                        PlanMotionState(entryId = 3, motionId = 5, title = "Push Press", sets = 4, repsPerSet = 4, intensity = 0.72, orderIndex = 3)
                    )
                )
            ),
            debugCurrentPlanId = 2
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun controller(nowProvider: () -> Long = { 123L }): HomeController {
        return HomeController(
            trainingPlanStore = trainingPlanStore,
            shouldSeedDebugPlans = true,
            nowProvider = nowProvider
        )
    }

    private fun initialState(controller: HomeController): HomeState {
        return runBlocking {
            controller.loadState(seedCatalog)
        }
    }

    @Test
    fun loadState_readsDatabaseSeedAndDefaults() {
        val state = initialState(controller())

        assertEquals(MainTab.Home, state.selectedTab)
        assertEquals(0, state.selectedTabIndex)
        assertEquals(7, state.availableMotions.size)
        assertEquals(4, state.trainingPlans.size)
        assertEquals(2, state.currentPlanId)
        assertEquals(PlanDestination.Overview, state.planDestination)
        assertTrue(state.bodyMetrics.isNotEmpty())
    }

    @Test
    fun selectTab_updatesSelectedMainTab() {
        val controller = controller()
        val motionState = controller.selectTab(initialState(controller), 2)
        val settingsState = controller.selectTab(initialState(controller), 4)

        assertEquals(MainTab.Motion, motionState.selectedTab)
        assertEquals(2, motionState.selectedTabIndex)
        assertEquals(MainTab.Settings, settingsState.selectedTab)
        assertEquals(4, settingsState.selectedTabIndex)
    }

    @Test
    fun selectTab_withInvalidIndexFallsBackToHome() {
        val controller = controller()
        val updatedState = controller.selectTab(initialState(controller), 99)

        assertEquals(MainTab.Home, updatedState.selectedTab)
        assertEquals(0, updatedState.selectedTabIndex)
    }

    @Test
    fun createPlan_persistsPlanAndOpensItsDetail() = runBlocking {
        val controller = controller(nowProvider = { 900L })
        val updatedState = controller.createPlan(initialState(controller), " New Plan ")
        val createdPlan = updatedState.trainingPlans.first { it.name == "New Plan" }

        assertEquals(900L, createdPlan.lastAppliedAt)
        assertEquals(5, updatedState.trainingPlans.size)
        assertEquals(PlanDestination.Detail(createdPlan.id), updatedState.planDestination)
    }

    @Test
    fun selectPlan_updatesCurrentPlanAndTimestamp() = runBlocking {
        val controller = controller(nowProvider = { 777L })
        val updatedState = controller.selectPlan(initialState(controller), 1)

        assertEquals(1, updatedState.currentPlanId)
        assertEquals(777L, updatedState.trainingPlans.first { it.id == 1 }.lastAppliedAt)
    }

    @Test
    fun updatePlanCurrentDay_persistsSelectedCycleDay() = runBlocking {
        val controller = controller()
        val updatedState = controller.updatePlanCurrentDay(
            state = initialState(controller),
            planId = 2,
            dayIndex = 3
        )

        assertEquals(3, updatedState.trainingPlans.first { it.id == 2 }.currentIndex)
    }

    @Test
    fun confirmPlanDeletion_removesPlanClearsDialogAndReturnsToList() = runBlocking {
        val controller = controller()
        val state = controller.requestPlanDeletion(initialState(controller), 2)
        val updatedState = controller.confirmPlanDeletion(state)

        assertFalse(updatedState.trainingPlans.any { it.id == 2 })
        assertEquals(1, updatedState.currentPlanId)
        assertEquals(PlanDestination.List, updatedState.planDestination)
        assertEquals(null, updatedState.planIdPendingDelete)
    }

    @Test
    fun addMotionToPlan_closesPickerAndOpensMotionDetail() = runBlocking {
        val controller = controller()
        val state = controller.openAddMotionPicker(initialState(controller), 1)
        val motion = state.availableMotions.first { it.title == "Snatch" }
        val updatedState = controller.addMotionToPlan(state, 1, motion)
        val updatedPlan = updatedState.trainingPlans.first { it.id == 1 }
        val addedMotionEntryId = updatedPlan.motions.last().entryId

        assertEquals(4, updatedPlan.motions.size)
        assertEquals(1, updatedPlan.motions.last().sets)
        assertEquals(1, updatedPlan.motions.last().repsPerSet)
        assertEquals(0.0, updatedPlan.motions.last().intensity, 0.0)
        assertEquals(null, updatedState.addMotionPlanId)
        assertEquals(
            PlanDestination.Motion(planId = 1, motionEntryId = addedMotionEntryId),
            updatedState.planDestination
        )
    }

    @Test
    fun confirmMotionDeletion_removesMotionAndReturnsToDetail() = runBlocking {
        val controller = controller()
        val state = controller.requestMotionDeletion(initialState(controller), 1, 2)
        val updatedState = controller.confirmMotionDeletion(state)
        val updatedPlan = updatedState.trainingPlans.first { it.id == 1 }

        assertEquals(listOf(1, 3), updatedPlan.motions.map { it.entryId })
        assertEquals(PlanDestination.Detail(1), updatedState.planDestination)
        assertEquals(null, updatedState.motionPendingDelete)
    }
}

