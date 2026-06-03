package com.potato.liftinsight.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoEditSelectionsTest {
    @Test
    fun wholeSelectionCoversEntireVideo() {
        val selection = VideoEditSelections.whole(12_000L)

        assertEquals(1, selection.keptRanges.size)
        assertEquals(0L, selection.keptRanges[0].startMs)
        assertEquals(12_000L, selection.keptRanges[0].endMs)
        assertTrue(VideoEditSelections.isWhole(selection, 12_000L))
    }

    @Test
    fun splitAtEditedPositionSplitsContainingSegment() {
        val original = VideoEditSelections.whole(12_000L)

        val updated = VideoEditSelections.splitAtEditedPosition(
            selection = original,
            editedPositionMs = 5_000L
        )

        assertEquals(2, updated.keptRanges.size)
        assertEquals(0L, updated.keptRanges[0].startMs)
        assertEquals(5_000L, updated.keptRanges[0].endMs)
        assertEquals(5_000L, updated.keptRanges[1].startMs)
        assertEquals(12_000L, updated.keptRanges[1].endMs)
        assertFalse(VideoEditSelections.isWhole(updated, 12_000L))
    }

    @Test
    fun splitNearSegmentEdgeIsIgnored() {
        val original = VideoEditSelections.whole(12_000L)

        val updated = VideoEditSelections.splitAtEditedPosition(
            selection = original,
            editedPositionMs = 200L
        )

        assertEquals(original, updated)
    }

    @Test
    fun deleteSegmentRemovesChosenPart() {
        val splitSelection = VideoEditSelection(
            keptRanges = listOf(
                VideoEditRange(0L, 3_000L),
                VideoEditRange(3_000L, 7_000L),
                VideoEditRange(7_000L, 12_000L)
            )
        )

        val updated = VideoEditSelections.deleteSegment(
            selection = splitSelection,
            segmentIndex = 1
        )

        assertEquals(2, updated.keptRanges.size)
        assertEquals(0L, updated.keptRanges[0].startMs)
        assertEquals(3_000L, updated.keptRanges[0].endMs)
        assertEquals(7_000L, updated.keptRanges[1].startMs)
        assertEquals(12_000L, updated.keptRanges[1].endMs)
    }

    @Test
    fun segmentLookupUsesAdjacentSplitPoints() {
        val selection = VideoEditSelection(
            keptRanges = listOf(
                VideoEditRange(0L, 3_000L),
                VideoEditRange(3_000L, 7_000L),
                VideoEditRange(7_000L, 12_000L)
            )
        )

        assertEquals(0, VideoEditSelections.segmentIndexAtEditedPosition(selection, 0L))
        assertEquals(0, VideoEditSelections.segmentIndexAtEditedPosition(selection, 2_999L))
        assertEquals(1, VideoEditSelections.segmentIndexAtEditedPosition(selection, 3_000L))
        assertEquals(1, VideoEditSelections.segmentIndexAtEditedPosition(selection, 6_999L))
        assertEquals(2, VideoEditSelections.segmentIndexAtEditedPosition(selection, 7_000L))
        assertEquals(2, VideoEditSelections.segmentIndexAtEditedPosition(selection, 12_000L))
    }

    @Test
    fun splitPointsIncludeStartAndEditedBoundaries() {
        val selection = VideoEditSelection(
            keptRanges = listOf(
                VideoEditRange(0L, 3_000L),
                VideoEditRange(7_000L, 12_000L)
            )
        )

        assertEquals(listOf(0L, 3_000L, 8_000L), VideoEditSelections.splitPoints(selection))
    }

    @Test
    fun sourcePositionMapsAcrossSplitSegments() {
        val selection = VideoEditSelection(
            keptRanges = listOf(
                VideoEditRange(0L, 3_000L),
                VideoEditRange(7_000L, 12_000L)
            )
        )

        assertEquals(1_000L, VideoEditSelections.sourcePositionAtEditedPosition(selection, 1_000L))
        assertEquals(9_000L, VideoEditSelections.sourcePositionAtEditedPosition(selection, 5_000L))
    }
}
