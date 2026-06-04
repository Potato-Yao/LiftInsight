package com.potato.liftinsight.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.potato.liftinsight.R
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

internal data class PoseOverlayLandmark(
    val x: Float,
    val y: Float,
    val visibility: Float
)

internal data class PoseOverlaySpinePoints(
    val midShoulder: Pair<Float, Float>,
    val midSpine: Pair<Float, Float>,
    val midHip: Pair<Float, Float>
)

internal data class PoseOverlayAngles(
    val spineAngle: Double?,
    val leftLegSpineAngle: Double?,
    val rightLegSpineAngle: Double?,
    val leftKneeAngle: Double?,
    val rightKneeAngle: Double?
) {
    fun toDisplayLines(context: Context): List<String> {
        val lines = mutableListOf<String>()

        spineAngle?.let {
            lines += context.getString(R.string.training_video_overlay_spine_angle, it)
        }
        leftLegSpineAngle?.let {
            lines += context.getString(R.string.training_video_overlay_left_leg_spine_angle, it)
        }
        rightLegSpineAngle?.let {
            lines += context.getString(R.string.training_video_overlay_right_leg_spine_angle, it)
        }
        leftKneeAngle?.let {
            lines += context.getString(R.string.training_video_overlay_left_knee_angle, it)
        }
        rightKneeAngle?.let {
            lines += context.getString(R.string.training_video_overlay_right_knee_angle, it)
        }

        return lines
    }
}

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
            return PoseLandmarkVideoProcessor(
                context = context.applicationContext,
                database = LiftInsightDatabase.from(context.applicationContext),
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
    private val database: LiftInsightDatabase,
    private val logger: AppLogger
) : VideoProcessor {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queuedVideoNames = ConcurrentHashMap.newKeySet<String>()

    private val poseDetector: PoseDetector by lazy {
        val options = AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE)
            .build()

        PoseDetection.getClient(options)
    }

    private val linePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.GREEN
    }

    private val pointPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.RED
    }

    private val spinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.CYAN
    }

    private val spinePointPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.YELLOW
    }

    private val midSpinePointPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.MAGENTA
    }

    private val textBackgroundPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.argb(160, 0, 0, 0)
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.WHITE
        textSize = 34f
    }

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

        val entity = database.planDao().getVideoProcessState(normalizedVideoName)
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
            resolveVideoFile(processedName).exists()
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
        val originalFile = resolveVideoFile(videoName.trim())
        return originalFile.takeIf { file -> file.exists() }
    }

    override fun getProcessedVideoFile(videoName: String): File? {
        val status = getStatus(videoName)
        val processedVideoName = status.processedVideoName ?: return null
        val processedFile = resolveVideoFile(processedVideoName)

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

        val originalFile = resolveVideoFile(normalizedVideoName)
        return originalFile.takeIf { file -> file.exists() }
    }

    private suspend fun processVideo(videoName: String) = withContext(Dispatchers.IO) {
        val inputFile = awaitInputVideoFile(videoName)

        if (inputFile == null) {
            logger.warn(TAG, "Cannot process video because the finalized source file was not ready: videoName=$videoName")
            persistState(
                VideoProcessStateEntity(
                    videoName = videoName,
                    state = VideoProcessState.ERROR.name,
                    progress = 0,
                    processedVideoName = null
                )
            )
            return@withContext
        }

        val processedVideoName = processedVideoName(videoName)
        val outputFile = resolveVideoFile(processedVideoName)

        logger.debug(TAG, "Starting pose landmark processing: videoName=$videoName, output=${outputFile.name}")

        persistState(
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

            persistState(
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

            persistState(
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
        val encoderSession = VideoEncoderSession(
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
                val processedFrame = detectAndDrawPose(preparedFrame)
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
                    database.planDao().updateVideoProcessProgress(
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
        val inputFile = resolveVideoFile(videoName)

        repeat(INPUT_FILE_READY_CHECK_COUNT) {
            if (isVideoFileReady(inputFile)) {
                return inputFile
            }

            delay(INPUT_FILE_READY_DELAY_MS)
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

    private fun detectAndDrawPose(bitmap: Bitmap): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val pose = runCatching {
            Tasks.await(
                poseDetector.process(InputImage.fromBitmap(mutableBitmap, 0))
            )
        }.getOrNull()

        if (pose != null) {
            drawPoseLandmarks(
                canvas = canvas,
                pose = pose,
                linePaint = linePaint,
                pointPaint = pointPaint,
                pointRadius = 6f
            )
        }

        return mutableBitmap
    }

    private fun drawPoseLandmarks(
        canvas: Canvas,
        pose: Pose,
        linePaint: Paint,
        pointPaint: Paint,
        pointRadius: Float
    ) {
        val positions = mutableMapOf<Int, PoseOverlayLandmark>()

        for (type in OVERLAY_LANDMARK_TYPES) {
            val landmark = pose.getPoseLandmark(type) ?: continue

            if (landmark.inFrameLikelihood < MIN_LANDMARK_CONFIDENCE) {
                continue
            }

            positions[type] = PoseOverlayLandmark(
                x = landmark.position.x,
                y = landmark.position.y,
                visibility = landmark.inFrameLikelihood
            )
            canvas.drawCircle(
                landmark.position.x,
                landmark.position.y,
                pointRadius,
                pointPaint
            )
        }

        OVERLAY_CONNECTIONS.forEach { (startType, endType) ->
            val start = positions[startType] ?: return@forEach
            val end = positions[endType] ?: return@forEach

            canvas.drawLine(
                start.x,
                start.y,
                end.x,
                end.y,
                linePaint
            )
        }

        val spinePoints = calculateSpinePoints(positions)
        if (spinePoints != null) {
            canvas.drawLine(
                spinePoints.midShoulder.first,
                spinePoints.midShoulder.second,
                spinePoints.midHip.first,
                spinePoints.midHip.second,
                spinePaint
            )
            canvas.drawCircle(
                spinePoints.midShoulder.first,
                spinePoints.midShoulder.second,
                pointRadius + 2f,
                spinePointPaint
            )
            canvas.drawCircle(
                spinePoints.midSpine.first,
                spinePoints.midSpine.second,
                pointRadius + 2f,
                midSpinePointPaint
            )
            canvas.drawCircle(
                spinePoints.midHip.first,
                spinePoints.midHip.second,
                pointRadius + 2f,
                spinePointPaint
            )
        }

        drawAngleOverlay(
            canvas = canvas,
            angles = calculateOverlayAngles(
                landmarks = positions,
                spinePoints = spinePoints,
                frameWidth = canvas.width.toFloat(),
                frameHeight = canvas.height.toFloat()
            )
        )
    }

    private fun drawAngleOverlay(
        canvas: Canvas,
        angles: PoseOverlayAngles
    ) {
        val lines = angles.toDisplayLines(context)
        if (lines.isEmpty()) {
            return
        }

        val padding = 20f
        val lineSpacing = 12f
        val fontMetrics = textPaint.fontMetrics
        val lineHeight = fontMetrics.bottom - fontMetrics.top
        val backgroundHeight = padding * 2 + lineHeight * lines.size + lineSpacing * (lines.size - 1)

        canvas.drawRect(
            0f,
            0f,
            canvas.width.toFloat(),
            backgroundHeight,
            textBackgroundPaint
        )

        var y = padding - fontMetrics.top
        lines.forEach { line ->
            canvas.drawText(line, padding, y, textPaint)
            y += lineHeight + lineSpacing
        }
    }

    private fun persistState(state: VideoProcessStateEntity) {
        database.planDao().upsertVideoProcessState(state)
    }

    private fun processedVideoName(videoName: String): String {
        val dotIndex = videoName.lastIndexOf('.')

        if (dotIndex < 0) {
            return "${videoName}_processed.mp4"
        }

        val extension = videoName.substring(dotIndex)
        return if (extension.equals(".mp4", ignoreCase = true)) {
            videoName.substring(0, dotIndex) + "_processed.mp4"
        } else {
            "${videoName}_processed.mp4"
        }
    }

    private fun resolveVideoFile(fileName: String): File {
        return File(videoDirectory(), fileName)
    }

    private fun videoDirectory(): File {
        return context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
    }

    companion object {
        private const val TAG = "VideoProcessor"
        private const val DEFAULT_FRAME_RATE = 30
        private const val MAX_FRAME_RATE = 60
        private const val MIN_FRAME_RATE = 12
        private const val MIN_LANDMARK_CONFIDENCE = 0.5f
        private const val INPUT_FILE_READY_CHECK_COUNT = 10
        private const val INPUT_FILE_READY_DELAY_MS = 300L

        private val OVERLAY_LANDMARK_TYPES = intArrayOf(
            PoseLandmark.NOSE,
            PoseLandmark.LEFT_EYE_INNER,
            PoseLandmark.LEFT_EYE,
            PoseLandmark.LEFT_EYE_OUTER,
            PoseLandmark.RIGHT_EYE_INNER,
            PoseLandmark.RIGHT_EYE,
            PoseLandmark.RIGHT_EYE_OUTER,
            PoseLandmark.LEFT_EAR,
            PoseLandmark.RIGHT_EAR,
            PoseLandmark.LEFT_MOUTH,
            PoseLandmark.RIGHT_MOUTH,
            PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_ELBOW,
            PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_PINKY,
            PoseLandmark.RIGHT_PINKY,
            PoseLandmark.LEFT_INDEX,
            PoseLandmark.RIGHT_INDEX,
            PoseLandmark.LEFT_THUMB,
            PoseLandmark.RIGHT_THUMB,
            PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE,
            PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_ANKLE,
            PoseLandmark.LEFT_HEEL,
            PoseLandmark.RIGHT_HEEL,
            PoseLandmark.LEFT_FOOT_INDEX,
            PoseLandmark.RIGHT_FOOT_INDEX
        )

        private val OVERLAY_CONNECTIONS = listOf(
            PoseLandmark.NOSE to PoseLandmark.LEFT_EYE_INNER,
            PoseLandmark.LEFT_EYE_INNER to PoseLandmark.LEFT_EYE,
            PoseLandmark.LEFT_EYE to PoseLandmark.LEFT_EYE_OUTER,
            PoseLandmark.LEFT_EYE_OUTER to PoseLandmark.LEFT_EAR,
            PoseLandmark.NOSE to PoseLandmark.RIGHT_EYE_INNER,
            PoseLandmark.RIGHT_EYE_INNER to PoseLandmark.RIGHT_EYE,
            PoseLandmark.RIGHT_EYE to PoseLandmark.RIGHT_EYE_OUTER,
            PoseLandmark.RIGHT_EYE_OUTER to PoseLandmark.RIGHT_EAR,
            PoseLandmark.LEFT_MOUTH to PoseLandmark.RIGHT_MOUTH,
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
            PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_WRIST to PoseLandmark.LEFT_PINKY,
            PoseLandmark.LEFT_WRIST to PoseLandmark.LEFT_INDEX,
            PoseLandmark.LEFT_WRIST to PoseLandmark.LEFT_THUMB,
            PoseLandmark.LEFT_PINKY to PoseLandmark.LEFT_INDEX,
            PoseLandmark.RIGHT_WRIST to PoseLandmark.RIGHT_PINKY,
            PoseLandmark.RIGHT_WRIST to PoseLandmark.RIGHT_INDEX,
            PoseLandmark.RIGHT_WRIST to PoseLandmark.RIGHT_THUMB,
            PoseLandmark.RIGHT_PINKY to PoseLandmark.RIGHT_INDEX,
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
            PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
            PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
            PoseLandmark.LEFT_ANKLE to PoseLandmark.LEFT_HEEL,
            PoseLandmark.LEFT_HEEL to PoseLandmark.LEFT_FOOT_INDEX,
            PoseLandmark.LEFT_ANKLE to PoseLandmark.LEFT_FOOT_INDEX,
            PoseLandmark.RIGHT_ANKLE to PoseLandmark.RIGHT_HEEL,
            PoseLandmark.RIGHT_HEEL to PoseLandmark.RIGHT_FOOT_INDEX,
            PoseLandmark.RIGHT_ANKLE to PoseLandmark.RIGHT_FOOT_INDEX
        )
    }
}

internal fun calculateSpinePoints(
    landmarks: Map<Int, PoseOverlayLandmark>
): PoseOverlaySpinePoints? {
    val leftShoulder = landmarks[PoseLandmark.LEFT_SHOULDER] ?: return null
    val rightShoulder = landmarks[PoseLandmark.RIGHT_SHOULDER] ?: return null
    val leftHip = landmarks[PoseLandmark.LEFT_HIP] ?: return null
    val rightHip = landmarks[PoseLandmark.RIGHT_HIP] ?: return null

    val minVisibility = minOf(
        leftShoulder.visibility,
        rightShoulder.visibility,
        leftHip.visibility,
        rightHip.visibility
    )
    if (minVisibility < 0.5f) {
        return null
    }

    val midShoulder = Pair(
        (leftShoulder.x + rightShoulder.x) / 2f,
        (leftShoulder.y + rightShoulder.y) / 2f
    )
    val midHip = Pair(
        (leftHip.x + rightHip.x) / 2f,
        (leftHip.y + rightHip.y) / 2f
    )
    val midSpine = Pair(
        (midShoulder.first + midHip.first) / 2f,
        (midShoulder.second + midHip.second) / 2f
    )

    return PoseOverlaySpinePoints(
        midShoulder = midShoulder,
        midSpine = midSpine,
        midHip = midHip
    )
}

internal fun calculateOverlayAngles(
    landmarks: Map<Int, PoseOverlayLandmark>,
    spinePoints: PoseOverlaySpinePoints?,
    frameWidth: Float = 1f,
    frameHeight: Float = 1f
): PoseOverlayAngles {
    val spineAngle = spinePoints?.let {
        Math.toDegrees(
            kotlin.math.atan2(
                ((it.midShoulder.first - it.midHip.first) * frameWidth).toDouble(),
                -(((it.midShoulder.second - it.midHip.second) * frameHeight).toDouble())
            )
        )
    }

    val leftLegSpineAngle = calculateLegSpineAngle(
        knee = landmarks[PoseLandmark.LEFT_KNEE],
        spinePoints = spinePoints,
        frameWidth = frameWidth,
        frameHeight = frameHeight
    )
    val rightLegSpineAngle = calculateLegSpineAngle(
        knee = landmarks[PoseLandmark.RIGHT_KNEE],
        spinePoints = spinePoints,
        frameWidth = frameWidth,
        frameHeight = frameHeight
    )

    val leftKneeAngle = calculateJointAngle(
        first = landmarks[PoseLandmark.LEFT_HIP],
        center = landmarks[PoseLandmark.LEFT_KNEE],
        third = landmarks[PoseLandmark.LEFT_ANKLE],
        frameWidth = frameWidth,
        frameHeight = frameHeight
    )
    val rightKneeAngle = calculateJointAngle(
        first = landmarks[PoseLandmark.RIGHT_HIP],
        center = landmarks[PoseLandmark.RIGHT_KNEE],
        third = landmarks[PoseLandmark.RIGHT_ANKLE],
        frameWidth = frameWidth,
        frameHeight = frameHeight
    )

    return PoseOverlayAngles(
        spineAngle = spineAngle,
        leftLegSpineAngle = leftLegSpineAngle,
        rightLegSpineAngle = rightLegSpineAngle,
        leftKneeAngle = leftKneeAngle,
        rightKneeAngle = rightKneeAngle
    )
}

private fun calculateLegSpineAngle(
    knee: PoseOverlayLandmark?,
    spinePoints: PoseOverlaySpinePoints?,
    frameWidth: Float,
    frameHeight: Float
): Double? {
    if (knee == null || spinePoints == null || knee.visibility < 0.5f) {
        return null
    }

    val spineDx = ((spinePoints.midShoulder.first - spinePoints.midHip.first) * frameWidth).toDouble()
    val spineDy = ((spinePoints.midShoulder.second - spinePoints.midHip.second) * frameHeight).toDouble()
    val legDx = ((knee.x - spinePoints.midHip.first) * frameWidth).toDouble()
    val legDy = ((knee.y - spinePoints.midHip.second) * frameHeight).toDouble()

    return angleBetweenVectors(spineDx, spineDy, legDx, legDy)
}

private fun calculateJointAngle(
    first: PoseOverlayLandmark?,
    center: PoseOverlayLandmark?,
    third: PoseOverlayLandmark?,
    frameWidth: Float,
    frameHeight: Float
): Double? {
    if (first == null || center == null || third == null) {
        return null
    }

    if (first.visibility < 0.5f || center.visibility < 0.5f || third.visibility < 0.5f) {
        return null
    }

    val firstDx = ((first.x - center.x) * frameWidth).toDouble()
    val firstDy = ((first.y - center.y) * frameHeight).toDouble()
    val thirdDx = ((third.x - center.x) * frameWidth).toDouble()
    val thirdDy = ((third.y - center.y) * frameHeight).toDouble()

    return angleBetweenVectors(firstDx, firstDy, thirdDx, thirdDy)
}

private fun angleBetweenVectors(
    firstDx: Double,
    firstDy: Double,
    secondDx: Double,
    secondDy: Double
): Double? {
    val firstLength = kotlin.math.sqrt(firstDx * firstDx + firstDy * firstDy)
    val secondLength = kotlin.math.sqrt(secondDx * secondDx + secondDy * secondDy)
    if (firstLength == 0.0 || secondLength == 0.0) {
        return null
    }

    val cosine = ((firstDx * secondDx) + (firstDy * secondDy)) / (firstLength * secondLength)
    return Math.toDegrees(kotlin.math.acos(cosine.coerceIn(-1.0, 1.0)))
}

private data class DecodedFrame(
    val bitmap: Bitmap,
    val timestampUs: Long
)

private class VideoEncoderSession(
    outputFile: File,
    width: Int,
    height: Int,
    frameRate: Int
) {
    private val encoder: MediaCodec = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE)
    private val muxer: MediaMuxer = createMuxer(outputFile)
    private val bufferInfo = MediaCodec.BufferInfo()
    private val inputBufferSize = width * height * 3 / 2
    private val inputMode = resolveInputMode(encoder, VIDEO_MIME_TYPE)

    private var muxerStarted = false
    private var outputTrackIndex = -1
    private var released = false

    init {
        outputFile.parentFile?.mkdirs()

        val format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                inputMode.colorFormat
            )
            setInteger(MediaFormat.KEY_BIT_RATE, (width * height * 5).coerceAtLeast(1_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
    }

    fun writeFrame(bitmap: Bitmap, presentationTimeUs: Long) {
        val frameData = if (inputMode.usesByteBuffer) {
            bitmapToYuv420(bitmap, inputMode.layout)
        } else {
            null
        }

        while (true) {
            drainEncoder(endOfStream = false)

            val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferIndex < 0) {
                continue
            }

            if (inputMode.usesByteBuffer) {
                val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                    ?: throw IllegalStateException("Encoder did not provide an input buffer")

                inputBuffer.clear()

                if (frameData == null || frameData.size != inputBufferSize) {
                    throw IllegalStateException("Encoded frame data size did not match the configured input size")
                }

                if (inputBuffer.remaining() < frameData.size) {
                    throw IllegalStateException(
                        "Encoder input buffer was smaller than the prepared frame data: capacity=${inputBuffer.remaining()}, required=${frameData.size}"
                    )
                }

                inputBuffer.put(frameData)
            } else {
                val inputImage = encoder.getInputImage(inputBufferIndex)
                    ?: throw IllegalStateException("Encoder did not provide an input image")

                inputImage.use {
                    copyBitmapToImage(bitmap, it)
                }
            }

            encoder.queueInputBuffer(
                inputBufferIndex,
                0,
                inputBufferSize,
                presentationTimeUs.coerceAtLeast(0L),
                0
            )

            return
        }
    }

    fun finish(lastPresentationTimeUs: Long) {
        while (true) {
            val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferIndex < 0) {
                drainEncoder(endOfStream = false)
                continue
            }

            encoder.queueInputBuffer(
                inputBufferIndex,
                0,
                0,
                lastPresentationTimeUs.coerceAtLeast(0L),
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
            break
        }

        drainEncoder(endOfStream = true)
    }

    fun release() {
        if (released) {
            return
        }

        released = true

        runCatching { encoder.stop() }
        runCatching { encoder.release() }

        if (muxerStarted) {
            runCatching { muxer.stop() }
        }

        runCatching { muxer.release() }
    }

    private fun drainEncoder(endOfStream: Boolean) {
        while (true) {
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) {
                        return
                    }
                }

                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) {
                        throw IllegalStateException("Encoder output format changed more than once")
                    }

                    outputTrackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }

                outputBufferIndex >= 0 -> {
                    val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                        ?: throw IllegalStateException("Missing encoder output buffer")

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size > 0) {
                        if (!muxerStarted) {
                            throw IllegalStateException("Muxer was not started before encoder output")
                        }

                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(outputTrackIndex, outputBuffer, bufferInfo)
                    }

                    val endOfStreamReached = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(outputBufferIndex, false)

                    if (endOfStreamReached) {
                        return
                    }
                }
            }
        }
    }

    companion object {
        private const val TIMEOUT_US = 10_000L
        private const val VIDEO_MIME_TYPE = "video/avc"

        private fun createMuxer(outputFile: File): MediaMuxer {
            outputFile.parentFile?.mkdirs()

            return MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
        }
    }
}

