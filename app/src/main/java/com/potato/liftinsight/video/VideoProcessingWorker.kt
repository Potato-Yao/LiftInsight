package com.potato.liftinsight.video

import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.training.data.VideoProcessState
import com.potato.liftinsight.training.data.VideoProcessStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal class VideoProcessingWorker(
    private val videoFileManager: VideoFileManager,
    private val poseDetectionService: PoseDetectionService,
    private val videoEncoderService: VideoEncoderService,
    private val logger: AppLogger,
    private val videoProcessStore: VideoProcessStore
) {
    suspend fun processVideo(videoName: String, options: DrawingOptions) = withContext(Dispatchers.IO) {
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
                videoName = videoName,
                options = options
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
        inputFile: java.io.File,
        outputFile: java.io.File,
        videoName: String,
        options: DrawingOptions
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
                val processedFrame = poseDetectionService.detectAndDrawPose(preparedFrame, options)
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

    private fun readFrameTimestamps(inputFile: java.io.File): List<Long> {
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

    private suspend fun awaitInputVideoFile(videoName: String): java.io.File? {
        val inputFile = videoFileManager.resolveVideoFile(videoName)

        repeat(VideoFileManager.INPUT_FILE_READY_CHECK_COUNT) {
            if (isVideoFileReady(inputFile)) {
                return inputFile
            }

            delay(VideoFileManager.INPUT_FILE_READY_DELAY_MS)
        }

        return inputFile.takeIf(::isVideoFileReady)
    }

    private fun isVideoFileReady(inputFile: java.io.File): Boolean {
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

    private companion object {
        private const val TAG = "VideoProcessingWorker"
        const val DEFAULT_FRAME_RATE = 30
        const val MAX_FRAME_RATE = 60
        const val MIN_FRAME_RATE = 12
    }
}

private data class DecodedFrame(
    val bitmap: Bitmap,
    val timestampUs: Long
)
