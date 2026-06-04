package com.potato.liftinsight.video

import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoExportHelperTest {
    private lateinit var context: Context
    private lateinit var videoDirectory: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        videoDirectory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir
    }

    @Test
    fun exportToGallery_returnsNullWhenSourceFileDoesNotExist() = runBlocking {
        val missingFile = File(videoDirectory, "nonexistent-video.mp4")

        val result = VideoExportHelper.exportToGallery(context, missingFile)

        assertNull(result)
    }

    @Test
    fun exportToGallery_returnsNonNullForValidVideoFile() = runBlocking {
        val sourceFile = createVideoFile("export-test-video.mp4")

        val result = VideoExportHelper.exportToGallery(context, sourceFile)

        assertNotNull(result)
    }

    @Test
    fun buildExportFileName_appendsExportSuffix() {
        val result = VideoExportHelper.buildExportFileName("squat-session.mp4")

        assertEquals("squat-session_export.mp4", result)
    }

    @Test
    fun buildExportFileName_handlesFilenameWithoutExtension() {
        val result = VideoExportHelper.buildExportFileName("squat-session")

        assertEquals("squat-session_export.mp4", result)
    }

    @Test
    fun buildExportFileName_handlesMultiDotFilename() {
        val result = VideoExportHelper.buildExportFileName("video.copy.1.mp4")

        assertEquals("video.copy.1_export.mp4", result)
    }

    private fun createVideoFile(fileName: String): File {
        val file = File(videoDirectory, fileName)
        file.parentFile?.mkdirs()
        file.writeText("placeholder-video-content")
        return file
    }
}
