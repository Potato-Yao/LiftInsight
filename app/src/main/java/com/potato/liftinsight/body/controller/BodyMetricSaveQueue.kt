package com.potato.liftinsight.body.controller

import com.potato.liftinsight.body.model.BodyState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel

internal class BodyMetricSaveQueue(
    scope: CoroutineScope,
    private val saveBodyMetrics: suspend (BodyState) -> Unit,
    private val onSaveFailure: (Throwable) -> Unit
) {
    private val commands = Channel<SaveCommand>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (command in commands) {
                when (command) {
                    is SaveCommand.Save -> save(command.state)
                    is SaveCommand.Flush -> command.completed.complete(Unit)
                }
            }
        }
    }

    fun enqueue(state: BodyState) {
        commands.trySend(SaveCommand.Save(state))
    }

    internal suspend fun awaitIdle() {
        val completed = CompletableDeferred<Unit>()
        commands.send(SaveCommand.Flush(completed))
        completed.await()
    }

    private suspend fun save(state: BodyState) {
        try {
            saveBodyMetrics(state)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            onSaveFailure(error)
        }
    }

    private sealed interface SaveCommand {
        data class Save(val state: BodyState) : SaveCommand

        data class Flush(val completed: CompletableDeferred<Unit>) : SaveCommand
    }
}
