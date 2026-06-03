package com.potato.liftinsight.record.controller

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.record.model.TrainingHistoryState
import com.potato.liftinsight.training.data.MetaHistoryRecord
import com.potato.liftinsight.training.data.UpdateImportedVideoMetadataRequest
import com.potato.liftinsight.video.VideoProcessor
import com.potato.liftinsight.video.imported.ImportedVideoAnalysisMode
import com.potato.liftinsight.video.imported.ImportedVideoSource
import java.io.File
import java.io.IOException
import java.time.Instant
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

    fun submitVideoProcessing(videoName: String) {
        if (videoName.isBlank()) {
            return
        }

        videoProcessor.submitForProcessing(videoName)
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
