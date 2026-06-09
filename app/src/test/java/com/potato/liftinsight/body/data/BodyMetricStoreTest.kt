package com.potato.liftinsight.body.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.potato.liftinsight.common.logging.RecordingAppLogger
import com.potato.liftinsight.training.data.BodyMetricEntity
import com.potato.liftinsight.training.data.LiftInsightDatabase
import kotlinx.coroutines.Dispatchers
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

        withContext(Dispatchers.IO) {
            store.saveMetrics(metrics)
        }

        val results = withContext(Dispatchers.IO) {
            store.loadMetrics()
        }

        assertEquals(2, results.size)
    }

    @Test
    fun saveMetrics_replacesExistingMetrics() = runBlocking {
        withContext(Dispatchers.IO) {
            store.saveMetrics(listOf(BodyMetricEntity(id = 1, value = "old", updatedAt = 100L)))
            store.saveMetrics(listOf(BodyMetricEntity(id = 1, value = "new", updatedAt = 200L)))
        }

        val results = withContext(Dispatchers.IO) {
            store.loadMetrics()
        }

        assertEquals(1, results.size)
        assertEquals("new", results[0].value)
    }
}
