package com.potato.liftinsight.video

import android.content.Context
import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.training.data.BarbellFrameEntity
import com.potato.liftinsight.training.data.LiftInsightDatabase
import com.potato.liftinsight.training.data.VideoProcessState
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

data class VideoProcessingStatus(
    val videoName: String,
    val state: VideoProcessState,
    val progress: Int,
    val processedVideoName: String?
) {
    val hasProcessedCopy: Boolean
        get() = state == VideoProcessState.DONE && !processedVideoName.isNullOrBlank()

    val isProcessing: Boolean
        get() = state == VideoProcessState.PROCESSING
}

interface VideoProcessor {
    fun submitForProcessing(videoName: String)
    fun submitForProcessing(videoName: String, options: DrawingOptions)

    fun resetProcessingState(videoName: String)

    fun hasProcessedCopy(videoName: String): Boolean

    fun isProcessing(videoName: String): Boolean

    fun getProgress(videoName: String): Int

    fun getStatus(videoName: String): VideoProcessingStatus

    fun getOriginalVideoFile(videoName: String): File?

    fun getProcessedVideoFile(videoName: String): File?

    fun getPlaybackVideoFile(videoName: String): File?

    fun clearAnalysisData(metahistoryId: Int)

    fun clearPoseFrames(metahistoryId: Int)

    fun clearBarbellFrames(metahistoryId: Int)

    fun clearTimeseries(metahistoryId: Int)

    fun deleteVideoFiles(videoName: String)

    suspend fun trackBarbell(
        videoName: String,
        metahistoryId: Int,
        initialX: Float,
        initialY: Float,
        initialRadius: Float,
        onProgress: (Int) -> Unit
    ): List<BarbellFrameEntity>

    suspend fun trackBarbellHybrid(
        videoName: String,
        metahistoryId: Int,
        initialX: Float,
        initialY: Float,
        initialRadius: Float,
        initialX2: Float?,
        initialY2: Float?,
        onProgress: (Int) -> Unit
    ): List<BarbellFrameEntity>

    companion object {
        fun from(
            context: Context,
            logger: AppLogger = AndroidAppLogger
        ): VideoProcessor {
            val appContext = context.applicationContext
            val database = LiftInsightDatabase.from(appContext)
            val videoProcessStore = VideoProcessStore.fromDatabase(database, logger)
            val videoFileManager = VideoFileManager(appContext)
            val poseDetectionService = PoseDetectionService(appContext)
            val barbellDetectionService = BarbellDetectionService(logger)
            val barbellTrackingService = BarbellTrackingService(logger)
            val worker = VideoProcessingWorker(
                videoFileManager = videoFileManager,
                poseDetectionService = poseDetectionService,
                logger = logger,
                videoProcessStore = videoProcessStore
            )
            return PoseLandmarkVideoProcessor(
                context = appContext,
                videoProcessStore = videoProcessStore,
                videoFileManager = videoFileManager,
                worker = worker,
                barbellDetectionService = barbellDetectionService,
                barbellTrackingService = barbellTrackingService,
                poseDetectionService = poseDetectionService,
                logger = logger
            )
        }
    }
}

object NoOpVideoProcessor : VideoProcessor {
    override fun submitForProcessing(videoName: String) = Unit
    override fun submitForProcessing(videoName: String, options: DrawingOptions) = Unit

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

    override fun getOriginalVideoFile(videoName: String): File? = null

    override fun getProcessedVideoFile(videoName: String): File? = null

