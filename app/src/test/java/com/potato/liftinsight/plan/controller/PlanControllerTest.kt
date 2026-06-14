package com.potato.liftinsight.plan.controller

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.common.logging.RecordingAppLogger
import com.potato.liftinsight.plan.data.TrainingPlanSeedCatalog
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.plan.model.AvailableMotionState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.PlanState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.plan.model.WorkoutSetFeeling
import com.potato.liftinsight.plan.model.WorkoutSetPerformanceInput
import com.potato.liftinsight.plan.route.PlanRoute
import com.potato.liftinsight.training.data.MotionStore
import com.potato.liftinsight.training.data.LiftInsightDatabase
import com.potato.liftinsight.training.data.VideoProcessState
import com.potato.liftinsight.training.data.BarbellFrameEntity
import com.potato.liftinsight.video.DrawingOptions
import com.potato.liftinsight.video.VideoProcessingStatus
import com.potato.liftinsight.video.VideoProcessor
import kotlinx.coroutines.runBlocking
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
class PlanControllerTest {
    private lateinit var database: LiftInsightDatabase
    private lateinit var trainingPlanStore: TrainingPlanStore
    private lateinit var motionStore: MotionStore
    private lateinit var seedCatalog: TrainingPlanSeedCatalog

    private val millisPerDay = 86_400_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LiftInsightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        trainingPlanStore = TrainingPlanStore.fromDatabase(database)
        motionStore = MotionStore.fromDatabase(database)
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
                        PlanMotionState(entryId = 1, motionId = 1, title = "Snatch", dayIndex = 1, sets = 5, repsPerSet = 2, intensity = 0.82, orderIndex = 1),
                        PlanMotionState(entryId = 2, motionId = 6, title = "Front Squat", dayIndex = 2, sets = 5, repsPerSet = 3, intensity = 0.78, orderIndex = 1),
                        PlanMotionState(entryId = 3, motionId = 3, title = "Snatch Pull", dayIndex = 3, sets = 4, repsPerSet = 3, intensity = 0.9, orderIndex = 1)
                    )
                ),
                TrainingPlanState(
                    id = 2,
                    name = "Competition Peak",
                    lastAppliedAt = 1715800000000,
                    currentIndex = 1,
                    motions = listOf(
                        PlanMotionState(entryId = 1, motionId = 2, title = "Clean & Jerk", dayIndex = 1, sets = 6, repsPerSet = 1, intensity = 0.92, orderIndex = 1),
                        PlanMotionState(entryId = 2, motionId = 1, title = "Snatch", dayIndex = 2, sets = 5, repsPerSet = 1, intensity = 0.88, orderIndex = 1),
                        PlanMotionState(entryId = 3, motionId = 5, title = "Push Press", dayIndex = 3, sets = 4, repsPerSet = 3, intensity = 0.8, orderIndex = 1)
                    )
                ),
                TrainingPlanState(
                    id = 3,
                    name = "Technique Cycle",
                    lastAppliedAt = 1715400000000,
                    currentIndex = 1,
                    motions = listOf(
                        PlanMotionState(entryId = 1, motionId = 1, title = "Snatch", dayIndex = 1, sets = 6, repsPerSet = 2, intensity = 0.74, orderIndex = 1),
                        PlanMotionState(entryId = 2, motionId = 2, title = "Clean & Jerk", dayIndex = 2, sets = 5, repsPerSet = 2, intensity = 0.76, orderIndex = 1),
                        PlanMotionState(entryId = 3, motionId = 4, title = "Clean Pull", dayIndex = 3, sets = 4, repsPerSet = 3, intensity = 0.84, orderIndex = 1)
                    )
                ),
                TrainingPlanState(
                    id = 4,
                    name = "Pull Volume Block",
                    lastAppliedAt = 1715200000000,
                    currentIndex = 1,
                    motions = listOf(
                        PlanMotionState(entryId = 1, motionId = 4, title = "Clean Pull", dayIndex = 1, sets = 5, repsPerSet = 3, intensity = 0.86, orderIndex = 1),
                        PlanMotionState(entryId = 2, motionId = 7, title = "Back Squat", dayIndex = 2, sets = 5, repsPerSet = 5, intensity = 0.8, orderIndex = 1),
                        PlanMotionState(entryId = 3, motionId = 5, title = "Push Press", dayIndex = 3, sets = 4, repsPerSet = 4, intensity = 0.72, orderIndex = 1)
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

    private fun controller(
        nowProvider: () -> Long = { 123L },
        logger: AppLogger = AndroidAppLogger,
        videoProcessor: VideoProcessor = FakeVideoProcessor()
    ): PlanController {
        return PlanController(
            trainingPlanStore = trainingPlanStore,
            shouldSeedDebugPlans = true,
            nowProvider = nowProvider,
            logger = logger,
            videoProcessor = videoProcessor
        )
    }

    private fun initialState(controller: PlanController): PlanState {
        return runBlocking {
            controller.loadState(seedCatalog)
        }
    }

    @Test
    fun loadState_readsDatabaseSeedAndDefaults() {
        val state = initialState(controller())

        assertEquals(7, state.availableMotions.size)
        assertEquals(4, state.trainingPlans.size)
        assertEquals(2, state.currentPlanId)
        assertEquals(PlanRoute.Overview, state.planRoute)
    }

    @Test
    fun createPlan_opensDraftEditorWithoutPersisting() {
        val controller = controller(nowProvider = { 900L })
        val initial = initialState(controller)

        val updatedState = controller.createPlan(initial)

        assertEquals(initial.trainingPlans.size, updatedState.trainingPlans.size)
        assertEquals(PlanRoute.Editor, updatedState.planRoute)
        assertNotNull(updatedState.planEditor)
        assertTrue(updatedState.planEditor?.isNewPlan == true)
        assertEquals("", updatedState.planEditor?.title)
        assertNull(updatedState.planEditor?.cyclePeriod)
    }

    @Test
    fun submitPlanEditor_persistsNewPlanAndKeepsEditorOpen() = runBlocking {
        val controller = controller(nowProvider = { 900L })
        val createdState = controller.createPlan(initialState(controller))
        val titledState = controller.updatePlanEditorTitle(createdState, " New Plan ")
        val configuredState = controller.updatePlanEditorCyclePeriod(titledState, 9)

        val updatedState = controller.submitPlanEditor(configuredState)
        val createdPlan = updatedState.trainingPlans.first { it.name == "New Plan" }

        assertEquals(900L, createdPlan.lastAppliedAt)
        assertEquals(5, updatedState.trainingPlans.size)
        assertEquals(PlanRoute.Editor, updatedState.planRoute)
        assertEquals(createdPlan.id, updatedState.planEditor?.planId)
        assertEquals("New Plan", updatedState.planEditor?.title)
        assertEquals(9, updatedState.planEditor?.cyclePeriod)
        assertFalse(updatedState.planEditor?.isNewPlan ?: true)
    }

    @Test
    fun handlePlanBack_closesNewDraftEditorAndReturnsToList() = runBlocking {
        val controller = controller(nowProvider = { 900L })
        val initial = initialState(controller)
        val createdState = controller.createPlan(initial)
        val renamedState = controller.updatePlanEditorTitle(createdState, "Temporary Draft")

        val updatedState = controller.handlePlanBack(renamedState)

        assertFalse(updatedState.trainingPlans.any { it.name == "Temporary Draft" })
        assertEquals(PlanRoute.List, updatedState.planRoute)
        assertNull(updatedState.planEditor)
        assertEquals(initial.trainingPlans.size, updatedState.trainingPlans.size)
    }

    @Test
    fun handlePlanBack_fromMotionReturnsToEditor() = runBlocking {
        val controller = controller()
        val detailState = controller.showPlanDetail(initialState(controller), 1)
        val state = controller.showMotionDetail(detailState, 2)

        val updatedState = controller.handlePlanBack(state)

        assertEquals(PlanRoute.Editor, updatedState.planRoute)
        assertEquals(1, updatedState.planEditor?.planId)
    }

    @Test
    fun handlePlanBack_fromPlanListReturnsToOverview() = runBlocking {
        val controller = controller()
        val state = controller.showPlanList(initialState(controller))

        val updatedState = controller.handlePlanBack(state)

        assertEquals(PlanRoute.Overview, updatedState.planRoute)
    }

    @Test
    fun selectPlan_updatesCurrentPlanAndTimestamp() = runBlocking {
        val controller = controller(nowProvider = { 777L })
        val updatedState = controller.selectPlan(initialState(controller), 1)

        assertEquals(1, updatedState.currentPlanId)
        assertEquals(777L, updatedState.trainingPlans.first { it.id == 1 }.lastAppliedAt)
    }

    @Test
    fun selectPlan_whenPlanIsMissing_logsWarningAndKeepsState() = runBlocking {
        val logger = RecordingAppLogger()
        val controller = controller(logger = logger)
        val state = initialState(controller)

        val updatedState = controller.selectPlan(state, 999)

        assertEquals(state, updatedState)
        assertTrue(
            logger.entries().any { entry ->
                entry.level == "warn" &&
                    entry.tag == "PlanController" &&
                    entry.message.contains("was not found") &&
                    entry.message.contains("planId=999")
            }
        )
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
    fun loadState_advancesSelectedPlanDayWhenDateChangesAndDoesNotDoubleAdvance() = runBlocking {
        val firstDay = 60L * millisPerDay
        val nextDay = firstDay + millisPerDay
        val firstDayController = controller(nowProvider = { firstDay })
        val firstDayState = firstDayController.updatePlanCurrentDay(
            state = initialState(firstDayController),
            planId = 2,
            dayIndex = 7
        )

        assertEquals(7, firstDayState.trainingPlans.first { it.id == 2 }.currentIndex)

        val nextDayController = controller(nowProvider = { nextDay })
        val nextDayState = initialState(nextDayController)
        val refreshedSameDayState = nextDayController.refreshState(nextDayState)

        assertEquals(1, nextDayState.trainingPlans.first { it.id == 2 }.currentIndex)
        assertEquals(1, refreshedSameDayState.trainingPlans.first { it.id == 2 }.currentIndex)
    }

    @Test
    fun workoutSessionActions_persistWorkoutLifecycleAndStopConfirmation() = runBlocking {
        var now = 1_000L
        val controller = controller(nowProvider = { now })
        val initial = initialState(controller)

        val startedState = controller.startWorkout(initial)

        assertTrue(startedState.workoutSession.isWorkoutGoing)
        assertFalse(startedState.workoutSession.isPaused)
        assertEquals(1_000L, startedState.workoutSession.startedAt)
        assertEquals(1_000L, startedState.workoutSession.lastResumedAt)

        now = 4_000L

        val pausedState = controller.toggleWorkoutPause(startedState)

        assertTrue(pausedState.workoutSession.isWorkoutGoing)
        assertTrue(pausedState.workoutSession.isPaused)
        assertEquals(3_000L, pausedState.workoutSession.elapsedBeforePauseMs)

        now = 8_000L

        val resumedState = controller.toggleWorkoutPause(pausedState)

        assertTrue(resumedState.workoutSession.isWorkoutGoing)
        assertFalse(resumedState.workoutSession.isPaused)
        assertEquals(1_000L, resumedState.workoutSession.startedAt)
        assertEquals(8_000L, resumedState.workoutSession.lastResumedAt)
        assertEquals(3_000L, resumedState.workoutSession.elapsedBeforePauseMs)

        val stopRequestedState = controller.requestWorkoutStop(resumedState)

        assertTrue(stopRequestedState.workoutStopPendingConfirmation)

        val stopDismissedState = controller.dismissWorkoutStop(stopRequestedState)

        assertFalse(stopDismissedState.workoutStopPendingConfirmation)

        val stopConfirmedState = controller.confirmWorkoutStop(
            controller.requestWorkoutStop(stopDismissedState)
        )

        assertFalse(stopConfirmedState.workoutSession.isWorkoutGoing)
        assertFalse(stopConfirmedState.workoutSession.isPaused)
        assertEquals(0L, stopConfirmedState.workoutSession.startedAt)
        assertEquals(0L, stopConfirmedState.workoutSession.lastResumedAt)
        assertEquals(0L, stopConfirmedState.workoutSession.elapsedBeforePauseMs)
        assertFalse(stopConfirmedState.workoutStopPendingConfirmation)
    }

    @Test
    fun closeCameraWithVideo_submitsProcessingAndStoresPendingVideo() {
        val fakeVideoProcessor = FakeVideoProcessor()
        val controller = controller(videoProcessor = fakeVideoProcessor)
        val state = controller.emptyState().copy(planRoute = PlanRoute.Camera(
            motionId = 1,
            motionTitle = "Snatch",
            setIndex = 1,
            setsInMotion = 3,
            expectedReps = 2,
            expectedWeight = 80.0,
            expectedIntensity = 0.8
        ))

        val updatedState = controller.closeCameraWithVideo(state, "lift-1.mp4")

        assertEquals(PlanRoute.Overview, updatedState.planRoute)
        assertEquals("lift-1.mp4", updatedState.cameraVideoName)
        assertEquals(listOf("lift-1.mp4"), fakeVideoProcessor.submittedVideoNames)
    }

    @Test
    fun refreshState_clearsCameraVideoName() = runBlocking {
        val controller = controller()
        val state = initialState(controller).copy(cameraVideoName = "lift-1.mp4")
        val refreshedState = controller.refreshState(state)
        assertNull(refreshedState.cameraVideoName)
    }

    @Test
    fun confirmPlanDeletion_removesPlanClearsDialogAndReturnsToList() = runBlocking {
        val controller = controller()
        val state = controller.requestPlanDeletion(initialState(controller), 2)
        val updatedState = controller.confirmPlanDeletion(state)

        assertFalse(updatedState.trainingPlans.any { it.id == 2 })
        assertEquals(1, updatedState.currentPlanId)
        assertEquals(PlanRoute.List, updatedState.planRoute)
        assertEquals(null, updatedState.planIdPendingDelete)
    }

    @Test
    fun addMotionToPlan_persistsMotionForSelectedDayAndOpensMotionDetail() = runBlocking {
        val controller = controller()
        val detailState = controller.showPlanDetail(initialState(controller), 1)
        val pickerState = controller.openAddMotionPicker(detailState)
        val motion = pickerState.availableMotions.first { it.title == "Snatch" }

        val updatedState = controller.addMotionToPlan(pickerState, motion)
        val updatedPlan = updatedState.trainingPlans.first { it.id == 1 }
        val destination = updatedState.planRoute as PlanRoute.Motion
        val addedMotion = updatedState.planEditor
            ?.motions
            ?.firstOrNull { planMotion -> planMotion.entryId == destination.motionEntryId }

        assertEquals(4, updatedPlan.motions.size)
        assertEquals(2, updatedPlan.motions.count { planMotion -> planMotion.title == "Snatch" })
        assertEquals(1, addedMotion?.sets)
        assertEquals(1, addedMotion?.repsPerSet)
        assertEquals(0.0, addedMotion?.intensity ?: Double.NaN, 0.0)
        assertEquals(0.0, addedMotion?.weight ?: Double.NaN, 0.0)
        assertEquals(1, addedMotion?.dayIndex)
        assertEquals(PlanRoute.Motion(motionEntryId = destination.motionEntryId), updatedState.planRoute)
        assertEquals(1, updatedState.planEditor?.selectedDayIndex)
    }

    @Test
    fun movePlanMotion_reordersDraftPlanMotionsForSelectedDay() = runBlocking {
        val controller = controller()
        val draftState = controller.createPlan(initialState(controller))
        val titledState = controller.updatePlanEditorTitle(draftState, "Draft Plan")
        val configuredState = controller.selectPlanEditorDay(
            controller.updatePlanEditorCyclePeriod(titledState, 7),
            1
        )
        val snatchState = controller.addMotionToPlan(
            configuredState,
            configuredState.availableMotions.first { motion -> motion.title == "Snatch" }
        )
        val twoMotionState = controller.addMotionToPlan(
            snatchState,
            snatchState.availableMotions.first { motion -> motion.title == "Clean & Jerk" }
        )
        val cleanAndJerkEntryId = twoMotionState.planEditor
            ?.motions
            ?.first { motion -> motion.title == "Clean & Jerk" }
            ?.entryId
            ?: -1

        val updatedState = controller.movePlanMotion(twoMotionState, cleanAndJerkEntryId, -1)

        assertEquals(
            listOf("Clean & Jerk", "Snatch"),
            updatedState.planEditor?.motions?.map { motion -> motion.title }
        )
        assertEquals(
            listOf(1, 2),
            updatedState.planEditor?.motions?.map { motion -> motion.orderIndex }
        )
    }

    @Test
    fun movePlanMotion_reordersAndPersistsExistingPlanMotions() = runBlocking {
        val controller = controller()
        val draftState = controller.createPlan(initialState(controller))
        val titledState = controller.updatePlanEditorTitle(draftState, "Persisted Plan")
        val configuredState = controller.selectPlanEditorDay(
            controller.updatePlanEditorCyclePeriod(titledState, 7),
            1
        )
        val snatchState = controller.addMotionToPlan(
            configuredState,
            configuredState.availableMotions.first { motion -> motion.title == "Snatch" }
        )
        val twoMotionState = controller.addMotionToPlan(
            snatchState,
            snatchState.availableMotions.first { motion -> motion.title == "Clean & Jerk" }
        )
        val persistedState = controller.submitPlanEditor(twoMotionState)
        val cleanAndJerkEntryId = persistedState.planEditor
            ?.motions
            ?.first { motion -> motion.title == "Clean & Jerk" }
            ?.entryId
            ?: -1

        val updatedState = controller.movePlanMotion(persistedState, cleanAndJerkEntryId, -1)
        val updatedPlan = updatedState.trainingPlans.first { plan -> plan.name == "Persisted Plan" }

        assertEquals(
            listOf("Clean & Jerk", "Snatch"),
            updatedState.planEditor?.motions?.map { motion -> motion.title }
        )
        assertEquals(
            listOf("Clean & Jerk", "Snatch"),
            updatedPlan.motions.map { motion -> motion.title }
        )
        assertEquals(listOf(1, 2), updatedPlan.motions.map { motion -> motion.orderIndex })
    }

    @Test
    fun updateMotionWeight_persistsWeightAndClampsNegativeValues() = runBlocking {
        val controller = controller()
        val detailState = controller.showPlanDetail(initialState(controller), 1)
        val initialMotionEntryId = detailState.planEditor?.motions?.first()?.entryId ?: -1

        val weightedState = controller.updateMotionWeight(
            state = detailState,
            motionEntryId = initialMotionEntryId,
            weight = 92.5
        )
        val persistedMotionEntryId = weightedState.planEditor?.motions?.first()?.entryId ?: -1
        val clampedState = controller.updateMotionWeight(
            state = weightedState,
            motionEntryId = persistedMotionEntryId,
            weight = -3.0
        )

        assertEquals(92.5, weightedState.trainingPlans.first { it.id == 1 }.motions.first().weight, 0.0)
        assertEquals(92.5, weightedState.planEditor?.motions?.first()?.weight ?: Double.NaN, 0.0)
        assertEquals(0.0, clampedState.trainingPlans.first { it.id == 1 }.motions.first().weight, 0.0)
        assertEquals(0.0, clampedState.planEditor?.motions?.first()?.weight ?: Double.NaN, 0.0)
    }

    @Test
    fun confirmMotionDeletion_removesMotionAndReturnsToEditor() = runBlocking {
        val controller = controller()
        val detailState = controller.showPlanDetail(initialState(controller), 1)
        val motionEntryIdToDelete = detailState.planEditor?.motions?.getOrNull(1)?.entryId ?: -1
        val state = controller.requestMotionDeletion(detailState, motionEntryIdToDelete)
        val updatedState = controller.confirmMotionDeletion(state)
        val updatedPlan = updatedState.trainingPlans.first { it.id == 1 }

        assertEquals(listOf("Snatch", "Snatch Pull"), updatedPlan.motions.map { it.title })
        assertEquals(PlanRoute.Editor, updatedState.planRoute)
        assertNull(updatedState.motionPendingDelete)
        assertEquals(listOf("Snatch", "Snatch Pull"), updatedState.planEditor?.motions?.map { it.title })
    }

    @Test
    fun refreshState_readsMotionLibraryChangesFromDatabase() = runBlocking {
        val controller = controller()
        val state = initialState(controller)
        val snatch = motionStore.getMotions().first { motion -> motion.name == "Snatch" }

        motionStore.updateMotion(snatch.copy(name = "Hang Snatch"))

        val refreshedState = controller.refreshState(state)

        assertTrue(refreshedState.availableMotions.any { motion -> motion.title == "Hang Snatch" })
        assertEquals(
            "Hang Snatch",
            refreshedState.trainingPlans
                .first { it.id == 1 }
                .motions
                .first { it.entryId == 1 }
                .title
        )
    }

    @Test
    fun startWorkout_createsHistoryRecordAndSetsActiveHistoryId() = runBlocking {
        var now = 1_000L
        val controller = controller(nowProvider = { now })
        val initial = initialState(controller)

        val startedState = controller.startWorkout(initial)

        assertTrue(startedState.workoutSession.isWorkoutGoing)
        assertNotNull(startedState.workoutProgress)
        assertNotNull(startedState.workoutProgress?.activeHistoryId)

        val historyId = startedState.workoutProgress!!.activeHistoryId!!
        val historyRecords = trainingPlanStore.getHistoryRecords()
        val createdHistory = historyRecords.first { it.id == historyId }

        assertEquals(2, createdHistory.planId)
        assertEquals(1_000L, createdHistory.startTime)
        assertEquals(1_000L, createdHistory.endTime)
        assertEquals(1, createdHistory.dayIndex)
    }

    @Test
    fun finishCurrentWorkoutSet_linksMetaHistoryToHistory() = runBlocking {
        var now = 1_000L
        val controller = controller(nowProvider = { now })
        val initial = initialState(controller)
        val startedState = controller.startWorkout(initial)

        now = 3_000L
        val setStartedState = controller.startNextWorkoutSet(startedState)

        val performance = WorkoutSetPerformanceInput(
            repsDone = 1,
            weightDone = 90.0,
            feeling = WorkoutSetFeeling.HardButControlled,
            breakDurationSeconds = 60
        )

        val finishedSetState = controller.finishCurrentWorkoutSet(setStartedState, performance)

        val historyId = startedState.workoutProgress!!.activeHistoryId!!
        val metaHistoryRecords = trainingPlanStore.getMetaHistoryRecordsByHistoryId(historyId)

        assertTrue(metaHistoryRecords.isNotEmpty())
        assertEquals(historyId, metaHistoryRecords.first().historyId)
    }

    @Test
    fun confirmWorkoutStop_updatesHistoryEndTime() = runBlocking {
        var now = 1_000L
        val controller = controller(nowProvider = { now })
        val initial = initialState(controller)
        val startedState = controller.startWorkout(initial)

        val historyId = startedState.workoutProgress!!.activeHistoryId!!

        now = 5_000L
        val stopRequestedState = controller.requestWorkoutStop(startedState)
        val stoppedState = controller.confirmWorkoutStop(stopRequestedState)

        assertFalse(stoppedState.workoutSession.isWorkoutGoing)
        assertNull(stoppedState.workoutProgress)

        val historyRecords = trainingPlanStore.getHistoryRecords()
        val updatedHistory = historyRecords.first { it.id == historyId }

        assertEquals(5_000L, updatedHistory.endTime)
    }

    @Test
    fun skipWorkoutSet_whenFinishing_updatesHistoryEndTime() = runBlocking {
        val controller = controller(nowProvider = { 1_000L })
        val initial = initialState(controller)
        val startedState = controller.startWorkout(initial)

        val historyId = startedState.workoutProgress!!.activeHistoryId!!
        val totalSets = startedState.workoutProgress!!.totalSetCount

        var currentState = startedState
        for (i in 1..totalSets) {
            currentState = controller.skipWorkoutSet(currentState)
        }

        assertTrue(currentState.workoutProgress?.isFinished == true || currentState.workoutSession.isWorkoutGoing.not())

        val historyRecords = trainingPlanStore.getHistoryRecords()
        val updatedHistory = historyRecords.first { it.id == historyId }

        assertTrue(updatedHistory.endTime > 0L)
    }
}

private class FakeVideoProcessor : VideoProcessor {
    val submittedVideoNames = mutableListOf<String>()

    override fun submitForProcessing(videoName: String) {
        submittedVideoNames += videoName
    }

    override fun submitForProcessing(videoName: String, options: DrawingOptions) {
        submitForProcessing(videoName)
    }

    override fun resetProcessingState(videoName: String) = Unit

    override fun hasProcessedCopy(videoName: String): Boolean = false

    override fun isProcessing(videoName: String): Boolean = false

    override fun getProgress(videoName: String): Int = 0

    override fun getStatus(videoName: String): VideoProcessingStatus {
        return VideoProcessingStatus(
            videoName = videoName,
            state = VideoProcessState.NOT_STARTED,
            progress = 0,
            processedVideoName = null
        )
    }

    override fun getOriginalVideoFile(videoName: String) = null

    override fun getProcessedVideoFile(videoName: String) = null

    override fun getPlaybackVideoFile(videoName: String) = null

    override fun clearAnalysisData(metahistoryId: Int) = Unit

    override fun clearPoseFrames(metahistoryId: Int) = Unit

    override fun clearBarbellFrames(metahistoryId: Int) = Unit

    override fun clearTimeseries(metahistoryId: Int) = Unit

    override fun deleteVideoFiles(videoName: String) = Unit

    override suspend fun trackBarbell(
        videoName: String,
        metahistoryId: Int,
        initialX: Float,
        initialY: Float,
        initialRadius: Float,
        onProgress: (Int) -> Unit
    ): List<BarbellFrameEntity> = emptyList()

    override suspend fun trackBarbellHybrid(
        videoName: String,
        metahistoryId: Int,
        initialX: Float,
        initialY: Float,
        initialRadius: Float,
        initialX2: Float?,
        initialY2: Float?,
        onProgress: (Int) -> Unit
    ): List<BarbellFrameEntity> = emptyList()
}
