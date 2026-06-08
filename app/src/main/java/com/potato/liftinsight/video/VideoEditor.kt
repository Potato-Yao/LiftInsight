package com.potato.liftinsight.video

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object VideoEditor {
    private const val DEFAULT_BUFFER_SIZE_BYTES = 1_048_576
    private const val TAG = "VideoEditor"

    @Volatile
    private var logger: AppLogger = AndroidAppLogger

    internal fun setLogger(logger: AppLogger) {
        this.logger = logger
    }

    suspend fun durationMs(file: File): Long = withContext(Dispatchers.IO) {
        logger.debug(TAG, "durationMs start: file=${file.name}")

        if (!file.exists()) {
            return@withContext 0L
        }

        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
        } finally {
            retriever.release()
        }
    }

    suspend fun applyEditInPlace(
        sourceFile: File,
        processedFile: File?,
        selection: VideoEditSelection
    ): Boolean = withContext(Dispatchers.IO) {
        logger.debug(TAG, "applyEditInPlace start: sourceFile=${sourceFile.name}")

        if (!sourceFile.exists()) {
            return@withContext false
        }

        val sourceTempFile = tempOutputFile(sourceFile) ?: return@withContext false
        val processedTempFile = if (processedFile?.exists() == true) {
            tempOutputFile(processedFile)
        } else {
            null
        }

        try {
            if (!writeEditedCopy(sourceFile, sourceTempFile, selection)) {
                return@withContext false
            }

            if (processedFile != null && processedTempFile != null) {
                if (!writeEditedCopy(processedFile, processedTempFile, selection)) {
                    return@withContext false
                }
            }

            if (!replaceFile(sourceTempFile, sourceFile)) {
                return@withContext false
            }

            if (processedFile != null && processedTempFile != null) {
                if (!replaceFile(processedTempFile, processedFile)) {
                    return@withContext false
                }
            }

            val result = true
            logger.debug(TAG, "applyEditInPlace result: $result")
            result
        } finally {
            if (sourceTempFile.exists()) {
                sourceTempFile.delete()
            }

            if (processedTempFile != null && processedTempFile.exists()) {
                processedTempFile.delete()
            }
        }
    }

    private fun writeEditedCopy(
        inputFile: File,
        outputFile: File,
        selection: VideoEditSelection
    ): Boolean {
        if (outputFile.exists()) {
            outputFile.delete()
        }

        val extractor = MediaExtractor()
        val retriever = MediaMetadataRetriever()

        try {
            extractor.setDataSource(inputFile.absolutePath)
            retriever.setDataSource(inputFile.absolutePath)

            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            try {
                val trackIndexMap = linkedMapOf<Int, Int>()
                var maxBufferSize = DEFAULT_BUFFER_SIZE_BYTES

                for (trackIndex in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(trackIndex)
                    trackIndexMap[trackIndex] = muxer.addTrack(format)
                    extractor.selectTrack(trackIndex)

                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        maxBufferSize = maxOf(
                            maxBufferSize,
                            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        )
                    }
                }

                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull()
                    ?.let { rotationDegrees ->
                        muxer.setOrientationHint(rotationDegrees)
                    }

                muxer.start()

                val buffer = ByteBuffer.allocate(maxBufferSize)
                val bufferInfo = android.media.MediaCodec.BufferInfo()
                var outputOffsetUs = 0L

                for (range in selection.keptRanges) {
                    val startUs = range.startMs * 1_000L
                    val endUs = range.endMs * 1_000L

                    extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                    while (true) {
                        val sampleTrackIndex = extractor.sampleTrackIndex

                        if (sampleTrackIndex < 0) {
                            break
                        }

                        val sampleTimeUs = extractor.sampleTime

                        if (sampleTimeUs < 0L) {
                            break
                        }

                        if (sampleTimeUs < startUs) {
                            extractor.advance()
                            continue
                        }

                        if (sampleTimeUs > endUs) {
                            break
                        }

                        bufferInfo.offset = 0
                        bufferInfo.size = extractor.readSampleData(buffer, 0)

                        if (bufferInfo.size < 0) {
                            break
                        }

                        bufferInfo.presentationTimeUs = outputOffsetUs + (sampleTimeUs - startUs)
                        bufferInfo.flags = extractor.sampleFlags

                        muxer.writeSampleData(
                            trackIndexMap.getValue(sampleTrackIndex),
                            buffer,
                            bufferInfo
                        )

                        extractor.advance()
                    }

                    outputOffsetUs += range.durationMs * 1_000L
                }
            } finally {
                try {
                    muxer.stop()
                } catch (error: Exception) {
                    logger.warn(TAG, "Failed to stop muxer during writeEditedCopy cleanup")
                }

                muxer.release()
            }

            return outputFile.exists() && outputFile.length() > 0L
        } catch (error: Exception) {
            logger.error(TAG, "Failed to write edited video copy: input=${inputFile.name}", error)
            if (outputFile.exists()) {
                outputFile.delete()
            }

            return false
        } finally {
            retriever.release()
            extractor.release()
        }
    }

    private fun replaceFile(tempFile: File, targetFile: File): Boolean {
        if (!tempFile.exists()) {
            return false
        }

        val backupFile = File(targetFile.parentFile, "${targetFile.name}.bak")

        if (backupFile.exists()) {
            backupFile.delete()
        }

        if (targetFile.exists() && !targetFile.renameTo(backupFile)) {
            return false
        }

        if (!tempFile.renameTo(targetFile)) {
            if (backupFile.exists()) {
                backupFile.renameTo(targetFile)
            }

            return false
        }

        if (backupFile.exists()) {
            backupFile.delete()
        }

        return true
    }

    private fun tempOutputFile(targetFile: File): File? {
        val parent = targetFile.parentFile ?: return null

        if (!parent.exists() && !parent.mkdirs()) {
            return null
        }

        return File(parent, "${targetFile.nameWithoutExtension}-editing.mp4")
    }
}
