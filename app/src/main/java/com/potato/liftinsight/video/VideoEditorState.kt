package com.potato.liftinsight.video

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class VideoEditorState(
    initialSelection: VideoEditSelection
) {
    var selection by mutableStateOf(initialSelection)
        private set

    var selectedSegmentIndex by mutableStateOf<Int?>(null)
        private set

    private var history by mutableStateOf<List<VideoEditorSnapshot>>(emptyList())

    val timelineSegments: List<VideoTimelineSegment>
        get() = VideoEditSelections.timelineSegments(selection)

    val selectedSegment: VideoTimelineSegment?
        get() = selectedSegmentIndex
            ?.let { segmentIndex -> timelineSegments.getOrNull(segmentIndex) }

    val durationMs: Long
        get() = selection.durationMs

    val canUndo: Boolean
        get() = history.isNotEmpty()

    fun reset(selection: VideoEditSelection) {
        this.selection = selection
        selectedSegmentIndex = null
        history = emptyList()
    }

    fun selectSegment(segmentIndex: Int?) {
        selectedSegmentIndex = segmentIndex?.takeIf { index ->
            index in selection.keptRanges.indices
        }
    }

    fun selectSegmentAtEditedPosition(editedPositionMs: Long) {
        selectedSegmentIndex = VideoEditSelections.segmentIndexAtEditedPosition(
            selection = selection,
            editedPositionMs = editedPositionMs
        )
    }

    fun canSplitAt(editedPositionMs: Long): Boolean {
        return VideoEditSelections.canSplitAtEditedPosition(
            selection = selection,
            editedPositionMs = editedPositionMs
        )
    }

    fun splitAt(editedPositionMs: Long): Boolean {
        val updatedSelection = VideoEditSelections.splitAtEditedPosition(
            selection = selection,
            editedPositionMs = editedPositionMs
        )

        if (updatedSelection == selection) {
            return false
        }

        pushHistory()
        selection = updatedSelection
        selectSegmentAtEditedPosition(editedPositionMs)
        return true
    }

    fun deleteSelectedSegment(): Boolean {
        val segmentIndex = selectedSegmentIndex ?: return false
        val updatedSelection = VideoEditSelections.deleteSegment(
            selection = selection,
            segmentIndex = segmentIndex
        )

        if (updatedSelection == selection) {
            return false
        }

        pushHistory()
        selection = updatedSelection
        selectedSegmentIndex = segmentIndex.coerceAtMost(updatedSelection.keptRanges.lastIndex)
            .takeIf { updatedSelection.keptRanges.isNotEmpty() }
        return true
    }

    fun undo(): Boolean {
        val previousSnapshot = history.lastOrNull() ?: return false

        history = history.dropLast(1)
        selection = previousSnapshot.selection
        selectedSegmentIndex = previousSnapshot.selectedSegmentIndex
        return true
    }

    private fun pushHistory() {
        val updatedHistory = history + VideoEditorSnapshot(
            selection = selection,
            selectedSegmentIndex = selectedSegmentIndex
        )
        val overflow = updatedHistory.size - MAX_HISTORY_SIZE

        history = if (overflow <= 0) {
            updatedHistory
        } else {
            updatedHistory.drop(overflow)
        }
    }

    private data class VideoEditorSnapshot(
        val selection: VideoEditSelection,
        val selectedSegmentIndex: Int?
    )

    private companion object {
        const val MAX_HISTORY_SIZE = 10
    }
}