private data class EncoderInputMode(
    val colorFormat: Int,
    val layout: YuvLayout,
    val usesByteBuffer: Boolean
)

private enum class YuvLayout {
    PLANAR,
    SEMI_PLANAR,
    IMAGE
}

private fun resolveInputMode(encoder: MediaCodec, mimeType: String): EncoderInputMode {
    val capabilities = encoder.codecInfo.getCapabilitiesForType(mimeType)
    val supportedFormats = capabilities.colorFormats.toSet()

    val byteBufferSemiPlanarFormats = listOf(
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar
    )
    val byteBufferPlanarFormats = listOf(
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar
    )

    val semiPlanarFormat = byteBufferSemiPlanarFormats.firstOrNull { colorFormat ->
        supportedFormats.contains(colorFormat)
    }
    if (semiPlanarFormat != null) {
        return EncoderInputMode(
            colorFormat = semiPlanarFormat,
            layout = YuvLayout.SEMI_PLANAR,
            usesByteBuffer = true
        )
    }

    val planarFormat = byteBufferPlanarFormats.firstOrNull { colorFormat ->
        supportedFormats.contains(colorFormat)
    }
    if (planarFormat != null) {
        return EncoderInputMode(
            colorFormat = planarFormat,
            layout = YuvLayout.PLANAR,
            usesByteBuffer = true
        )
    }

    if (supportedFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)) {
        return EncoderInputMode(
            colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            layout = YuvLayout.IMAGE,
            usesByteBuffer = false
        )
    }

    throw IllegalStateException(
        "No supported YUV420 encoder input format was available. Supported=${capabilities.colorFormats.joinToString()}"
    )
}

