package com.potato.liftinsight.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.training.data.LiftInsightDatabase
import com.potato.liftinsight.training.data.VideoProcessState
import com.potato.liftinsight.training.data.VideoProcessStateEntity
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    fun hasProcessedCopy(videoName: String): Boolean

    fun isProcessing(videoName: String): Boolean

    fun getProgress(videoName: String): Int

    fun getStatus(videoName: String): VideoProcessingStatus

    fun getOriginalVideoFile(videoName: String): File?

    fun getProcessedVideoFile(videoName: String): File?

    fun getPlaybackVideoFile(videoName: String): File?

    companion object {
        fun from(
            context: Context,
            logger: AppLogger = AndroidAppLogger
        ): VideoProcessor {
            val appContext = context.applicationContext
            val database = LiftInsightDatabase.from(appContext)
            return PoseLandmarkVideoProcessor(
                context = appContext,
                videoProcessStore = VideoProcessStore.fromDatabase(database, logger),
                videoFileManager = VideoFileManager(appContext),
                poseDetectionService = PoseDetectionService(appContext),
                videoEncoderService = VideoEncoderService(),
                logger = logger
            )
        }
    }
}

object NoOpVideoProcessor : VideoProcessor {
    override fun submitForProcessing(videoName: String) = Unit

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
}

