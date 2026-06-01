package com.potato.liftinsight.motion

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.potato.liftinsight.motion.controller.MotionController
import com.potato.liftinsight.motion.model.MotionState
import kotlinx.coroutines.launch

@Composable
internal fun MotionTabHost(
    motionState: MotionState,
    onMotionStateChange: (MotionState) -> Unit,
    motionController: MotionController,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onMotionLibraryChanged: suspend () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()

    MotionScreen(
        state = motionState,
        onAddMotion = {
            onMotionStateChange(motionController.openCreateMotion(motionState))
        },
        onEditMotion = { motionId ->
            onMotionStateChange(motionController.openEditMotion(motionState, motionId))
        },
        onBackFromEditor = {
            onMotionStateChange(motionController.closeEditor(motionState))
        },
        onMotionNameChange = { name ->
            onMotionStateChange(motionController.updateEditorName(motionState, name))
        },
        onSubmitMotion = {
            coroutineScope.launch {
                val result = motionController.submitMotion(motionState)
                onMotionStateChange(result.state)
                if (result.didChangeData) {
                    onMotionLibraryChanged()
                }
            }
        },
        onDeleteMotion = {
            coroutineScope.launch {
                val result = motionController.deleteMotion(motionState)
                onMotionStateChange(result.state)
                if (result.didChangeData) {
                    onMotionLibraryChanged()
                }
            }
        },
        modifier = modifier.padding(contentPadding)
    )
}
