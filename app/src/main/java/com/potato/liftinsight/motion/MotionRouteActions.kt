package com.potato.liftinsight.motion

import com.potato.liftinsight.home.controller.HomeController
import com.potato.liftinsight.home.controller.HomeState
import com.potato.liftinsight.motion.controller.MotionController
import com.potato.liftinsight.motion.model.MotionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class MotionRouteActions(
    val onAddMotion: () -> Unit,
    val onEditMotionLibraryEntry: (Int) -> Unit,
    val onBackFromMotionEditor: () -> Unit,
    val onMotionNameChange: (String) -> Unit,
    val onSubmitMotion: () -> Unit,
    val onDeleteMotionFromLibrary: () -> Unit
)

internal fun buildMotionRouteActions(
    controller: MotionController,
    homeController: HomeController,
    currentMotionState: () -> MotionState,
    updateMotionState: (MotionState) -> Unit,
    currentHomeState: () -> HomeState,
    updateHomeState: (HomeState) -> Unit,
    coroutineScope: CoroutineScope
): MotionRouteActions {
    return MotionRouteActions(
        onAddMotion = {
            updateMotionState(controller.openCreateMotion(currentMotionState()))
        },
        onEditMotionLibraryEntry = { motionId ->
            updateMotionState(controller.openEditMotion(currentMotionState(), motionId))
        },
        onBackFromMotionEditor = {
            updateMotionState(controller.closeEditor(currentMotionState()))
        },
        onMotionNameChange = { name ->
            updateMotionState(controller.updateEditorName(currentMotionState(), name))
        },
        onSubmitMotion = {
            coroutineScope.launch {
                val result = controller.submitMotion(currentMotionState())
                updateMotionState(result.state)

                if (result.didChangeData) {
                    updateHomeState(homeController.refreshState(currentHomeState()))
                }
            }
        },
        onDeleteMotionFromLibrary = {
            coroutineScope.launch {
                val result = controller.deleteMotion(currentMotionState())
                updateMotionState(result.state)

                if (result.didChangeData) {
                    updateHomeState(homeController.refreshState(currentHomeState()))
                }
            }
        }
    )
}

