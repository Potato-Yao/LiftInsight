package com.potato.liftinsight.video

import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import com.potato.liftinsight.training.data.LiftInsightDatabase
import com.potato.liftinsight.training.data.VideoProcessState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoProcessorTest {
    private lateinit var context: Context
    private lateinit var database: LiftInsightDatabase
    private lateinit var videoProcessor: VideoProcessor

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = LiftInsightDatabase.from(context)
        videoProcessor = VideoProcessor.from(context)

        withContext(Dispatchers.IO) {
            database.clearAllTables()
            deleteVideoFile("missing-lift.mp4")
            deleteVideoFile("missing-lift_processed.mp4")
        }
    }

    @Test
    fun submitForProcessing_missingVideoMarksErrorWithoutCrashingCallerThread() = runBlocking {
        videoProcessor.submitForProcessing("missing-lift.mp4")

        val status = waitForStatus("missing-lift.mp4") { processingStatus ->
            processingStatus.state == VideoProcessState.ERROR
        }

        assertEquals(VideoProcessState.ERROR, status.state)
        assertFalse(status.hasProcessedCopy)
        assertFalse(status.isProcessing)
        assertEquals(
            null,
            withContext(Dispatchers.IO) {
                videoProcessor.getProcessedVideoFile("missing-lift.mp4")
            }
        )
    }

    @Test
    fun getPlaybackVideoFile_returnsOriginalVideoWhenProcessedCopyIsUnavailable() = runBlocking {
        val originalFile = createVideoFile("session-lift.mp4")

        val playbackFile = withContext(Dispatchers.IO) {
            videoProcessor.getPlaybackVideoFile("session-lift.mp4")
        }

        assertNotNull(playbackFile)
        assertEquals(originalFile.absolutePath, playbackFile?.absolutePath)
    }

    @Test
    fun getPlaybackVideoFile_prefersProcessedVideoWhenStateAndFileExist() = runBlocking {
        val originalFile = createVideoFile("session-processed-source.mp4")
        val processedFile = createVideoFile("session-processed-source_processed.mp4")

        withContext(Dispatchers.IO) {
            database.planDao().upsertVideoProcessState(
                com.potato.liftinsight.training.data.VideoProcessStateEntity(
                    videoName = originalFile.name,
                    state = VideoProcessState.DONE.name,
                    progress = 100,
                    processedVideoName = processedFile.name
                )
            )
        }

        val playbackFile = withContext(Dispatchers.IO) {
            videoProcessor.getPlaybackVideoFile(originalFile.name)
        }

        assertNotNull(playbackFile)
        assertEquals(processedFile.absolutePath, playbackFile?.absolutePath)
    }

    private suspend fun waitForStatus(
        videoName: String,
        condition: (VideoProcessingStatus) -> Boolean
    ): VideoProcessingStatus {
        repeat(120) {
            val status = withContext(Dispatchers.IO) {
                videoProcessor.getStatus(videoName)
            }

            if (condition(status)) {
                return status
            }

            delay(50L)
        }

        return withContext(Dispatchers.IO) {
            videoProcessor.getStatus(videoName)
        }
    }

    private fun deleteVideoFile(fileName: String) {
        val videoDirectory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir

        File(videoDirectory, fileName).delete()
    }

    private fun createVideoFile(fileName: String): File {
        val videoDirectory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir
        val file = File(videoDirectory, fileName)

        file.parentFile?.mkdirs()
        file.writeText("placeholder")

        return file
    }
}

