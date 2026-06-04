package com.potato.liftinsight.record.controller

import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.training.data.LiftInsightDatabase
import com.potato.liftinsight.video.VideoProcessor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrainingHistoryControllerTest {
    private lateinit var context: Context
    private lateinit var database: LiftInsightDatabase
    private lateinit var trainingPlanStore: TrainingPlanStore
    private lateinit var videoProcessor: VideoProcessor
    private lateinit var controller: TrainingHistoryController

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = LiftInsightDatabase.from(context)
        trainingPlanStore = TrainingPlanStore.from(context)
        videoProcessor = VideoProcessor.from(context)

        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }

        controller = TrainingHistoryController(
            trainingPlanStore = trainingPlanStore,
            videoProcessor = videoProcessor
        )
    }

    @Test
    fun exportVideo_returnsNullWhenVideoNameIsBlank() = runBlocking {
        val result = controller.exportVideo(context, "  ")

        assertNull(result)
    }

    @Test
    fun exportVideo_returnsNullWhenVideoDoesNotExist() = runBlocking {
        val result = controller.exportVideo(context, "no-such-video.mp4")

        assertNull(result)
    }

    @Test
    fun exportVideo_returnsNonNullForValidVideo() = runBlocking {
        val videoFile = createVideoFile("export-controller-test.mp4")

        val result = controller.exportVideo(context, videoFile.name)

        assertNotNull(result)
    }

    @Test
    fun exportOriginalVideo_returnsNullWhenVideoNameIsBlank() = runBlocking {
        val result = controller.exportOriginalVideo(context, "  ")

        assertNull(result)
    }

    @Test
    fun exportOriginalVideo_returnsNullWhenVideoDoesNotExist() = runBlocking {
        val result = controller.exportOriginalVideo(context, "no-such-original.mp4")

        assertNull(result)
    }

    @Test
    fun exportOriginalVideo_returnsNonNullForExistingOriginalVideo() = runBlocking {
        val videoFile = createVideoFile("export-original-test.mp4")

        val result = controller.exportOriginalVideo(context, videoFile.name)

        assertNotNull(result)
    }

    @Test
    fun exportProcessedVideo_returnsNullWhenVideoNameIsBlank() = runBlocking {
        val result = controller.exportProcessedVideo(context, "  ")

        assertNull(result)
    }

    @Test
    fun exportProcessedVideo_returnsNullWhenProcessedFileDoesNotExist() = runBlocking {
        val result = controller.exportProcessedVideo(context, "no-such-processed.mp4")

        assertNull(result)
    }

    @Test
    fun exportProcessedVideo_returnsNonNullForExistingProcessedVideo() = runBlocking {
        val originalFile = createVideoFile("export-processed-test.mp4")
        val processedFile = createVideoFile("export-processed-test_processed.mp4")

        withContext(Dispatchers.IO) {
            database.planDao().upsertVideoProcessState(
                com.potato.liftinsight.training.data.VideoProcessStateEntity(
                    videoName = originalFile.name,
                    state = com.potato.liftinsight.training.data.VideoProcessState.DONE.name,
                    progress = 100,
                    processedVideoName = processedFile.name
                )
            )
        }

        val result = controller.exportProcessedVideo(
            context,
            "export-processed-test.mp4"
        )

        assertNotNull(result)
    }

    private fun createVideoFile(fileName: String): File {
        val videoDirectory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir
        val file = File(videoDirectory, fileName)

        file.parentFile?.mkdirs()
        file.writeText("placeholder-video-content")

        return file
    }
}
