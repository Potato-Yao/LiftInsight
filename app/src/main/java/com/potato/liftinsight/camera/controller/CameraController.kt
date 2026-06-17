package com.potato.liftinsight.camera.controller

import android.content.Context
import android.os.Environment
import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CameraController(
    private val logger: AppLogger = AndroidAppLogger
) {
    fun createVideoOutputFile(
        context: Context,
        motionId: Int,
        setIndex: Int
    ): File {
        val outputDir = videoOutputDirectory(context)

        if (!outputDir.exists()) {
            logger.debug(TAG, "createVideoOutputFile: outputDir created=${outputDir.mkdirs()}")
        }

        val result = File(outputDir, videoFileName(motionId, setIndex))
        logger.debug(TAG, "createVideoOutputFile: motionId=$motionId, setIndex=$setIndex, outputDir=${outputDir.absolutePath}")
        logger.debug(TAG, "createVideoOutputFile result: ${result.name}")
        return result
    }

    fun videoOutputDirectory(context: Context): File {
        return context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir
    }

    companion object {
        private const val TAG = "CameraController"
    }
}

internal fun videoFileName(motionId: Int, setIndex: Int): String {
    val formatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd-HH-mm-ss")
        .withZone(ZoneId.systemDefault())

    return "${formatter.format(Instant.now())}-$motionId-$setIndex.mp4"
}