private fun bitmapToYuv420(bitmap: Bitmap, layout: YuvLayout): ByteArray {
    if (layout == YuvLayout.IMAGE) {
        throw IllegalArgumentException("IMAGE layout cannot be written as a raw byte buffer")
    }

    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    val ySize = width * height
    val chromaPlaneSize = ySize / 4
    val output = ByteArray(ySize + (chromaPlaneSize * 2))

    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    var yIndex = 0
    var planarUIndex = ySize
    var planarVIndex = ySize + chromaPlaneSize
    var semiPlanarIndex = ySize

    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixel = pixels[(y * width) + x]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            val yValue = (((66 * r) + (129 * g) + (25 * b) + 128) shr 8) + 16
            val uValue = (((-38 * r) - (74 * g) + (112 * b) + 128) shr 8) + 128
            val vValue = (((112 * r) - (94 * g) - (18 * b) + 128) shr 8) + 128

            output[yIndex++] = yValue.coerceIn(0, 255).toByte()

            if (y % 2 == 0 && x % 2 == 0) {
                when (layout) {
                    YuvLayout.PLANAR -> {
                        output[planarUIndex++] = uValue.coerceIn(0, 255).toByte()
                        output[planarVIndex++] = vValue.coerceIn(0, 255).toByte()
                    }

                    YuvLayout.SEMI_PLANAR -> {
                        output[semiPlanarIndex++] = uValue.coerceIn(0, 255).toByte()
                        output[semiPlanarIndex++] = vValue.coerceIn(0, 255).toByte()
                    }

                    YuvLayout.IMAGE -> Unit
                }
            }
        }
    }

    return output
}

private fun copyBitmapToImage(bitmap: Bitmap, image: Image) {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)

    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]

    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer

    yBuffer.rewind()
    uBuffer.rewind()
    vBuffer.rewind()

    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixel = pixels[y * width + x]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            val yValue = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
            val uValue = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
            val vValue = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

            val yOffset = y * yPlane.rowStride + x * yPlane.pixelStride
            yBuffer.put(yOffset, yValue.coerceIn(0, 255).toByte())

            if (y % 2 == 0 && x % 2 == 0) {
                val chromaX = x / 2
                val chromaY = y / 2
                val uOffset = chromaY * uPlane.rowStride + chromaX * uPlane.pixelStride
                val vOffset = chromaY * vPlane.rowStride + chromaX * vPlane.pixelStride

                uBuffer.put(uOffset, uValue.coerceIn(0, 255).toByte())
                vBuffer.put(vOffset, vValue.coerceIn(0, 255).toByte())
            }
        }
    }
}

private inline fun <T : AutoCloseable?, R> T.use(block: (T) -> R): R {
    return try {
        block(this)
    } finally {
        this?.close()
    }
}
