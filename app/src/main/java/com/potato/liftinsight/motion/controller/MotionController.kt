package com.potato.liftinsight.motion.controller

import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.motion.model.MotionEditorMessage
import com.potato.liftinsight.motion.model.MotionEditorState
import com.potato.liftinsight.motion.model.MotionRowState
import com.potato.liftinsight.motion.model.MotionSectionState
import com.potato.liftinsight.motion.model.MotionState
import com.potato.liftinsight.training.data.CreateMotionRequest
import com.potato.liftinsight.training.data.MotionRecord
import com.potato.liftinsight.training.data.MotionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MotionMutationResult(
    val state: MotionState,
    val didChangeData: Boolean = false
)

class MotionController(
    private val motionStore: MotionStore,
    private val logger: AppLogger = AndroidAppLogger
) {
    fun emptyState(): MotionState {
        return MotionState()
    }

    suspend fun loadState(): MotionState {
        logDebug("Loading motion library state")
        return reloadState(emptyState())
    }

    fun openCreateMotion(state: MotionState): MotionState {
        logDebug("Opening motion create editor")
        return state.copy(editor = MotionEditorState())
    }

    fun openEditMotion(
        state: MotionState,
        motionId: Int
    ): MotionState {
        val motion = state.motions.firstOrNull { row -> row.id == motionId } ?: run {
            logWarn("Cannot open motion editor because the motion was not found: motionId=$motionId")
            return state
        }

        logDebug("Opening motion editor: motionId=$motionId")

        return state.copy(
            editor = MotionEditorState(
                motionId = motion.id,
                name = motion.name
            )
        )
    }

    fun updateEditorName(
        state: MotionState,
        name: String
    ): MotionState {
        val editor = state.editor ?: run {
            logWarn("Ignoring motion name update because no editor is open")
            return state
        }

        logDebug("Updating motion editor name: motionId=${editor.motionId ?: -1}")

        return state.copy(
            editor = editor.copy(
                name = name,
                message = null
            )
        )
    }

    fun closeEditor(state: MotionState): MotionState {
        logDebug("Closing motion editor")
        return state.copy(editor = null)
    }

    suspend fun submitMotion(state: MotionState): MotionMutationResult {
        val editor = state.editor ?: run {
            logWarn("Ignoring motion submission because no editor is open")
            return MotionMutationResult(state)
        }

        logDebug("Submitting motion editor: motionId=${editor.motionId ?: -1}")

        return withContext(Dispatchers.IO) {
            try {
                if (editor.motionId == null) {
                    motionStore.createMotion(CreateMotionRequest(name = editor.name))
                } else {
                    val updated = motionStore.updateMotion(
                        MotionRecord(
                            id = editor.motionId,
                            name = editor.name
                        )
                    )

                    if (!updated) {
                        logWarn("Motion update failed because the record could not be persisted: motionId=${editor.motionId}")
                        return@withContext MotionMutationResult(
                            state = state.copy(
                                editor = editor.copy(message = MotionEditorMessage.SaveFailed)
                            )
                        )
                    }
                }
            } catch (error: IllegalArgumentException) {
                logError(
                    message = "Motion submission failed validation: motionId=${editor.motionId ?: -1}",
                    throwable = error
                )

                return@withContext MotionMutationResult(
                    state = state.copy(
                        editor = editor.copy(
                            message = toEditorMessage(error)
                        )
                    )
                )
            }

            logDebug("Motion submission succeeded: motionId=${editor.motionId ?: -1}")

            MotionMutationResult(
                state = reloadState(state.copy(editor = null)),
                didChangeData = true
            )
        }
    }

    suspend fun deleteMotion(state: MotionState): MotionMutationResult {
        val editor = state.editor ?: run {
            logWarn("Ignoring motion delete request because no editor is open")
            return MotionMutationResult(state)
        }
        val motionId = editor.motionId ?: run {
            logWarn("Ignoring motion delete request because the motion has not been created yet")
            return MotionMutationResult(state)
        }

        logDebug("Deleting motion: motionId=$motionId")

        return withContext(Dispatchers.IO) {
            val deleted = motionStore.deleteMotion(motionId)

            if (!deleted) {
                logWarn("Motion delete was blocked: motionId=$motionId")
                return@withContext MotionMutationResult(
                    state = state.copy(
                        editor = editor.copy(message = MotionEditorMessage.DeleteBlocked)
                    )
                )
            }

            logDebug("Deleted motion: motionId=$motionId")

            MotionMutationResult(
                state = reloadState(state.copy(editor = null)),
                didChangeData = true
            )
        }
    }

    private suspend fun reloadState(state: MotionState): MotionState {
        return withContext(Dispatchers.IO) {
            val motions = motionStore.getMotions().map { motion ->
                MotionRowState(
                    id = motion.id,
                    name = motion.name
                )
            }

            state.copy(
                motions = motions,
                sections = buildSections(motions)
            ).also { updatedState ->
                logDebug(
                    "Loaded motion library state: motions=${updatedState.motions.size}, sections=${updatedState.sections.size}"
                )
            }
        }
    }

    private fun logDebug(message: String) {
        logger.debug(TAG, message)
    }

    private fun logWarn(message: String) {
        logger.warn(TAG, message)
    }

    private fun logError(message: String, throwable: Throwable) {
        logger.error(TAG, message, throwable)
    }

    companion object {
        private const val TAG = "MotionController"
    }
}

private fun buildSections(motions: List<MotionRowState>): List<MotionSectionState> {
    if (motions.isEmpty()) {
        return emptyList()
    }

    val sections = linkedMapOf<String, MutableList<MotionRowState>>()

    motions.forEach { motion ->
        val label = motionSectionLabel(motion.name)
        val items = sections.getOrPut(label) { mutableListOf() }
        items += motion
    }

    return sections.map { (label, rows) ->
        MotionSectionState(
            label = label,
            motions = rows.toList()
        )
    }
}

private fun motionSectionLabel(name: String): String {
    val firstCharacter = name.firstOrNull() ?: return "#"
    val label = firstCharacter.uppercaseChar().toString()

    if (label.first().isLetter()) {
        return label
    }

    return "#"
}

private fun toEditorMessage(error: IllegalArgumentException): MotionEditorMessage {
    val message = error.message.orEmpty()

    if (message.contains("required", ignoreCase = true)) {
        return MotionEditorMessage.BlankName
    }

    if (message.contains("unique", ignoreCase = true)) {
        return MotionEditorMessage.DuplicateName
    }

    return MotionEditorMessage.SaveFailed
}

