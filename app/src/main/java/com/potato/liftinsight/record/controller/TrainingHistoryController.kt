package com.potato.liftinsight.record.controller

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.record.model.TrainingHistoryState
import com.potato.liftinsight.training.data.MetaHistoryRecord
import com.potato.liftinsight.training.data.UpdateImportedVideoMetadataRequest
import com.potato.liftinsight.video.VideoExportHelper
import com.potato.liftinsight.video.VideoProcessor
import com.potato.liftinsight.video.imported.ImportedVideoAnalysisMode
import com.potato.liftinsight.video.imported.ImportedVideoSource
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TrainingHistoryController(
    private val trainingPlanStore: TrainingPlanStore,
    private val videoProcessor: VideoProcessor
) {
    fun emptyState(): TrainingHistoryState {
        return TrainingHistoryState()
    }

    suspend fun loadState(): TrainingHistoryState {
        val records = withContext(Dispatchers.IO) {
            trainingPlanStore.getMetaHistoryRecords()
        }

        return TrainingHistoryState(records = records)
    }

    fun selectRecord(state: TrainingHistoryState, record: MetaHistoryRecord): TrainingHistoryState {
        val selectedRecord = state.records.firstOrNull { historyRecord ->
            historyRecord.id == record.id
        } ?: record

        return state.copy(selectedRecord = selectedRecord)
    }

    fun dismissSelectedRecord(state: TrainingHistoryState): TrainingHistoryState {
        return state.copy(selectedRecord = null)
    }

    fun clearVideoStatus(state: TrainingHistoryState): TrainingHistoryState {
        return state.copy(selectedVideoStatus = null)
    }

    fun requestVideoStatusRefresh(state: TrainingHistoryState): TrainingHistoryState {
        return state.copy(
            selectedVideoStatus = null,
            videoStatusRefreshKey = state.videoStatusRefreshKey + 1
        )
    }

    suspend fun refreshSelectedVideoStatus(state: TrainingHistoryState): TrainingHistoryState {
        val videoName = state.selectedRecord?.videoName

        if (videoName.isNullOrBlank()) {
            return clearVideoStatus(state)
        }

        val status = withContext(Dispatchers.IO) {
            videoProcessor.getStatus(videoName)
        }

        return state.copy(selectedVideoStatus = status)
    }

    suspend fun updateRecordDetails(
        state: TrainingHistoryState,
        historyId: Int,
        weight: Double,
        rep: Int,
        rpe: Int
    ): TrainingHistoryState {
        val didUpdate = withContext(Dispatchers.IO) {
            trainingPlanStore.updateMetaHistoryDetails(
                historyId = historyId,
                weight = weight,
                rep = rep,
                rpe = rpe
            )
        }

        if (!didUpdate) {
            return state
        }

        val updatedRecord = state.records.firstOrNull { historyRecord ->
            historyRecord.id == historyId
        }?.copy(weight = weight, rep = rep, rpe = rpe) ?: return state

        return state.withUpdatedRecord(updatedRecord)
    }

    suspend fun attachLocalVideo(
        state: TrainingHistoryState,
        context: Context,
        uri: Uri,
        targetRecord: MetaHistoryRecord
    ): TrainingHistoryState {
        val importedVideoName = withContext(Dispatchers.IO) {
            importVideoIntoAppStorage(
                context = context,
                uri = uri,
                motionId = targetRecord.motionId,
                setIndex = 1
            )
        } ?: return state

        var updatedState = attachVideoToRecord(
            state = state,
            historyId = targetRecord.id,
            videoName = importedVideoName
        )
        updatedState = updateImportedVideoMetadata(
            state = updatedState,
            request = UpdateImportedVideoMetadataRequest(
                historyId = targetRecord.id,
                videoSource = ImportedVideoSource.LOCAL_FILE,
                analysisMode = ImportedVideoAnalysisMode.ESTIMATED
            )
        )

        return requestVideoStatusRefresh(updatedState)
    }

    suspend fun attachCapturedVideo(
        state: TrainingHistoryState,
        targetRecord: MetaHistoryRecord,
        videoName: String
    ): TrainingHistoryState {
        if (videoName.isBlank()) {
            return state
        }

        var updatedState = attachVideoToRecord(
            state = state,
            historyId = targetRecord.id,
            videoName = videoName
        )
        updatedState = updateImportedVideoMetadata(
            state = updatedState,
            request = UpdateImportedVideoMetadataRequest(
                historyId = targetRecord.id,
                videoSource = ImportedVideoSource.CAMERA_CAPTURE,
                analysisMode = ImportedVideoAnalysisMode.ESTIMATED
            )
        )

        return requestVideoStatusRefresh(updatedState)
    }

    suspend fun updateReferenceCalibration(
        state: TrainingHistoryState,
        record: MetaHistoryRecord,
        referenceLabel: String,
        pixelDistance: Double,
        distanceMeters: Double
    ): TrainingHistoryState {
        return updateImportedVideoMetadata(
            state = state,
            request = UpdateImportedVideoMetadataRequest(
                historyId = record.id,
                videoSource = record.videoSource,
                analysisMode = ImportedVideoAnalysisMode.REFERENCE_CALIBRATED,
                referenceLabel = referenceLabel,
                referencePixelDistance = pixelDistance,
                referenceDistanceMeters = distanceMeters
            )
        )
    }

    suspend fun resolvePlaybackUri(videoName: String): Uri? {
        val playbackFile = withContext(Dispatchers.IO) {
            videoProcessor.getPlaybackVideoFile(videoName)
        }

        return playbackFile?.let(Uri::fromFile)
    }

    suspend fun exportVideo(context: Context, videoName: String): Uri? {
        if (videoName.isBlank()) {
            return null
        }

        val sourceFile = withContext(Dispatchers.IO) {
            videoProcessor.getPlaybackVideoFile(videoName)
        } ?: return null

        return VideoExportHelper.exportToGallery(
            context = context,
            sourceFile = sourceFile
        )
    }

    suspend fun exportOriginalVideo(context: Context, videoName: String): Uri? {
        if (videoName.isBlank()) {
            return null
        }

        val sourceFile = withContext(Dispatchers.IO) {
            videoProcessor.getOriginalVideoFile(videoName)
        } ?: return null

        return VideoExportHelper.exportToGallery(
            context = context,
            sourceFile = sourceFile
        )
    }

    suspend fun exportProcessedVideo(context: Context, videoName: String): Uri? {
        if (videoName.isBlank()) {
            return null
        }

        val sourceFile = withContext(Dispatchers.IO) {
            videoProcessor.getProcessedVideoFile(videoName)
        } ?: return null

        return VideoExportHelper.exportToGallery(
            context = context,
            sourceFile = sourceFile
        )
    }

    fun submitVideoProcessing(videoName: String) {
        if (videoName.isBlank()) {
            return
        }

        videoProcessor.submitForProcessing(videoName)
    }

    suspend fun softDeleteRecord(
        state: TrainingHistoryState,
        record: MetaHistoryRecord
    ): TrainingHistoryState {
        val deleted = withContext(Dispatchers.IO) {
            trainingPlanStore.softDeleteMetaHistory(record.id)
        }

        if (!deleted) {
            return state
        }

        val updatedRecords = state.records.filter { it.id != record.id }
        val updatedSelectedRecord = if (state.selectedRecord?.id == record.id) {
            null
        } else {
            state.selectedRecord
        }

        return state.copy(
            records = updatedRecords,
            selectedRecord = updatedSelectedRecord
        )
    }

    suspend fun deleteSelectedRecords(state: TrainingHistoryState): TrainingHistoryState {
        val ids = state.selectedRecordIds.toList()

        if (ids.isEmpty()) {
            return state
        }

        val count = withContext(Dispatchers.IO) {
            trainingPlanStore.softDeleteMetaHistoryByIds(ids)
        }

        if (count == 0) {
            return state
        }

        val updatedRecords = state.records.filter { it.id !in state.selectedRecordIds }

        return state.copy(
            records = updatedRecords,
            selectedRecordIds = emptySet()
        )
    }

    suspend fun deleteDayRecords(
        state: TrainingHistoryState,
        dateKey: String
    ): TrainingHistoryState {
        val dayRecordIds = state.records
            .filter { it.date.take(10) == dateKey }
            .map { it.id }

        if (dayRecordIds.isEmpty()) {
            return state
        }

        val count = withContext(Dispatchers.IO) {
            trainingPlanStore.softDeleteMetaHistoryByIds(dayRecordIds)
        }

        if (count == 0) {
            return state
        }

        val updatedRecords = state.records.filter { it.date.take(10) != dateKey }

        return state.copy(records = updatedRecords)
    }

    fun toggleBatchMode(state: TrainingHistoryState): TrainingHistoryState {
        return state.copy(
            isBatchMode = !state.isBatchMode,
            selectedRecordIds = if (state.isBatchMode) emptySet() else state.selectedRecordIds
        )
    }

    fun toggleRecordSelection(
        state: TrainingHistoryState,
        recordId: Int
    ): TrainingHistoryState {
        val updatedSelection = if (recordId in state.selectedRecordIds) {
            state.selectedRecordIds - recordId
        } else {
            state.selectedRecordIds + recordId
        }

        return state.copy(selectedRecordIds = updatedSelection)
    }

    fun toggleDateGroup(
        state: TrainingHistoryState,
        dateKey: String
    ): TrainingHistoryState {
        val updatedExpanded = if (dateKey in state.expandedDateGroups) {
            state.expandedDateGroups - dateKey
        } else {
            state.expandedDateGroups + dateKey
        }

        return state.copy(expandedDateGroups = updatedExpanded)
    }

    fun initializeExpandedGroups(state: TrainingHistoryState): TrainingHistoryState {
        val todayKey = LocalDate.now().toString()

        return state.copy(expandedDateGroups = setOf(todayKey))
    }

    suspend fun loadBinState(): TrainingHistoryState {
        val records = withContext(Dispatchers.IO) {
            trainingPlanStore.getBinRecords()
        }

        return TrainingHistoryState(
            isBinMode = true,
            binRecords = records
        )
    }

    fun selectBinRecord(
        state: TrainingHistoryState,
        record: MetaHistoryRecord
    ): TrainingHistoryState {
        return state.copy(selectedBinRecord = record)
    }

    fun dismissBinRecord(state: TrainingHistoryState): TrainingHistoryState {
        return state.copy(selectedBinRecord = null)
    }

    suspend fun permanentlyDeleteBinRecord(
        state: TrainingHistoryState,
        binId: Int
    ): TrainingHistoryState {
        val deleted = withContext(Dispatchers.IO) {
            trainingPlanStore.permanentlyDeleteBinRecord(binId)
        }

        if (!deleted) {
            return state
        }

        val updatedBinRecords = state.binRecords.filter { it.id != binId }
        val updatedSelectedBinRecord = if (state.selectedBinRecord?.id == binId) {
            null
        } else {
            state.selectedBinRecord
        }

        return state.copy(
            binRecords = updatedBinRecords,
            selectedBinRecord = updatedSelectedBinRecord
        )
    }

    suspend fun revertBinRecord(
        state: TrainingHistoryState,
        binId: Int
    ): TrainingHistoryState {
        val reverted = withContext(Dispatchers.IO) {
            trainingPlanStore.revertBinRecord(binId)
        }

        if (!reverted) {
            return state
        }

        val updatedBinRecords = state.binRecords.filter { it.id != binId }
        val updatedSelectedBinRecord = if (state.selectedBinRecord?.id == binId) {
            null
        } else {
            state.selectedBinRecord
        }

        return state.copy(
            binRecords = updatedBinRecords,
            selectedBinRecord = updatedSelectedBinRecord
        )
    }

    suspend fun permanentlyDeleteSelectedBinRecords(
        state: TrainingHistoryState
    ): TrainingHistoryState {
        val ids = state.selectedRecordIds.toList()

        if (ids.isEmpty()) {
            return state
        }

        val count = withContext(Dispatchers.IO) {
            trainingPlanStore.permanentlyDeleteBinRecords(ids)
        }

        if (count == 0) {
            return state
        }

        val updatedBinRecords = state.binRecords.filter { it.id !in state.selectedRecordIds }

        return state.copy(
            binRecords = updatedBinRecords,
            selectedRecordIds = emptySet()
        )
    }

    suspend fun revertSelectedBinRecords(
        state: TrainingHistoryState
    ): TrainingHistoryState {
        val ids = state.selectedRecordIds.toList()

        if (ids.isEmpty()) {
            return state
        }

        val count = withContext(Dispatchers.IO) {
            trainingPlanStore.revertBinRecords(ids)
        }

        if (count == 0) {
            return state
        }

        val updatedBinRecords = state.binRecords.filter { it.id !in state.selectedRecordIds }

        return state.copy(
            binRecords = updatedBinRecords,
            selectedRecordIds = emptySet()
        )
    }

    fun exitBinMode(state: TrainingHistoryState): TrainingHistoryState {
        return state.copy(
            isBinMode = false,
            binRecords = emptyList(),
            selectedBinRecord = null,
            selectedRecordIds = emptySet(),
            isBatchMode = false
        )
    }

    private suspend fun attachVideoToRecord(
        state: TrainingHistoryState,
        historyId: Int,
        videoName: String
    ): TrainingHistoryState {
        val normalizedVideoName = videoName.trim()

        if (normalizedVideoName.isBlank()) {
            return state
        }

        val didUpdate = withContext(Dispatchers.IO) {
            trainingPlanStore.updateMetaHistoryVideoName(historyId, normalizedVideoName)
        }

        if (!didUpdate) {
            return state
        }

        val updatedRecord = state.records.firstOrNull { record ->
            record.id == historyId
        }?.copy(videoName = normalizedVideoName) ?: return state

        return state.withUpdatedRecord(updatedRecord)
    }

    private suspend fun updateImportedVideoMetadata(
        state: TrainingHistoryState,
        request: UpdateImportedVideoMetadataRequest
    ): TrainingHistoryState {
        val didUpdate = withContext(Dispatchers.IO) {
            trainingPlanStore.updateImportedVideoMetadata(request)
        }

        if (!didUpdate) {
            return state
        }

        val refreshedRecord = withContext(Dispatchers.IO) {
            trainingPlanStore.getMetaHistoryRecords().firstOrNull { record ->
                record.id == request.historyId
            }
        } ?: return state

        return state.withUpdatedRecord(refreshedRecord)
    }

    private fun importVideoIntoAppStorage(
        context: Context,
        uri: Uri,
        motionId: Int,
        setIndex: Int
    ): String? {
        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val outputFile = File(outputDir, importedVideoFileName(motionId, setIndex))

        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                outputFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return null

            outputFile.name
        } catch (_: IOException) {
            null
        }
    }

    private fun importedVideoFileName(motionId: Int, setIndex: Int): String {
        val formatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd-HH-mm-ss")
            .withZone(ZoneId.systemDefault())

        return "${formatter.format(Instant.now())}-${motionId}-${setIndex}.mp4"
    }
}

private fun TrainingHistoryState.withUpdatedRecord(record: MetaHistoryRecord): TrainingHistoryState {
    val updatedRecords = records.map { historyRecord ->
        if (historyRecord.id == record.id) {
            record
        } else {
            historyRecord
        }
    }

    val updatedSelectedRecord = selectedRecord
        ?.takeIf { selected -> selected.id == record.id }
        ?.let { record }
        ?: updatedRecords.firstOrNull { historyRecord -> historyRecord.id == record.id }

    return copy(
        records = updatedRecords,
        selectedRecord = updatedSelectedRecord
    )
}
