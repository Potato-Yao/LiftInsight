package com.potato.liftinsight.plan.controller

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.potato.liftinsight.plan.data.TrainingPlanSeedCatalog
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.SessionIntensityDisplay
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.WorkoutSessionFeeling
import com.potato.liftinsight.training.data.CreateHistoryRequest
import com.potato.liftinsight.training.data.LiftInsightDatabase
import kotlinx.coroutines.runBlocking
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
class PlanControllerStateSupportTest {
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
                AvailableMotionState(id = 3, title = "Front Squat")
            ),
            samplePlans = listOf(
                TrainingPlanState(
                    id = 1,
                    name = "Test Plan",
                    lastAppliedAt = 1_000L,
                    currentIndex = 1,
                    cyclePeriod = 7,
                    motions = listOf(
                        PlanMotionState(entryId = 1, motionId = 1, title = "Snatch", dayIndex = 1, sets = 1, repsPerSet = 1, intensity = 0.8, orderIndex = 1)
                    )
                )
            ),
            sampleCurrentPlanId = 1
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun controller(nowProvider: () -> Long = { 1_000L }): PlanController {
        return PlanController(
            trainingPlanStore = trainingPlanStore,
            nowProvider = nowProvider
        )
    }

    private fun loadInitialState(nowProvider: () -> Long = { 1_000L }): com.potato.liftinsight.plan.model.PlanState {
        return runBlocking {
            controller(nowProvider).loadState(seedCatalog)
        }
    }

    // Case 1: Workout finished with intensity → returns today's intensity
    @Test
    fun sessionIntensity_whenWorkoutFinishedWithIntensity_returnsTodaysIntensity() = runBlocking {
        val ctrl = controller()
        val initial = loadInitialState()

        // Start workout
        val started = ctrl.startWorkout(initial)
        val totalSets = started.workoutProgress!!.totalSetCount

        // Skip all sets to finish the workout
        var state = started
        for (i in 1..totalSets) {
            state = ctrl.skipWorkoutSet(state)
        }

        // Submit workout feeling to set intensity
        val stateWithFeeling = ctrl.submitWorkoutFeeling(state, WorkoutSessionFeeling.Hard)

        val display = stateWithFeeling.sessionIntensityDisplay
        assertTrue(display is SessionIntensityDisplay.Available)
        val available = display as SessionIntensityDisplay.Available
        assertTrue(available.isFromToday)
        assertTrue(available.intensity > 0)
    }

    // Case 2: Workout not finished, 4 history records for same dayIndex → returns average of top 3
    @Test
    fun sessionIntensity_whenMultipleHistoryRecordsForSameDay_returnsAverageOfTop3() = runBlocking {
        val ctrl = controller()
        // Load state first so the plan exists in the database
        loadInitialState()

        // Insert 4 history records for dayIndex=1 (the current plan day)
        trainingPlanStore.createHistoryRecord(
            CreateHistoryRequest(planId = 1, startTime = 4_000L, endTime = 5_000L, intensity = 100, dayIndex = 1)
        )
        trainingPlanStore.createHistoryRecord(
            CreateHistoryRequest(planId = 1, startTime = 3_000L, endTime = 4_000L, intensity = 80, dayIndex = 1)
        )
        trainingPlanStore.createHistoryRecord(
            CreateHistoryRequest(planId = 1, startTime = 2_000L, endTime = 3_000L, intensity = 60, dayIndex = 1)
        )
        trainingPlanStore.createHistoryRecord(
            CreateHistoryRequest(planId = 1, startTime = 1_000L, endTime = 2_000L, intensity = 40, dayIndex = 1)
        )

        val state = ctrl.refreshState(loadInitialState())

        val display = state.sessionIntensityDisplay
        assertTrue(display is SessionIntensityDisplay.Available)
        val available = display as SessionIntensityDisplay.Available
        // Top 3 by startTime DESC: 100, 80, 60 → average = 80
        assertEquals(80, available.intensity)
        assertEquals(false, available.isFromToday)
    }

    // Case 3: Only 1 record exists → returns that record's intensity
    @Test
    fun sessionIntensity_whenSingleHistoryRecord_returnsThatIntensity() = runBlocking {
        val ctrl = controller()
        loadInitialState()

        trainingPlanStore.createHistoryRecord(
            CreateHistoryRequest(planId = 1, startTime = 1_000L, endTime = 2_000L, intensity = 75, dayIndex = 1)
        )

        val state = ctrl.refreshState(loadInitialState())

        val display = state.sessionIntensityDisplay
        assertTrue(display is SessionIntensityDisplay.Available)
        val available = display as SessionIntensityDisplay.Available
        assertEquals(75, available.intensity)
        assertEquals(false, available.isFromToday)
    }

    // Case 4: No records and no workout → returns NotAvailable
    @Test
    fun sessionIntensity_whenNoRecordsAndNoWorkout_returnsNotAvailable() = runBlocking {
        val state = loadInitialState()

        assertEquals(SessionIntensityDisplay.NotAvailable, state.sessionIntensityDisplay)
    }

    // Case 5: Records exist for different dayIndex → returns NotAvailable
    @Test
    fun sessionIntensity_whenRecordsForDifferentDay_returnsNotAvailable() = runBlocking {
        val ctrl = controller()
        loadInitialState()

        // Current plan day is 1, insert records for day 3
        trainingPlanStore.createHistoryRecord(
            CreateHistoryRequest(planId = 1, startTime = 1_000L, endTime = 2_000L, intensity = 90, dayIndex = 3)
        )
        trainingPlanStore.createHistoryRecord(
            CreateHistoryRequest(planId = 1, startTime = 2_000L, endTime = 3_000L, intensity = 85, dayIndex = 3)
        )

        val state = ctrl.refreshState(loadInitialState())

        assertEquals(SessionIntensityDisplay.NotAvailable, state.sessionIntensityDisplay)
    }

    // Case 6: Workout finished but intensity null → falls through to historical logic
    @Test
    fun sessionIntensity_whenFinishedWorkoutHasNullIntensity_fallsThroughToHistory() = runBlocking {
        val ctrl = controller()
        loadInitialState()

        // Insert history records for dayIndex=1
        trainingPlanStore.createHistoryRecord(
            CreateHistoryRequest(planId = 1, startTime = 2_000L, endTime = 3_000L, intensity = 50, dayIndex = 1)
        )
        trainingPlanStore.createHistoryRecord(
            CreateHistoryRequest(planId = 1, startTime = 1_000L, endTime = 2_000L, intensity = 30, dayIndex = 1)
        )

        val initial = loadInitialState()

        // Start workout and finish all sets without submitting feeling
        val started = ctrl.startWorkout(initial)
        val totalSets = started.workoutProgress!!.totalSetCount

        var state = started
        for (i in 1..totalSets) {
            state = ctrl.skipWorkoutSet(state)
        }

        // At this point, workout is finished but workoutIntensity is null
        // The session is also stopped (stopWorkout was called), but progress remains
        // Calling refreshState to get a clean reload
        val refreshedState = ctrl.refreshState(state)

        val display = refreshedState.sessionIntensityDisplay
        assertTrue(display is SessionIntensityDisplay.Available)
        val available = display as SessionIntensityDisplay.Available
        // The workout creates a history record with intensity=0 (same startTime=1000)
        // Top 3 by startTime DESC: inserted(50 at 2000), inserted(30 at 1000), workout(0 at 1000)
        // Average = (50 + 30 + 0) / 3 = 26.67 → 27
        assertEquals(27, available.intensity)
        assertEquals(false, available.isFromToday)
    }
}
