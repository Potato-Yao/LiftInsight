package com.potato.liftinsight.motion.controller

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
    private val motionStore: MotionStore
) {
    fun emptyState(): MotionState {
        return MotionState()
    }

    suspend fun loadState(): MotionState {
        return reloadState(emptyState())
    }

    fun openCreateMotion(state: MotionState): MotionState {
        return state.copy(editor = MotionEditorState())
    }

    fun openEditMotion(
        state: MotionState,
        motionId: Int
    ): MotionState {
        val motion = state.motions.firstOrNull { row -> row.id == motionId } ?: return state

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
        val editor = state.editor ?: return state

        return state.copy(
            editor = editor.copy(
                name = name,
                message = null
            )
        )
    }

    fun closeEditor(state: MotionState): MotionState {
        return state.copy(editor = null)
    }

    suspend fun submitMotion(state: MotionState): MotionMutationResult {
        val editor = state.editor ?: return MotionMutationResult(state)

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
                        return@withContext MotionMutationResult(
                            state = state.copy(
                                editor = editor.copy(message = MotionEditorMessage.SaveFailed)
                            )
                        )
                    }
                }
            } catch (error: IllegalArgumentException) {
                return@withContext MotionMutationResult(
                    state = state.copy(
                        editor = editor.copy(
                            message = toEditorMessage(error)
                        )
                    )
                )
            }

            MotionMutationResult(
                state = reloadState(state.copy(editor = null)),
                didChangeData = true
            )
        }
    }

    suspend fun deleteMotion(state: MotionState): MotionMutationResult {
        val editor = state.editor ?: return MotionMutationResult(state)
        val motionId = editor.motionId ?: return MotionMutationResult(state)

        return withContext(Dispatchers.IO) {
            val deleted = motionStore.deleteMotion(motionId)

            if (!deleted) {
                return@withContext MotionMutationResult(
                    state = state.copy(
                        editor = editor.copy(message = MotionEditorMessage.DeleteBlocked)
                    )
                )
            }

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
            )
        }
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

