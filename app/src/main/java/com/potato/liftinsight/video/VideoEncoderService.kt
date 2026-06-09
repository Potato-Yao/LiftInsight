package com.potato.liftinsight.video

import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

internal class VideoEncoderService {
    fun createSession(
        outputFile: File,
        width: Int,
        height: Int,
        frameRate: Int
    ): VideoEncoderSession {
        return VideoEncoderSession(
            outputFile = outputFile,
            width = width,
            height = height,
            frameRate = frameRate
        )
    }
}

internal class VideoEncoderSession(
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

internal inline fun <T : AutoCloseable?, R> T.use(block: (T) -> R): R {
    return try {
        block(this)
    } finally {
        this?.close()
    }
}
