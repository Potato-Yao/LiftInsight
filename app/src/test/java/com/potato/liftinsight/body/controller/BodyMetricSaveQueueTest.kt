package com.potato.liftinsight.body.controller

import com.potato.liftinsight.R
import com.potato.liftinsight.body.model.BodyMetricState
import com.potato.liftinsight.body.model.BodyMetricSection
import com.potato.liftinsight.body.model.BodyState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BodyMetricSaveQueueTest {
    @Test
    fun rapidConsecutiveEdits_areSavedInOrderWithLatestValueLast() = runBlocking {
        val ownerScope = CoroutineScope(Dispatchers.Default + Job())
        val savedValues = mutableListOf<String>()
        val queue = BodyMetricSaveQueue(
            scope = ownerScope,
            saveBodyMetrics = { state ->
                savedValues += state.bodyMetrics.single().value
            },
            onSaveFailure = { throw it }
        )

        queue.enqueue(bodyState("old"))
        queue.enqueue(bodyState("new"))
        queue.awaitIdle()

        assertEquals(listOf("old", "new"), savedValues)
        ownerScope.cancel()
    }

    @Test
    fun queuedSave_completesAfterCallerScopeIsCancelled() = runBlocking {
        val ownerScope = CoroutineScope(Dispatchers.Default + Job())
        val callerScope = CoroutineScope(Dispatchers.Default + Job())
        val saveStarted = CompletableDeferred<Unit>()
        val allowSaveToFinish = CompletableDeferred<Unit>()
        val savedValues = mutableListOf<String>()
        val queue = BodyMetricSaveQueue(
            scope = ownerScope,
            saveBodyMetrics = { state ->
                saveStarted.complete(Unit)
                allowSaveToFinish.await()
                savedValues += state.bodyMetrics.single().value
            },
            onSaveFailure = { throw it }
        )

        callerScope.launch {
            queue.enqueue(bodyState("accepted"))
        }.join()
        saveStarted.await()
        callerScope.cancel()
        allowSaveToFinish.complete(Unit)
        queue.awaitIdle()

        assertEquals(listOf("accepted"), savedValues)
        ownerScope.cancel()
    }

    private fun bodyState(value: String): BodyState {
        return BodyState(
            bodyMetrics = listOf(
                BodyMetricState(
                    section = BodyMetricSection.SUMMARY,
                    titleResId = R.string.body_age,
                    value = value
                )
            )
        )
    }
}
