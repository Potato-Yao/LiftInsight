package com.potato.liftinsight.camera.controller

import android.content.Context
import android.os.Environment
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CameraController {
    fun createVideoOutputFile(
        context: Context,
        motionId: Int,
        setIndex: Int
    ): File {
        val outputDir = videoOutputDirectory(context)

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        return File(outputDir, videoFileName(motionId, setIndex))
    }

    fun fileProviderAuthority(context: Context): String {
        return "${context.packageName}.fileprovider"
    }

    fun videoOutputDirectory(context: Context): File {
        return context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir
    }
}

internal fun videoFileName(motionId: Int, setIndex: Int): String {
    val formatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd-HH-mm-ss")
        .withZone(ZoneId.systemDefault())

    return "${formatter.format(Instant.now())}-$motionId-$setIndex.mp4"
}
