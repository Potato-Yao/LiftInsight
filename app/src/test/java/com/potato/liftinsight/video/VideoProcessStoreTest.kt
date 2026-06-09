package com.potato.liftinsight.video

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.potato.liftinsight.common.logging.RecordingAppLogger
import com.potato.liftinsight.training.data.LiftInsightDatabase
import com.potato.liftinsight.training.data.VideoProcessState
import com.potato.liftinsight.training.data.VideoProcessStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
class VideoProcessStoreTest {
    private lateinit var database: LiftInsightDatabase
    private lateinit var store: VideoProcessStore

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = LiftInsightDatabase.from(context)
        store = VideoProcessStore.fromDatabase(database, RecordingAppLogger())

        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
    }

    @Test
    fun getVideoProcessState_returnsNullWhenNoStateExists() = runBlocking {
        val result = withContext(Dispatchers.IO) {
            store.getVideoProcessState("no-such-video.mp4")
        }

        assertNull(result)
    }

    @Test
    fun upsertVideoProcessState_persistsState() = runBlocking {
        val entity = VideoProcessStateEntity(
            videoName = "test-video.mp4",
            state = VideoProcessState.PROCESSING.name,
            progress = 50,
            processedVideoName = null
        )

        withContext(Dispatchers.IO) {
            store.upsertVideoProcessState(entity)
        }

        val result = withContext(Dispatchers.IO) {
            store.getVideoProcessState("test-video.mp4")
        }

        assertNotNull(result)
        assertEquals(VideoProcessState.PROCESSING.name, result?.state)
        assertEquals(50, result?.progress)
    }

    @Test
    fun updateVideoProcessProgress_updatesProgress() = runBlocking {
        val entity = VideoProcessStateEntity(
            videoName = "progress-video.mp4",
            state = VideoProcessState.PROCESSING.name,
            progress = 0,
            processedVideoName = null
        )

        withContext(Dispatchers.IO) {
            store.upsertVideoProcessState(entity)
            store.updateVideoProcessProgress(
                videoName = "progress-video.mp4",
                state = VideoProcessState.PROCESSING.name,
                progress = 75
            )
        }

        val result = withContext(Dispatchers.IO) {
            store.getVideoProcessState("progress-video.mp4")
        }

        assertNotNull(result)
        assertEquals(75, result?.progress)
    }
}
