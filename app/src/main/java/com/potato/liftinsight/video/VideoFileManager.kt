package com.potato.liftinsight.video

import android.content.Context
import android.os.Environment
import java.io.File

internal class VideoFileManager(
    private val context: Context
) {
    fun resolveVideoFile(fileName: String): File {
        return File(videoDirectory(), fileName)
    }

    fun videoDirectory(): File {
        return context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
    }

    fun processedVideoName(videoName: String): String {
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

    companion object {
        internal const val INPUT_FILE_READY_CHECK_COUNT = 10
        internal const val INPUT_FILE_READY_DELAY_MS = 300L
    }
}