private class PoseLandmarkVideoProcessor(
    private val context: Context,
    private val videoProcessStore: VideoProcessStore,
    private val videoFileManager: VideoFileManager,
    private val poseDetectionService: PoseDetectionService,
    private val videoEncoderService: VideoEncoderService,
    private val logger: AppLogger
) : VideoProcessor {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queuedVideoNames = ConcurrentHashMap.newKeySet<String>()

    override fun submitForProcessing(videoName: String) {
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

                processVideo(normalizedVideoName)
            } finally {
                queuedVideoNames.remove(normalizedVideoName)
            }
        }
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
            state == VideoProcessState.DONE && processedVideoName == null -> {
                if (isQueued) {
                    VideoProcessState.PROCESSING
                } else {
                    VideoProcessState.NOT_STARTED
                }
            }

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

    private suspend fun processVideo(videoName: String) = withContext(Dispatchers.IO) {
        val inputFile = awaitInputVideoFile(videoName)

        if (inputFile == null) {
            logger.warn(TAG, "Cannot process video because the finalized source file was not ready: videoName=$videoName")
            videoProcessStore.upsertVideoProcessState(
                VideoProcessStateEntity(
                    videoName = videoName,
                    state = VideoProcessState.ERROR.name,
                    progress = 0,
                    processedVideoName = null
                )
            )
            return@withContext
        }

        val processedVideoName = videoFileManager.processedVideoName(videoName)
        val outputFile = videoFileManager.resolveVideoFile(processedVideoName)

        logger.debug(TAG, "Starting pose landmark processing: videoName=$videoName, output=${outputFile.name}")

        videoProcessStore.upsertVideoProcessState(
            VideoProcessStateEntity(
                videoName = videoName,
                state = VideoProcessState.PROCESSING.name,
                progress = 0,
                processedVideoName = null
            )
        )

        outputFile.delete()

        try {
            processFrames(
                inputFile = inputFile,
                outputFile = outputFile,
                videoName = videoName
            )

            videoProcessStore.upsertVideoProcessState(
                VideoProcessStateEntity(
                    videoName = videoName,
                    state = VideoProcessState.DONE.name,
                    progress = 100,
                    processedVideoName = processedVideoName
                )
            )
            logger.info(TAG, "Finished pose landmark processing: videoName=$videoName, output=${outputFile.name}")
        } catch (error: Exception) {
            outputFile.delete()

            logger.error(TAG, "Video processing failed: videoName=$videoName", error)

            videoProcessStore.upsertVideoProcessState(
                VideoProcessStateEntity(
                    videoName = videoName,
                    state = VideoProcessState.ERROR.name,
                    progress = 0,
                    processedVideoName = null
                )
            )
        }
    }

    private fun processFrames(
        inputFile: File,
        outputFile: File,
        videoName: String
    ) {
        val timestampsUs = readFrameTimestamps(inputFile)
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(inputFile.absolutePath)

        val firstFrameData = loadFirstAvailableFrame(
            retriever = retriever,
            timestampsUs = timestampsUs
        ) ?: throw IllegalStateException("Could not decode any video frame")
        val firstFrame = firstFrameData.bitmap
        val firstFrameTimestampUs = firstFrameData.timestampUs

        val frameSize = normalizeFrameSize(firstFrame.width, firstFrame.height)
        val frameRate = estimateFrameRate(timestampsUs)
        val encoderSession = videoEncoderService.createSession(
            outputFile = outputFile,
            width = frameSize.first,
            height = frameSize.second,
            frameRate = frameRate
        )

        var lastQueuedPresentationTimeUs = 0L
        var encodedFrameCount = 0

        try {
            timestampsUs.forEachIndexed { index, timestampUs ->
                val sourceFrame = if (index == 0) {
                    firstFrame
                } else {
                    loadFrameBitmap(
                        retriever = retriever,
                        presentationTimeUs = timestampUs,
                        width = frameSize.first,
                        height = frameSize.second
                    ) ?: return@forEachIndexed
                }

                val preparedFrame = ensureFrameSize(
                    bitmap = sourceFrame,
                    width = frameSize.first,
                    height = frameSize.second
                )
                val processedFrame = poseDetectionService.detectAndDrawPose(preparedFrame)
                val normalizedPresentationTimeUs = (timestampUs - firstFrameTimestampUs).coerceAtLeast(0L)

                encoderSession.writeFrame(
                    bitmap = processedFrame,
                    presentationTimeUs = normalizedPresentationTimeUs
                )

                lastQueuedPresentationTimeUs = normalizedPresentationTimeUs
                encodedFrameCount++

                if (encodedFrameCount % 100 == 0) {
                    logger.trace(
                        TAG,
                        "Processed video frames: videoName=$videoName, framesProcessed=$encodedFrameCount"
                    )
                }

                if (processedFrame !== preparedFrame) {
                    processedFrame.recycle()
                }

                if (preparedFrame !== sourceFrame) {
                    preparedFrame.recycle()
                }

                if (sourceFrame !== firstFrame) {
                    sourceFrame.recycle()
                }

                if (index == timestampsUs.lastIndex || index % 3 == 0) {
                    videoProcessStore.updateVideoProcessProgress(
                        videoName = videoName,
                        state = VideoProcessState.PROCESSING.name,
                        progress = (((index + 1) * 100) / timestampsUs.size).coerceIn(0, 99)
                    )
                }
            }

            if (encodedFrameCount == 0) {
                throw IllegalStateException("No frames were encoded")
            }

            encoderSession.finish(lastQueuedPresentationTimeUs)
        } finally {
            if (!firstFrame.isRecycled) {
                firstFrame.recycle()
            }

            retriever.release()
            encoderSession.release()
        }
    }

    private fun readFrameTimestamps(inputFile: File): List<Long> {
        val extractor = MediaExtractor()

        try {
            extractor.setDataSource(inputFile.absolutePath)

            val videoTrackIndex = selectVideoTrack(extractor)
                ?: throw IllegalStateException("No video track found")

            extractor.selectTrack(videoTrackIndex)

            val timestampsUs = mutableListOf<Long>()

            while (true) {
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0L) {
                    break
                }

                timestampsUs += sampleTimeUs

                if (!extractor.advance()) {
                    break
                }
            }

            return if (timestampsUs.isEmpty()) {
                listOf(0L)
            } else {
                timestampsUs
            }
        } finally {
            extractor.release()
        }
    }

    private suspend fun awaitInputVideoFile(videoName: String): File? {
        val inputFile = videoFileManager.resolveVideoFile(videoName)

        repeat(VideoFileManager.INPUT_FILE_READY_CHECK_COUNT) {
            if (isVideoFileReady(inputFile)) {
                return inputFile
            }

            delay(VideoFileManager.INPUT_FILE_READY_DELAY_MS)
        }

        return inputFile.takeIf(::isVideoFileReady)
    }

    private fun isVideoFileReady(inputFile: File): Boolean {
        if (!inputFile.exists() || inputFile.length() <= 0L) {
            return false
        }

        val retriever = MediaMetadataRetriever()

        return try {
            retriever.setDataSource(inputFile.absolutePath)

            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            val firstFrame = loadFrameBitmap(retriever, 0L)
            val isReady = durationMs > 0L && firstFrame != null

            firstFrame?.recycle()
            isReady
        } catch (_: Exception) {
            false
        } finally {
            retriever.release()
        }
    }

    private fun selectVideoTrack(extractor: MediaExtractor): Int? {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

            if (mime.startsWith("video/")) {
                return index
            }
        }

        return null
    }

    private fun loadFrameBitmap(
        retriever: MediaMetadataRetriever,
        presentationTimeUs: Long,
        width: Int? = null,
        height: Int? = null
    ): Bitmap? {
        val normalizedTimeUs = presentationTimeUs.coerceAtLeast(0L)

        return if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
            width != null &&
            height != null
        ) {
            retriever.getScaledFrameAtTime(
                normalizedTimeUs,
                MediaMetadataRetriever.OPTION_CLOSEST,
                width,
                height
            )
        } else {
            val bitmap = retriever.getFrameAtTime(
                normalizedTimeUs,
                MediaMetadataRetriever.OPTION_CLOSEST
            ) ?: return null

            if (width == null || height == null || (bitmap.width == width && bitmap.height == height)) {
                bitmap
            } else {
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
                bitmap.recycle()
                scaledBitmap
            }
        }
    }

    private fun loadFirstAvailableFrame(
        retriever: MediaMetadataRetriever,
        timestampsUs: List<Long>
    ): DecodedFrame? {
        timestampsUs.forEach { timestampUs ->
            val bitmap = loadFrameBitmap(
                retriever = retriever,
                presentationTimeUs = timestampUs
            )

            if (bitmap != null) {
                return DecodedFrame(
                    bitmap = bitmap,
                    timestampUs = timestampUs
                )
            }
        }

        return loadFrameBitmap(
            retriever = retriever,
            presentationTimeUs = 0L
        )?.let { bitmap ->
            DecodedFrame(
                bitmap = bitmap,
                timestampUs = 0L
            )
        }
    }

    private fun ensureFrameSize(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        if (bitmap.width == width && bitmap.height == height) {
            return bitmap
        }

        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun normalizeFrameSize(width: Int, height: Int): Pair<Int, Int> {
        val normalizedWidth = normalizeDimension(width)
        val normalizedHeight = normalizeDimension(height)

        return normalizedWidth to normalizedHeight
    }

    private fun normalizeDimension(value: Int): Int {
        if (value <= 2) {
            return 2
        }

        return if (value % 2 == 0) {
            value
        } else {
            value - 1
        }
    }

    private fun estimateFrameRate(timestampsUs: List<Long>): Int {
        if (timestampsUs.size < 2) {
            return DEFAULT_FRAME_RATE
        }

        val deltas = timestampsUs.zipWithNext { first, second -> second - first }
            .filter { delta -> delta > 0L }

        if (deltas.isEmpty()) {
            return DEFAULT_FRAME_RATE
        }

        val averageDeltaUs = deltas.average()
        if (averageDeltaUs <= 0.0) {
            return DEFAULT_FRAME_RATE
        }

        return (1_000_000.0 / averageDeltaUs)
            .toInt()
            .coerceIn(MIN_FRAME_RATE, MAX_FRAME_RATE)
    }

    companion object {
        private const val TAG = "VideoProcessor"
        private const val DEFAULT_FRAME_RATE = 30
        private const val MAX_FRAME_RATE = 60
        private const val MIN_FRAME_RATE = 12
    }
}

private data class DecodedFrame(
    val bitmap: Bitmap,
    val timestampUs: Long
)
