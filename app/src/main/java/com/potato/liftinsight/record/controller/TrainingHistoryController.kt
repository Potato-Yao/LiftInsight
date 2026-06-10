package com.potato.liftinsight.record.controller

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.record.model.AnalysisVideoState
import com.potato.liftinsight.record.model.DisplayMode
import com.potato.liftinsight.record.model.TrainingHistoryState
import com.potato.liftinsight.training.data.HistoryRecord
import com.potato.liftinsight.training.data.MetaHistoryRecord
import com.potato.liftinsight.training.data.UpdateImportedVideoMetadataRequest
import com.potato.liftinsight.video.DrawingOptions
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
    private val videoProcessor: VideoProcessor,
    private val logger: AppLogger = AndroidAppLogger
) {
    fun emptyState(): TrainingHistoryState {
        return TrainingHistoryState()
    }

    private fun logDebug(message: String) {
        logger.debug(TAG, message)
    }

    private fun logWarn(message: String) {
        logger.warn(TAG, message)
    }

    private fun logError(message: String, throwable: Throwable) {
        logger.error(TAG, message, throwable)
    }

    companion object {
        private const val TAG = "TrainingHistoryController"
    }

    suspend fun loadState(): TrainingHistoryState {
        logDebug("loadState start")
        val records = withContext(Dispatchers.IO) {
            trainingPlanStore.getMetaHistoryRecords()
        }
        logDebug("loadState result: loaded ${records.size} records")

        return TrainingHistoryState(records = records)
    }

    fun selectRecord(state: TrainingHistoryState, record: MetaHistoryRecord): TrainingHistoryState {
        logDebug("selectRecord start: recordId=${record.id}")
        val selectedRecord = state.records.firstOrNull { historyRecord ->
            historyRecord.id == record.id
        } ?: record

        return state.copy(selectedRecord = selectedRecord)
    }

    fun dismissSelectedRecord(state: TrainingHistoryState): TrainingHistoryState {
        logDebug("dismissSelectedRecord start")
        return state.copy(selectedRecord = null)
    }

    fun clearVideoStatus(state: TrainingHistoryState): TrainingHistoryState {
        logDebug("clearVideoStatus start")
        return state.copy(selectedVideoStatus = null)
    }

    fun requestVideoStatusRefresh(state: TrainingHistoryState): TrainingHistoryState {
        logDebug("requestVideoStatusRefresh start")
        return state.copy(
            selectedVideoStatus = null,
            videoStatusRefreshKey = state.videoStatusRefreshKey + 1
        )
    }

    suspend fun refreshSelectedVideoStatus(state: TrainingHistoryState): TrainingHistoryState {
        logDebug("refreshSelectedVideoStatus start")
        val videoName = state.selectedRecord?.videoName

        if (videoName.isNullOrBlank()) {
            logWarn("refreshSelectedVideoStatus: videoName is null or blank, clearing video status")
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
        logDebug("updateRecordDetails start: historyId=$historyId")
        val didUpdate = withContext(Dispatchers.IO) {
            trainingPlanStore.updateMetaHistoryDetails(
                historyId = historyId,
                weight = weight,
                rep = rep,
                rpe = rpe
            )
        }

        if (!didUpdate) {
            logWarn("updateRecordDetails: update failed for historyId=$historyId")
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
        logDebug("attachLocalVideo start: recordId=${targetRecord.id}, uri=$uri")
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
        logDebug("attachCapturedVideo start: recordId=${targetRecord.id}, videoName=$videoName")
        if (videoName.isBlank()) {
            logWarn("attachCapturedVideo: videoName is blank")
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
        logDebug("updateReferenceCalibration start: recordId=${record.id}")
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
        logDebug("resolvePlaybackUri start: videoName=$videoName")
        val playbackFile = withContext(Dispatchers.IO) {
            videoProcessor.getPlaybackVideoFile(videoName)
        }

        return playbackFile?.let(Uri::fromFile)
    }

    suspend fun exportVideo(context: Context, videoName: String): Uri? {
        logDebug("exportVideo start: videoName=$videoName")
        if (videoName.isBlank()) {
            logWarn("exportVideo: videoName is blank")
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
        logDebug("exportOriginalVideo start: videoName=$videoName")
        if (videoName.isBlank()) {
            logWarn("exportOriginalVideo: videoName is blank")
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
        logDebug("exportProcessedVideo start: videoName=$videoName")
        if (videoName.isBlank()) {
            logWarn("exportProcessedVideo: videoName is blank")
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
        logDebug("submitVideoProcessing start: videoName=$videoName")
        if (videoName.isBlank()) {
            logWarn("submitVideoProcessing: videoName is blank")
            return
        }

        videoProcessor.submitForProcessing(videoName)
    }

    suspend fun submitAnalysisProcessing(
        state: TrainingHistoryState,
        videoName: String,
        analysisState: AnalysisVideoState,
        record: MetaHistoryRecord
    ): TrainingHistoryState {
        logDebug("submitAnalysisProcessing start: videoName=$videoName, analysisState=$analysisState")

        val drawingOptions = DrawingOptions(
            drawLandmarks = analysisState.poseDetection,
            drawAngles = analysisState.angleDisplay
        )

        withContext(Dispatchers.IO) {
            trainingPlanStore.updateAnalysisVideoState(
                recordId = record.id,
                poseDetection = analysisState.poseDetection,
                angleDisplay = analysisState.angleDisplay,
                anglePlot = analysisState.anglePlot,
                barbellDetection = analysisState.barbellDetection,
                powerCalculation = analysisState.powerCalculation
            )
            videoProcessor.resetProcessingState(videoName)
            videoProcessor.submitForProcessing(videoName, drawingOptions)
        }

        // Update the record in state with the new analysis values
        val updatedRecord = record.copy(
            poseDetection = analysisState.poseDetection,
            angleDisplay = analysisState.angleDisplay,
            anglePlot = analysisState.anglePlot,
            barbellDetection = analysisState.barbellDetection,
            powerCalculation = analysisState.powerCalculation
        )

        return requestVideoStatusRefresh(selectRecord(state, updatedRecord))
    }

    suspend fun softDeleteRecord(
        state: TrainingHistoryState,
        record: MetaHistoryRecord
    ): TrainingHistoryState {
        logDebug("softDeleteRecord start: recordId=${record.id}")
        val deleted = withContext(Dispatchers.IO) {
            trainingPlanStore.softDeleteMetaHistory(record.id)
        }

        if (!deleted) {
            logWarn("softDeleteRecord: deletion failed for recordId=${record.id}")
            return state
        }

        logDebug("softDeleteRecord: successfully deleted recordId=${record.id}")
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
        logDebug("deleteSelectedRecords start: count=${ids.size}")

        if (ids.isEmpty()) {
            logWarn("deleteSelectedRecords: no records selected")
            return state
        }

        val count = withContext(Dispatchers.IO) {
            trainingPlanStore.softDeleteMetaHistoryByIds(ids)
        }

        if (count == 0) {
            logWarn("deleteSelectedRecords: deletion failed, no records were deleted")
            return state
        }

        logDebug("deleteSelectedRecords: deleted $count records")
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
        logDebug("deleteDayRecords start: dateKey=$dateKey")
        val dayRecordIds = state.records
            .filter { it.date.take(10) == dateKey }
            .map { it.id }

        if (dayRecordIds.isEmpty()) {
            logWarn("deleteDayRecords: no records found for dateKey=$dateKey")
            return state
        }

        val count = withContext(Dispatchers.IO) {
            trainingPlanStore.softDeleteMetaHistoryByIds(dayRecordIds)
        }

        if (count == 0) {
            logWarn("deleteDayRecords: deletion failed, no records were deleted")
            return state
        }

        logDebug("deleteDayRecords: deleted $count records")
        val updatedRecords = state.records.filter { it.date.take(10) != dateKey }

        return state.copy(records = updatedRecords)
    }

    fun toggleBatchMode(state: TrainingHistoryState): TrainingHistoryState {
        logDebug("toggleBatchMode start: current=${state.isBatchMode}, new=${!state.isBatchMode}")
        return state.copy(
            isBatchMode = !state.isBatchMode,
            selectedRecordIds = if (state.isBatchMode) emptySet() else state.selectedRecordIds
        )
    }

    fun toggleRecordSelection(
        state: TrainingHistoryState,
        recordId: Int
    ): TrainingHistoryState {
        logDebug("toggleRecordSelection start: recordId=$recordId")
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
        logDebug("toggleDateGroup start: dateKey=$dateKey")
        val updatedExpanded = if (dateKey in state.expandedDateGroups) {
            state.expandedDateGroups - dateKey
        } else {
            state.expandedDateGroups + dateKey
        }

        return state.copy(expandedDateGroups = updatedExpanded)
    }

    fun initializeExpandedGroups(state: TrainingHistoryState): TrainingHistoryState {
        logDebug("initializeExpandedGroups start")
        val todayKey = LocalDate.now().toString()

        return state.copy(expandedDateGroups = setOf(todayKey))
    }

    suspend fun loadBinState(state: TrainingHistoryState): TrainingHistoryState {
        logDebug("loadBinState start")
        val binRecords = withContext(Dispatchers.IO) {
            trainingPlanStore.getBinRecords()
        }

        logDebug("loadBinState result: loaded ${binRecords.size} bin records")
        return state.copy(
            isBinMode = true,
            binRecords = binRecords,
            selectedRecord = null,
            selectedVideoStatus = null,
            isBatchMode = false,
            selectedRecordIds = emptySet()
        )
    }

    fun selectBinRecord(
        state: TrainingHistoryState,
        record: MetaHistoryRecord
    ): TrainingHistoryState {
        logDebug("selectBinRecord start: recordId=${record.id}")
        return state.copy(selectedBinRecord = record)
    }

    fun dismissBinRecord(state: TrainingHistoryState): TrainingHistoryState {
        logDebug("dismissBinRecord start")
        return state.copy(selectedBinRecord = null)
    }

    suspend fun permanentlyDeleteBinRecord(
        state: TrainingHistoryState,
        binId: Int
    ): TrainingHistoryState {
        logDebug("permanentlyDeleteBinRecord start: binId=$binId")
        val deleted = withContext(Dispatchers.IO) {
            trainingPlanStore.permanentlyDeleteBinRecord(binId)
        }

        if (!deleted) {
            logWarn("permanentlyDeleteBinRecord: deletion failed for binId=$binId")
            return state
        }

        logDebug("permanentlyDeleteBinRecord: successfully deleted binId=$binId")
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
        logDebug("revertBinRecord start: binId=$binId")
        val reverted = withContext(Dispatchers.IO) {
            trainingPlanStore.revertBinRecord(binId)
        }

        if (!reverted) {
            logWarn("revertBinRecord: revert failed for binId=$binId")
            return state
        }

        logDebug("revertBinRecord: successfully reverted binId=$binId")
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
        logDebug("permanentlyDeleteSelectedBinRecords start: count=${ids.size}")

        if (ids.isEmpty()) {
            logWarn("permanentlyDeleteSelectedBinRecords: no records selected")
            return state
        }

        val count = withContext(Dispatchers.IO) {
            trainingPlanStore.permanentlyDeleteBinRecords(ids)
        }

        if (count == 0) {
            logWarn("permanentlyDeleteSelectedBinRecords: deletion failed, no records were deleted")
            return state
        }

        logDebug("permanentlyDeleteSelectedBinRecords: deleted $count records")
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
        logDebug("revertSelectedBinRecords start: count=${ids.size}")

        if (ids.isEmpty()) {
            logWarn("revertSelectedBinRecords: no records selected")
            return state
        }

        val count = withContext(Dispatchers.IO) {
            trainingPlanStore.revertBinRecords(ids)
        }

        if (count == 0) {
            logWarn("revertSelectedBinRecords: revert failed, no records were reverted")
            return state
        }

        logDebug("revertSelectedBinRecords: reverted $count records")
        val updatedBinRecords = state.binRecords.filter { it.id !in state.selectedRecordIds }

        return state.copy(
            binRecords = updatedBinRecords,
            selectedRecordIds = emptySet()
        )
    }

    fun exitBinMode(state: TrainingHistoryState): TrainingHistoryState {
        logDebug("exitBinMode start")
        return state.copy(
            isBinMode = false,
            binRecords = emptyList(),
            selectedBinRecord = null,
            selectedRecordIds = emptySet(),
            isBatchMode = false
        )
    }

    fun switchDisplayMode(state: TrainingHistoryState, mode: DisplayMode): TrainingHistoryState {
        logDebug("switchDisplayMode start: mode=$mode")
        return state.copy(
            displayMode = mode,
            selectedHistorySession = null,
            sessionMetaHistoryRecords = emptyList()
        )
    }

    suspend fun loadHistorySessions(state: TrainingHistoryState): TrainingHistoryState {
        logDebug("loadHistorySessions start")
        val historyRecords = withContext(Dispatchers.IO) {
            trainingPlanStore.getHistoryRecords()
        }
        logDebug("loadHistorySessions result: loaded ${historyRecords.size} sessions")
        return state.copy(historyRecords = historyRecords)
    }

    suspend fun selectHistorySession(state: TrainingHistoryState, session: HistoryRecord): TrainingHistoryState {
        logDebug("selectHistorySession start: sessionId=${session.id}")
        val records = withContext(Dispatchers.IO) {
            trainingPlanStore.getMetaHistoryRecordsByHistoryId(session.id)
        }
        return state.copy(
            selectedHistorySession = session,
            sessionMetaHistoryRecords = records
        )
    }

    fun dismissHistorySession(state: TrainingHistoryState): TrainingHistoryState {
        logDebug("dismissHistorySession start")
        return state.copy(
            selectedHistorySession = null,
            sessionMetaHistoryRecords = emptyList()
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
        } catch (error: IOException) {
            logError("Failed to import video into app storage: uri=$uri", error)
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
