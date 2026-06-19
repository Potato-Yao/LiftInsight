package com.potato.liftinsight.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoEditorStateTest {
    @Test
    fun splitAtAddsHistoryAndCanUndo() {
        val state = VideoEditorState(VideoEditSelections.whole(12_000L))

        assertTrue(state.splitAt(5_000L))
        assertEquals(
            listOf(
                VideoEditRange(0L, 5_000L),
                VideoEditRange(5_000L, 12_000L)
            ),
            state.selection.keptRanges
        )
        assertEquals(1, state.selectedSegmentIndex)
        assertTrue(state.canUndo)

        assertTrue(state.undo())
        assertEquals(VideoEditSelections.whole(12_000L), state.selection)
        assertNull(state.selectedSegmentIndex)
        assertFalse(state.canUndo)
    }

    @Test
    fun splitNearEdgeDoesNotChangeState() {
        val state = VideoEditorState(VideoEditSelections.whole(12_000L))

        assertFalse(state.splitAt(200L))
        assertEquals(VideoEditSelections.whole(12_000L), state.selection)
        assertFalse(state.canUndo)
    }

    @Test
    fun deleteSelectedSegmentKeepsNeighborSelected() {
        val state = VideoEditorState(
            VideoEditSelection(
                keptRanges = listOf(
                    VideoEditRange(0L, 3_000L),
                    VideoEditRange(3_000L, 7_000L),
                    VideoEditRange(7_000L, 12_000L)
                )
            )
        )

        state.selectSegment(1)

        assertTrue(state.deleteSelectedSegment())
        assertEquals(
            listOf(
                VideoEditRange(0L, 3_000L),
                VideoEditRange(7_000L, 12_000L)
            ),
            state.selection.keptRanges
        )
        assertEquals(1, state.selectedSegmentIndex)
        assertTrue(state.canUndo)
    }

    @Test
    fun resetClearsSelectionAndUndoHistory() {
        val state = VideoEditorState(VideoEditSelections.whole(12_000L))

        state.splitAt(5_000L)
        state.selectSegment(0)
        state.reset(VideoEditSelections.whole(8_000L))

        assertEquals(VideoEditSelections.whole(8_000L), state.selection)
        assertNull(state.selectedSegmentIndex)
        assertFalse(state.canUndo)
    }

    @Test
    fun applyAutomaticSelectionAddsHistoryAndCanUndo() {
        val state = VideoEditorState(VideoEditSelections.whole(12_000L))

        val autoSelection = VideoEditSelections.fromSourceSplitTimes(
            durationMs = 12_000L,
            splitTimesMs = listOf(4_000L, 8_000L)
        )

        state.applyAutomaticSelection(autoSelection, preferredEditedPositionMs = 0L)

        assertEquals(autoSelection, state.selection)
        assertEquals(0, state.selectedSegmentIndex)
        assertTrue(state.canUndo)

        // Undo should restore the original whole selection
        assertTrue(state.undo())
        assertEquals(VideoEditSelections.whole(12_000L), state.selection)
        assertNull(state.selectedSegmentIndex)
        assertFalse(state.canUndo)
    }

    @Test
    fun applyAutomaticSelectionWithZeroSegmentsPreservesUndo() {
        val state = VideoEditorState(VideoEditSelections.whole(10_000L))

        val autoSelection = VideoEditSelections.fromSourceSplitTimes(
            durationMs = 10_000L,
            splitTimesMs = listOf(500L) // too close to start, returns whole
        )

        state.applyAutomaticSelection(autoSelection, preferredEditedPositionMs = 0L)

        assertEquals(VideoEditSelections.whole(10_000L), state.selection)
        assertTrue(state.canUndo)
    }
}
