package com.potato.liftinsight.record.controller

import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.record.model.DisplayMode
import com.potato.liftinsight.training.data.HistoryEntity
import com.potato.liftinsight.training.data.HistoryRecord
import com.potato.liftinsight.training.data.LiftInsightDatabase
import com.potato.liftinsight.video.VideoProcessor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
class TrainingHistoryControllerTest {
    private lateinit var context: Context
    private lateinit var database: LiftInsightDatabase
    private lateinit var trainingPlanStore: TrainingPlanStore
    private lateinit var videoProcessor: VideoProcessor
    private lateinit var controller: TrainingHistoryController

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = LiftInsightDatabase.from(context)
        trainingPlanStore = TrainingPlanStore.from(context)
        videoProcessor = VideoProcessor.from(context)

        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }

        controller = TrainingHistoryController(
            trainingPlanStore = trainingPlanStore,
            videoProcessor = videoProcessor
        )
    }

    @Test
    fun exportVideo_returnsNullWhenVideoNameIsBlank() = runBlocking {
        val result = controller.exportVideo(context, "  ")

        assertNull(result)
    }

    @Test
    fun exportVideo_returnsNullWhenVideoDoesNotExist() = runBlocking {
        val result = controller.exportVideo(context, "no-such-video.mp4")

        assertNull(result)
    }

    @Test
    fun exportVideo_returnsNonNullForValidVideo() = runBlocking {
        val videoFile = createVideoFile("export-controller-test.mp4")

        val result = controller.exportVideo(context, videoFile.name)

        assertNotNull(result)
    }

    @Test
    fun exportOriginalVideo_returnsNullWhenVideoNameIsBlank() = runBlocking {
        val result = controller.exportOriginalVideo(context, "  ")

        assertNull(result)
    }

    @Test
    fun exportOriginalVideo_returnsNullWhenVideoDoesNotExist() = runBlocking {
        val result = controller.exportOriginalVideo(context, "no-such-original.mp4")

        assertNull(result)
    }

    @Test
    fun exportOriginalVideo_returnsNonNullForExistingOriginalVideo() = runBlocking {
        val videoFile = createVideoFile("export-original-test.mp4")

        val result = controller.exportOriginalVideo(context, videoFile.name)

        assertNotNull(result)
    }

    @Test
    fun exportProcessedVideo_returnsNullWhenVideoNameIsBlank() = runBlocking {
        val result = controller.exportProcessedVideo(context, "  ")

        assertNull(result)
    }

    @Test
    fun exportProcessedVideo_returnsNullWhenProcessedFileDoesNotExist() = runBlocking {
        val result = controller.exportProcessedVideo(context, "no-such-processed.mp4")

        assertNull(result)
    }

    @Test
    fun exportProcessedVideo_returnsNonNullForExistingProcessedVideo() = runBlocking {
        val originalFile = createVideoFile("export-processed-test.mp4")
        val processedFile = createVideoFile("export-processed-test_processed.mp4")

        withContext(Dispatchers.IO) {
            database.planDao().upsertVideoProcessState(
                com.potato.liftinsight.training.data.VideoProcessStateEntity(
                    videoName = originalFile.name,
                    state = com.potato.liftinsight.training.data.VideoProcessState.DONE.name,
                    progress = 100,
                    processedVideoName = processedFile.name
                )
            )
        }

        val result = controller.exportProcessedVideo(
            context,
            "export-processed-test.mp4"
        )

        assertNotNull(result)
    }

    private fun createVideoFile(fileName: String): File {
        val videoDirectory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir
        val file = File(videoDirectory, fileName)

        file.parentFile?.mkdirs()
        file.writeText("placeholder-video-content")

        return file
    }

    private fun insertTestMotion(motionId: Long = 1L, motionName: String = "Test Motion") {
        runBlocking {
            withContext(Dispatchers.IO) {
                database.motionDao().insertMotionEntity(
                    com.potato.liftinsight.training.data.MotionEntity(
                        id = motionId.toInt(),
                        name = motionName
                    )
                )
            }
        }
    }

    private fun createTestPlanAndHistory(): Int {
        return runBlocking {
            withContext(Dispatchers.IO) {
                val planId = database.planDao().createPlan(
                    com.potato.liftinsight.training.data.PlanEntity(
                        name = "Test Plan",
                        cyclePeriod = 7,
                        currentIndex = 1
                    ),
                    emptyList()
                )
                database.historyDao().insertHistory(
                    HistoryEntity(
                        planId = planId,
                        startTime = 1000L,
                        endTime = 2000L,
                        intensity = 7,
                        dayIndex = 1
                    )
                ).toInt()
            }
        }
    }

    private fun insertTestHistoryRecord(
        motionId: Int = 1,
        date: String = "2024-01-01 10:00:00",
        rep: Int = 10,
        rpe: Int = 8,
        weight: Double = 100.0
    ): Long {
        return runBlocking {
            withContext(Dispatchers.IO) {
                database.planDao().insertMetaHistory(
                    com.potato.liftinsight.training.data.MetaHistoryEntity(
                        date = date,
                        rep = rep,
                        rpe = rpe,
                        weight = weight,
                        motionId = motionId
                    )
                )
            }
        }
    }

    // --- softDeleteRecord ---

    @Test
    fun softDeleteRecord_removesRecordFromState() = runBlocking {
        insertTestMotion()
        val recordId = insertTestHistoryRecord().toInt()
        val state = controller.loadState()
        val record = state.records.first { it.id == recordId }

        val updatedState = controller.softDeleteRecord(state, record)

        assertNull(updatedState.records.find { it.id == recordId })
    }

    @Test
    fun softDeleteRecord_clearsSelectedRecordIfDeleted() = runBlocking {
        insertTestMotion()
        val recordId = insertTestHistoryRecord().toInt()
        val state = controller.loadState()
        val record = state.records.first { it.id == recordId }
        val stateWithSelected = state.copy(selectedRecord = record)

        val updatedState = controller.softDeleteRecord(stateWithSelected, record)

        assertNull(updatedState.selectedRecord)
    }

    @Test
    fun softDeleteRecord_movesRecordToBin() = runBlocking {
        insertTestMotion()
        val recordId = insertTestHistoryRecord().toInt()
        val state = controller.loadState()
        val record = state.records.first { it.id == recordId }

        controller.softDeleteRecord(state, record)
        val binState = controller.loadBinState(state)

        assertNotNull(binState.binRecords.find {
            it.date == record.date &&
                it.rep == record.rep &&
                it.rpe == record.rpe &&
                it.weight == record.weight &&
                it.motionId == record.motionId
        })
    }

    // --- deleteSelectedRecords ---

    @Test
    fun deleteSelectedRecords_removesSelectedRecordsFromState() = runBlocking {
        insertTestMotion()
        val recordId1 = insertTestHistoryRecord(date = "2024-01-01 10:00:00").toInt()
        val recordId2 = insertTestHistoryRecord(date = "2024-01-02 10:00:00").toInt()
        val state = controller.loadState().copy(selectedRecordIds = setOf(recordId1))

        val updatedState = controller.deleteSelectedRecords(state)

        assertNull(updatedState.records.find { it.id == recordId1 })
        assertNotNull(updatedState.records.find { it.id == recordId2 })
    }

    @Test
    fun deleteSelectedRecords_clearsSelectionAfterDelete() = runBlocking {
        insertTestMotion()
        val recordId1 = insertTestHistoryRecord(date = "2024-01-01 10:00:00").toInt()
        val state = controller.loadState().copy(selectedRecordIds = setOf(recordId1))

        val updatedState = controller.deleteSelectedRecords(state)

        assertTrue(updatedState.selectedRecordIds.isEmpty())
    }

    @Test
    fun deleteSelectedRecords_movesSelectedRecordsToBin() = runBlocking {
        insertTestMotion()
        insertTestHistoryRecord(date = "2024-01-01 10:00:00")
        insertTestHistoryRecord(date = "2024-01-02 10:00:00")
        val state = controller.loadState()
        val recordId1 = state.records.last().id
        val stateWithSelection = state.copy(selectedRecordIds = setOf(recordId1))

        controller.deleteSelectedRecords(stateWithSelection)
        val binState = controller.loadBinState(state)

        assertEquals(1, binState.binRecords.size)
    }

    // --- deleteDayRecords ---

    @Test
    fun deleteDayRecords_removesDayRecordsFromState() = runBlocking {
        insertTestMotion()
        val recordId1 = insertTestHistoryRecord(date = "2024-01-01 10:00:00").toInt()
        val recordId2 = insertTestHistoryRecord(date = "2024-01-02 10:00:00").toInt()
        val state = controller.loadState()

        val updatedState = controller.deleteDayRecords(state, "2024-01-01")

        assertNull(updatedState.records.find { it.id == recordId1 })
        assertNotNull(updatedState.records.find { it.id == recordId2 })
    }

    @Test
    fun deleteDayRecords_movesDayRecordsToBin() = runBlocking {
        insertTestMotion()
        insertTestHistoryRecord(date = "2024-01-01 10:00:00")
        insertTestHistoryRecord(date = "2024-01-02 10:00:00")
        val state = controller.loadState()

        controller.deleteDayRecords(state, "2024-01-01")
        val binState = controller.loadBinState(state)

        assertEquals(1, binState.binRecords.size)
    }

    // --- toggleBatchMode ---

    @Test
    fun toggleBatchMode_togglesBatchMode() {
        val state = controller.emptyState()

        val updatedState1 = controller.toggleBatchMode(state)
        assertTrue(updatedState1.isBatchMode)

        val updatedState2 = controller.toggleBatchMode(updatedState1)
        assertFalse(updatedState2.isBatchMode)
    }

    @Test
    fun toggleBatchMode_clearsSelectedRecordIdsWhenExiting() {
        val state = controller.emptyState().copy(
            isBatchMode = true,
            selectedRecordIds = setOf(1, 2, 3)
        )

        val updatedState = controller.toggleBatchMode(state)

        assertFalse(updatedState.isBatchMode)
        assertTrue(updatedState.selectedRecordIds.isEmpty())
    }

    // --- toggleRecordSelection ---

    @Test
    fun toggleRecordSelection_addsRecordIdToSelection() {
        val state = controller.emptyState()

        val updatedState = controller.toggleRecordSelection(state, 1)

        assertTrue(1 in updatedState.selectedRecordIds)
    }

    @Test
    fun toggleRecordSelection_removesRecordIdFromSelection() {
        val state = controller.emptyState().copy(selectedRecordIds = setOf(1))

        val updatedState = controller.toggleRecordSelection(state, 1)

        assertFalse(1 in updatedState.selectedRecordIds)
    }

    // --- toggleDateGroup ---

    @Test
    fun toggleDateGroup_addsDateKeyToExpandedGroups() {
        val state = controller.emptyState()

        val updatedState = controller.toggleDateGroup(state, "2024-01-01")

        assertTrue("2024-01-01" in updatedState.expandedDateGroups)
    }

    @Test
    fun toggleDateGroup_removesDateKeyFromExpandedGroups() {
        val state = controller.emptyState().copy(expandedDateGroups = setOf("2024-01-01"))

        val updatedState = controller.toggleDateGroup(state, "2024-01-01")

        assertFalse("2024-01-01" in updatedState.expandedDateGroups)
    }

    // --- initializeExpandedGroups ---

    @Test
    fun initializeExpandedGroups_setsTodayAsExpanded() {
        val state = controller.emptyState()
        val todayKey = java.time.LocalDate.now().toString()

        val updatedState = controller.initializeExpandedGroups(state)

        assertTrue(todayKey in updatedState.expandedDateGroups)
        assertEquals(1, updatedState.expandedDateGroups.size)
    }

    // --- loadBinState ---

    @Test
    fun loadBinState_loadsBinRecords() = runBlocking {
        insertTestMotion()
        insertTestHistoryRecord()

        val state = controller.loadState()
        val record = state.records.first()
        controller.softDeleteRecord(state, record)

        val binState = controller.loadBinState(state)

        assertTrue(binState.isBinMode)
        assertEquals(1, binState.binRecords.size)
    }

    @Test
    fun loadBinState_returnsEmptyWhenNoBinRecords() = runBlocking {
        val binState = controller.loadBinState(controller.emptyState())

        assertTrue(binState.isBinMode)
        assertTrue(binState.binRecords.isEmpty())
    }

    // --- selectBinRecord ---

    @Test
    fun selectBinRecord_setsSelectedBinRecord() {
        val record = com.potato.liftinsight.training.data.MetaHistoryRecord(
            id = 1,
            date = "2024-01-01 10:00:00",
            rep = 10,
            rpe = 8,
            weight = 100.0,
            motionId = 1,
            motionName = "Test Motion"
        )
        val state = controller.emptyState()

        val updatedState = controller.selectBinRecord(state, record)

        assertEquals(record, updatedState.selectedBinRecord)
    }

    // --- dismissBinRecord ---

    @Test
    fun dismissBinRecord_clearsSelectedBinRecord() {
        val record = com.potato.liftinsight.training.data.MetaHistoryRecord(
            id = 1,
            date = "2024-01-01 10:00:00",
            rep = 10,
            rpe = 8,
            weight = 100.0,
            motionId = 1,
            motionName = "Test Motion"
        )
        val state = controller.emptyState().copy(selectedBinRecord = record)

        val updatedState = controller.dismissBinRecord(state)

        assertNull(updatedState.selectedBinRecord)
    }

    // --- permanentlyDeleteBinRecord ---

    @Test
    fun permanentlyDeleteBinRecord_removesRecordFromBin() = runBlocking {
        insertTestMotion()
        insertTestHistoryRecord()

        val state = controller.loadState()
        val record = state.records.first()
        controller.softDeleteRecord(state, record)

        val binState = controller.loadBinState(state)
        val binRecord = binState.binRecords.first()

        val updatedBinState = controller.permanentlyDeleteBinRecord(binState, binRecord.id)

        assertTrue(updatedBinState.binRecords.isEmpty())
    }

    @Test
    fun permanentlyDeleteBinRecord_clearsSelectedBinRecordIfDeleted() = runBlocking {
        insertTestMotion()
        insertTestHistoryRecord()

        val state = controller.loadState()
        val record = state.records.first()
        controller.softDeleteRecord(state, record)

        val binState = controller.loadBinState(state)
        val binRecord = binState.binRecords.first()
        val stateWithSelected = binState.copy(selectedBinRecord = binRecord)

        val updatedBinState = controller.permanentlyDeleteBinRecord(stateWithSelected, binRecord.id)

        assertNull(updatedBinState.selectedBinRecord)
    }

    // --- revertBinRecord ---

    @Test
    fun revertBinRecord_removesRecordFromBin() = runBlocking {
        insertTestMotion()
        insertTestHistoryRecord()

        val state = controller.loadState()
        val record = state.records.first()
        controller.softDeleteRecord(state, record)

        val binState = controller.loadBinState(state)
        val binRecord = binState.binRecords.first()

        val updatedBinState = controller.revertBinRecord(binState, binRecord.id)

        assertTrue(updatedBinState.binRecords.isEmpty())
    }

    @Test
    fun revertBinRecord_restoresRecordToHistory() = runBlocking {
        insertTestMotion()
        insertTestHistoryRecord()

        val state = controller.loadState()
        val record = state.records.first()
        controller.softDeleteRecord(state, record)

        val binState = controller.loadBinState(state)
        val binRecord = binState.binRecords.first()

        controller.revertBinRecord(binState, binRecord.id)

        val restoredState = controller.loadState()
        assertEquals(1, restoredState.records.size)
    }

    @Test
    fun revertBinRecord_clearsSelectedBinRecordIfReverted() = runBlocking {
        insertTestMotion()
        insertTestHistoryRecord()

        val state = controller.loadState()
        val record = state.records.first()
        controller.softDeleteRecord(state, record)

        val binState = controller.loadBinState(state)
        val binRecord = binState.binRecords.first()
        val stateWithSelected = binState.copy(selectedBinRecord = binRecord)

        val updatedBinState = controller.revertBinRecord(stateWithSelected, binRecord.id)

        assertNull(updatedBinState.selectedBinRecord)
    }

    // --- permanentlyDeleteSelectedBinRecords ---

    @Test
    fun permanentlyDeleteSelectedBinRecords_removesSelectedRecordsFromBin() = runBlocking {
        insertTestMotion()
        insertTestHistoryRecord(date = "2024-01-01 10:00:00")
        insertTestHistoryRecord(date = "2024-01-02 10:00:00")

        val state = controller.loadState()
        state.records.forEach { record ->
            controller.softDeleteRecord(state, record)
        }

        val binState = controller.loadBinState(state)
        val binRecord = binState.binRecords.first()
        val updatedBinState = binState.copy(selectedRecordIds = setOf(binRecord.id))

        val resultState = controller.permanentlyDeleteSelectedBinRecords(updatedBinState)

        assertEquals(1, resultState.binRecords.size)
    }

    @Test
    fun permanentlyDeleteSelectedBinRecords_clearsSelectionAfterDelete() = runBlocking {
        insertTestMotion()
        insertTestHistoryRecord()

        val state = controller.loadState()
        val record = state.records.first()
        controller.softDeleteRecord(state, record)

        val binState = controller.loadBinState(state)
        val binRecord = binState.binRecords.first()
        val updatedBinState = binState.copy(selectedRecordIds = setOf(binRecord.id))

        val resultState = controller.permanentlyDeleteSelectedBinRecords(updatedBinState)

        assertTrue(resultState.selectedRecordIds.isEmpty())
    }

    // --- revertSelectedBinRecords ---

    @Test
    fun revertSelectedBinRecords_removesSelectedRecordsFromBin() = runBlocking {
        insertTestMotion()
        insertTestHistoryRecord(date = "2024-01-01 10:00:00")
        insertTestHistoryRecord(date = "2024-01-02 10:00:00")

        val state = controller.loadState()
        state.records.forEach { record ->
            controller.softDeleteRecord(state, record)
        }

        val binState = controller.loadBinState(state)
        val binRecord = binState.binRecords.first()
        val updatedBinState = binState.copy(selectedRecordIds = setOf(binRecord.id))

        val resultState = controller.revertSelectedBinRecords(updatedBinState)

        assertEquals(1, resultState.binRecords.size)
    }

    @Test
    fun revertSelectedBinRecords_restoresSelectedRecordsToHistory() = runBlocking {
        insertTestMotion()
        insertTestHistoryRecord(date = "2024-01-01 10:00:00")
        insertTestHistoryRecord(date = "2024-01-02 10:00:00")

        val state = controller.loadState()
        state.records.forEach { record ->
            controller.softDeleteRecord(state, record)
        }

        val binState = controller.loadBinState(state)
        val binRecord = binState.binRecords.first()
        val updatedBinState = binState.copy(selectedRecordIds = setOf(binRecord.id))

        controller.revertSelectedBinRecords(updatedBinState)

        val restoredState = controller.loadState()
        assertEquals(1, restoredState.records.size)
    }

    @Test
    fun revertSelectedBinRecords_clearsSelectionAfterRevert() = runBlocking {
        insertTestMotion()
        insertTestHistoryRecord()

        val state = controller.loadState()
        val record = state.records.first()
        controller.softDeleteRecord(state, record)

        val binState = controller.loadBinState(state)
        val binRecord = binState.binRecords.first()
        val updatedBinState = binState.copy(selectedRecordIds = setOf(binRecord.id))

        val resultState = controller.revertSelectedBinRecords(updatedBinState)

        assertTrue(resultState.selectedRecordIds.isEmpty())
    }

    // --- exitBinMode ---

    @Test
    fun exitBinMode_clearsBinState() {
        val state = controller.emptyState().copy(
            isBinMode = true,
            binRecords = listOf(
                com.potato.liftinsight.training.data.MetaHistoryRecord(
                    id = 1,
                    date = "2024-01-01 10:00:00",
                    rep = 10,
                    rpe = 8,
                    weight = 100.0,
                    motionId = 1,
                    motionName = "Test Motion"
                )
            ),
            selectedBinRecord = com.potato.liftinsight.training.data.MetaHistoryRecord(
                id = 1,
                date = "2024-01-01 10:00:00",
                rep = 10,
                rpe = 8,
                weight = 100.0,
                motionId = 1,
                motionName = "Test Motion"
            ),
            isBatchMode = true,
            selectedRecordIds = setOf(1)
        )

        val updatedState = controller.exitBinMode(state)

        assertFalse(updatedState.isBinMode)
        assertTrue(updatedState.binRecords.isEmpty())
        assertNull(updatedState.selectedBinRecord)
        assertFalse(updatedState.isBatchMode)
        assertTrue(updatedState.selectedRecordIds.isEmpty())
    }

    // --- switchDisplayMode ---

    @Test
    fun switchDisplayMode_changesDisplayMode() {
        val state = controller.emptyState()

        val updatedState = controller.switchDisplayMode(state, DisplayMode.HISTORY)

        assertEquals(DisplayMode.HISTORY, updatedState.displayMode)
    }

    @Test
    fun switchDisplayMode_clearsSelectedHistorySession() {
        val session = HistoryRecord(
            id = 1,
            planId = 1,
            planName = "Test Plan",
            startTime = 1000L,
            endTime = 2000L,
            intensity = 5,
            dayIndex = 1
        )
        val state = controller.emptyState().copy(
            displayMode = DisplayMode.HISTORY,
            selectedHistorySession = session,
            sessionMetaHistoryRecords = listOf(
                com.potato.liftinsight.training.data.MetaHistoryRecord(
                    id = 1,
                    date = "2024-01-01",
                    rep = 5,
                    rpe = 8,
                    weight = 80.0,
                    motionId = 1,
                    motionName = "Snatch"
                )
            )
        )

        val updatedState = controller.switchDisplayMode(state, DisplayMode.META_HISTORY)

        assertEquals(DisplayMode.META_HISTORY, updatedState.displayMode)
        assertNull(updatedState.selectedHistorySession)
        assertTrue(updatedState.sessionMetaHistoryRecords.isEmpty())
    }

    @Test
    fun switchDisplayMode_switchesBackToMetaHistory() {
        val state = controller.emptyState().copy(displayMode = DisplayMode.HISTORY)

        val updatedState = controller.switchDisplayMode(state, DisplayMode.META_HISTORY)

        assertEquals(DisplayMode.META_HISTORY, updatedState.displayMode)
    }

    // --- loadHistorySessions ---

    @Test
    fun loadHistorySessions_loadsSessionsFromStore() = runBlocking {
        insertTestMotion()
        insertTestHistoryRecord()
        createTestPlanAndHistory()

        val updatedState = controller.loadHistorySessions(controller.emptyState())

        assertTrue(updatedState.historyRecords.isNotEmpty())
    }

    @Test
    fun loadHistorySessions_returnsEmptyWhenNoSessions() = runBlocking {
        val updatedState = controller.loadHistorySessions(controller.emptyState())

        assertTrue(updatedState.historyRecords.isEmpty())
    }

    // --- selectHistorySession ---

    @Test
    fun selectHistorySession_setsSelectedSession() = runBlocking {
        insertTestMotion()
        insertTestHistoryRecord()
        val historyId = createTestPlanAndHistory()
        val stateWithHistory = controller.loadHistorySessions(controller.emptyState())
        val session = stateWithHistory.historyRecords.first { it.id == historyId }

        val updatedState = controller.selectHistorySession(stateWithHistory, session)

        assertNotNull(updatedState.selectedHistorySession)
        assertEquals(session.id, updatedState.selectedHistorySession!!.id)
    }

    @Test
    fun selectHistorySession_loadsSessionMetaHistoryRecords() = runBlocking {
        insertTestMotion()
        val recordId = insertTestHistoryRecord().toInt()
        val historyId = createTestPlanAndHistory()
        withContext(Dispatchers.IO) {
            database.historyDao().attachMetaHistories(historyId, listOf(recordId))
        }
        val stateWithHistory = controller.loadHistorySessions(controller.emptyState())
        val session = stateWithHistory.historyRecords.first { it.id == historyId }

        val updatedState = controller.selectHistorySession(stateWithHistory, session)

        assertTrue(updatedState.sessionMetaHistoryRecords.isNotEmpty())
    }

    // --- dismissHistorySession ---

    @Test
    fun dismissHistorySession_clearsSelectedSession() {
        val session = HistoryRecord(
            id = 1,
            planId = 1,
            planName = "Test Plan",
            startTime = 1000L,
            endTime = 2000L,
            intensity = 5,
            dayIndex = 1
        )
        val state = controller.emptyState().copy(
            selectedHistorySession = session,
            sessionMetaHistoryRecords = listOf(
                com.potato.liftinsight.training.data.MetaHistoryRecord(
                    id = 1,
                    date = "2024-01-01",
                    rep = 5,
                    rpe = 8,
                    weight = 80.0,
                    motionId = 1,
                    motionName = "Snatch"
                )
            )
        )

        val updatedState = controller.dismissHistorySession(state)

        assertNull(updatedState.selectedHistorySession)
        assertTrue(updatedState.sessionMetaHistoryRecords.isEmpty())
    }

    @Test
    fun dismissHistorySession_preservesOtherState() {
        val records = listOf(
            com.potato.liftinsight.training.data.MetaHistoryRecord(
                id = 1,
                date = "2024-01-01",
                rep = 5,
                rpe = 8,
                weight = 80.0,
                motionId = 1,
                motionName = "Snatch"
            )
        )
        val state = controller.emptyState().copy(
            records = records,
            selectedHistorySession = HistoryRecord(
                id = 1,
                planId = 1,
                planName = "Test Plan",
                startTime = 1000L,
                endTime = 2000L,
                intensity = 5,
                dayIndex = 1
            ),
            sessionMetaHistoryRecords = records
        )

        val updatedState = controller.dismissHistorySession(state)

        assertEquals(records, updatedState.records)
    }
}