    override fun getPlaybackVideoFile(videoName: String): File? = null

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

private class PoseLandmarkVideoProcessor(
    private val context: Context,
    private val videoProcessStore: VideoProcessStore,
    private val videoFileManager: VideoFileManager,
    private val worker: VideoProcessingWorker,
    private val barbellDetectionService: BarbellDetectionService,
    private val barbellTrackingService: BarbellTrackingService,
    private val poseDetectionService: PoseDetectionService,
    private val logger: AppLogger
) : VideoProcessor {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queuedVideoNames = ConcurrentHashMap.newKeySet<String>()

    override fun submitForProcessing(videoName: String) {
        submitForProcessingInternal(videoName)
    }

    override fun submitForProcessing(videoName: String, options: DrawingOptions) {
        submitForProcessingInternal(videoName)
    }

    private fun submitForProcessingInternal(videoName: String) {
        val normalizedVideoName = videoName.trim()

        if (normalizedVideoName.isBlank()) {
            logger.warn(TAG, "Ignoring blank video processing request")
            return
        }

        if (!queuedVideoNames.add(normalizedVideoName)) {
            logger.debug(TAG, "Skipping duplicate queued video processing request: videoName=$normalizedVideoName")
            return
        }

        logger.info(TAG, "Queueing video for processing: videoName=$normalizedVideoName")

        VideoProcessingForegroundService.start(context)

        scope.launch {
            try {
                val status = loadStatus(normalizedVideoName, includeQueuedState = false)

                if (status.isProcessing || status.hasProcessedCopy) {
                    logger.debug(
                        TAG,
                        "Skipping video processing because work already exists: videoName=$normalizedVideoName, state=${status.state}, hasProcessedCopy=${status.hasProcessedCopy}"
                    )
                    return@launch
                }

                worker.processVideo(normalizedVideoName)
            } finally {
                queuedVideoNames.remove(normalizedVideoName)
                if (queuedVideoNames.isEmpty()) {
                    VideoProcessingForegroundService.stopWork(context)
                }
            }
        }
    }

    override suspend fun trackBarbell(
        videoName: String,
        metahistoryId: Int,
        initialX: Float,
        initialY: Float,
        initialRadius: Float,
        onProgress: (Int) -> Unit
    ): List<BarbellFrameEntity> {
        val inputFile = videoFileManager.resolveVideoFile(videoName)
        if (!inputFile.exists()) {
            logger.warn(TAG, "Video file not found for barbell tracking: videoName=$videoName")
            return emptyList()
        }

        barbellDetectionService.initialize()

        val entities = barbellTrackingService.trackSelectedCircle(
            videoFile = inputFile,
            initialX = initialX,
            initialY = initialY,
            initialRadius = initialRadius,
            metahistoryId = metahistoryId,
            detectionService = barbellDetectionService,
            onProgress = onProgress
        )

        // Persist to database
        if (entities.isNotEmpty()) {
            videoProcessStore.replaceBarbellFrames(metahistoryId, entities)
            logger.info(TAG, "Persisted ${entities.size} barbell frames from user-driven tracking: videoName=$videoName, metahistoryId=$metahistoryId")
        }

        return entities
    }

    override suspend fun trackBarbellHybrid(
        videoName: String,
        metahistoryId: Int,
        initialX: Float,
        initialY: Float,
        initialRadius: Float,
        initialX2: Float?,
        initialY2: Float?,
        onProgress: (Int) -> Unit
    ): List<BarbellFrameEntity> {
        val inputFile = videoFileManager.resolveVideoFile(videoName)
        if (!inputFile.exists()) {
            logger.warn(TAG, "Video file not found for hybrid barbell tracking: videoName=$videoName")
            return emptyList()
        }

        barbellDetectionService.initialize()

        val entities = barbellTrackingService.trackBarbellHybrid(
            videoFile = inputFile,
            initialX = initialX,
            initialY = initialY,
            initialRadius = initialRadius,
            initialX2 = initialX2,
            initialY2 = initialY2,
            metahistoryId = metahistoryId,
            detectionService = barbellDetectionService,
            poseDetectionService = poseDetectionService,
            onProgress = onProgress
        )

        if (entities.isNotEmpty()) {
            videoProcessStore.replaceBarbellFrames(metahistoryId, entities)
            logger.info(TAG, "Persisted ${entities.size} barbell frames from hybrid tracking: videoName=$videoName, metahistoryId=$metahistoryId")
        }

        return entities
    }

    override fun hasProcessedCopy(videoName: String): Boolean {
        return getProcessedVideoFile(videoName) != null
    }

    override fun isProcessing(videoName: String): Boolean {
        return getStatus(videoName).isProcessing
    }

    override fun getProgress(videoName: String): Int {
        return getStatus(videoName).progress
    }

    override fun getStatus(videoName: String): VideoProcessingStatus {
        return loadStatus(videoName.trim(), includeQueuedState = true)
    }

    private fun loadStatus(
        normalizedVideoName: String,
        includeQueuedState: Boolean
    ): VideoProcessingStatus {
        val isQueued = includeQueuedState && queuedVideoNames.contains(normalizedVideoName)

        if (normalizedVideoName.isBlank()) {
            return VideoProcessingStatus(
                videoName = normalizedVideoName,
                state = VideoProcessState.NOT_STARTED,
                progress = 0,
                processedVideoName = null
            )
        }

        val entity = videoProcessStore.getVideoProcessState(normalizedVideoName)
            ?: return VideoProcessingStatus(
                videoName = normalizedVideoName,
                state = if (isQueued) VideoProcessState.PROCESSING else VideoProcessState.NOT_STARTED,
                progress = 0,
                processedVideoName = null
            )

        val state = try {
            VideoProcessState.valueOf(entity.state)
        } catch (_: IllegalArgumentException) {
            VideoProcessState.NOT_STARTED
        }

        val processedVideoName = entity.processedVideoName?.takeIf { processedName ->
            videoFileManager.resolveVideoFile(processedName).exists()
        }
        val resolvedState = when {
            isQueued && state != VideoProcessState.DONE -> VideoProcessState.PROCESSING
            else -> state
        }

        return VideoProcessingStatus(
            videoName = entity.videoName,
            state = resolvedState,
            progress = when (resolvedState) {
                VideoProcessState.DONE -> 100
                VideoProcessState.PROCESSING -> entity.progress.coerceIn(0, 99)
                else -> entity.progress.coerceIn(0, 100)
            },
            processedVideoName = processedVideoName
        )
    }

    override fun getOriginalVideoFile(videoName: String): File? {
        val originalFile = videoFileManager.resolveVideoFile(videoName.trim())
        return originalFile.takeIf { file -> file.exists() }
    }

    override fun getProcessedVideoFile(videoName: String): File? {
        val status = getStatus(videoName)
        val processedVideoName = status.processedVideoName ?: return null
        val processedFile = videoFileManager.resolveVideoFile(processedVideoName)

        return processedFile.takeIf { file -> file.exists() }
    }

    override fun getPlaybackVideoFile(videoName: String): File? {
        val normalizedVideoName = videoName.trim()

        if (normalizedVideoName.isBlank()) {
            return null
        }

        val processedFile = getProcessedVideoFile(normalizedVideoName)
        if (processedFile != null) {
            return processedFile
        }

        val originalFile = videoFileManager.resolveVideoFile(normalizedVideoName)
        return originalFile.takeIf { file -> file.exists() }
    }

    override fun resetProcessingState(videoName: String) {
        val normalizedVideoName = videoName.trim()
        val status = loadStatus(normalizedVideoName, includeQueuedState = false)
        status.processedVideoName?.let { processedName ->
            videoFileManager.resolveVideoFile(processedName).delete()
        }
        videoProcessStore.deleteVideoProcessState(normalizedVideoName)
        queuedVideoNames.remove(normalizedVideoName)
    }

    override fun clearAnalysisData(metahistoryId: Int) {
        videoProcessStore.deleteTimeseries(metahistoryId)
        videoProcessStore.deletePoseFrames(metahistoryId)
        videoProcessStore.deleteBarbellFrames(metahistoryId)
    }

    override fun clearPoseFrames(metahistoryId: Int) {
        videoProcessStore.deletePoseFrames(metahistoryId)
    }

    override fun clearBarbellFrames(metahistoryId: Int) {
        videoProcessStore.deleteBarbellFrames(metahistoryId)
    }

    override fun clearTimeseries(metahistoryId: Int) {
        videoProcessStore.deleteTimeseries(metahistoryId)
    }

    override fun deleteVideoFiles(videoName: String) {
        val normalizedVideoName = videoName.trim()

        if (normalizedVideoName.isBlank()) {
            return
        }

        val originalFile = videoFileManager.resolveVideoFile(normalizedVideoName)
        if (originalFile.exists()) {
            originalFile.delete()
        }

        val processedVideoName = videoFileManager.processedVideoName(normalizedVideoName)
        val processedFile = videoFileManager.resolveVideoFile(processedVideoName)
        if (processedFile.exists()) {
            processedFile.delete()
        }

        videoProcessStore.deleteVideoProcessState(normalizedVideoName)
        videoProcessStore.deleteVideoExportState(normalizedVideoName)
        queuedVideoNames.remove(normalizedVideoName)
    }

    companion object {
        private const val TAG = "VideoProcessor"
    }
}
