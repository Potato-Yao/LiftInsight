package com.potato.liftinsight.body.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.potato.liftinsight.R
import com.potato.liftinsight.body.controller.BodyController
import com.potato.liftinsight.body.controller.BodyMetricSaveQueue
import com.potato.liftinsight.common.logging.RecordingAppLogger
import com.potato.liftinsight.training.data.BodyMetricEntity
import com.potato.liftinsight.training.data.LiftInsightDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BodyMetricStoreTest {
    private lateinit var database: LiftInsightDatabase
    private lateinit var store: BodyMetricStore
    private lateinit var logger: RecordingAppLogger

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = LiftInsightDatabase.from(context)
        logger = RecordingAppLogger()
        store = BodyMetricStore.fromDatabase(database, logger)

        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
    }

    @Test
    fun loadMetrics_returnsEmptyWhenNoMetricsPersisted() = runBlocking {
        val results = withContext(Dispatchers.IO) {
            store.loadMetrics()
        }

        assertTrue(results.isEmpty())
    }

    @Test
    fun saveMetrics_persistsMetrics() = runBlocking {
        val metrics = listOf(
            BodyMetricEntity(id = 1, value = "25", updatedAt = 1000L),
            BodyMetricEntity(id = 2, value = "male", updatedAt = 2000L)
        )

        store.saveMetrics(metrics)

        val results = withContext(Dispatchers.IO) {
            store.loadMetrics()
        }

        assertEquals(2, results.size)
    }

    @Test
    fun saveMetrics_replacesExistingMetrics() = runBlocking {
        store.saveMetrics(listOf(BodyMetricEntity(id = 1, value = "old", updatedAt = 100L)))
        store.saveMetrics(listOf(BodyMetricEntity(id = 1, value = "new", updatedAt = 200L)))

        val results = withContext(Dispatchers.IO) {
            store.loadMetrics()
        }

        assertEquals(1, results.size)
        assertEquals("new", results[0].value)
    }

    @Test
    fun rapidQueuedSaves_persistLatestBodyState() = runBlocking {
        val ownerScope = CoroutineScope(Dispatchers.Default + Job())
        val controller = BodyController(store, logger)
        val queue = BodyMetricSaveQueue(
            scope = ownerScope,
            saveBodyMetrics = controller::saveBodyMetrics,
            onSaveFailure = { throw it }
        )
        val oldState = controller.updateBodyMetric(
            controller.emptyState(),
            R.string.body_age,
            "20"
        )
        val latestState = controller.updateBodyMetric(
            oldState,
            R.string.body_age,
            "21"
        )

        queue.enqueue(oldState)
        queue.enqueue(latestState)
        queue.awaitIdle()

        val age = store.loadMetrics().single { metric -> metric.id == R.string.body_age }
        assertEquals("21", age.value)
        ownerScope.cancel()
    }
}
