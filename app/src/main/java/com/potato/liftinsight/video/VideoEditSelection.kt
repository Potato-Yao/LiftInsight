package com.potato.liftinsight.video

internal data class VideoEditRange(
    val startMs: Long,
    val endMs: Long
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)
}

internal data class VideoEditSelection(
    val keptRanges: List<VideoEditRange>
) {
    val durationMs: Long
        get() = keptRanges.sumOf { range -> range.durationMs }
}

internal data class VideoTimelineSegment(
    val index: Int,
    val sourceRange: VideoEditRange,
    val editedStartMs: Long,
    val editedEndMs: Long
) {
    val durationMs: Long
        get() = sourceRange.durationMs
}

internal object VideoEditSelections {
    const val MIN_DURATION_MS = 1_000L

    fun whole(durationMs: Long): VideoEditSelection {
        return VideoEditSelection(
            keptRanges = listOf(
                VideoEditRange(
                    startMs = 0L,
                    endMs = durationMs.coerceAtLeast(0L)
                )
            )
        )
    }

    fun isWhole(selection: VideoEditSelection, durationMs: Long): Boolean {
        val singleRange = selection.keptRanges.singleOrNull() ?: return false

        return singleRange.startMs == 0L && singleRange.endMs == durationMs.coerceAtLeast(0L)
    }

    fun timelineSegments(selection: VideoEditSelection): List<VideoTimelineSegment> {
        var editedOffsetMs = 0L

        return selection.keptRanges.mapIndexed { index, range ->
            val segment = VideoTimelineSegment(
                index = index,
                sourceRange = range,
                editedStartMs = editedOffsetMs,
                editedEndMs = editedOffsetMs + range.durationMs
            )

            editedOffsetMs = segment.editedEndMs
            segment
        }
    }

    fun segmentIndexAtEditedPosition(selection: VideoEditSelection, editedPositionMs: Long): Int? {
        val boundedPositionMs = editedPositionMs.coerceAtLeast(0L)

        return timelineSegments(selection)
            .firstOrNull { segment ->
                boundedPositionMs >= segment.editedStartMs &&
                    boundedPositionMs <= segment.editedEndMs
            }
            ?.index
            ?: timelineSegments(selection).lastOrNull()?.index
    }

    fun sourcePositionAtEditedPosition(selection: VideoEditSelection, editedPositionMs: Long): Long {
        val boundedPositionMs = editedPositionMs.coerceAtLeast(0L)
        val segment = timelineSegments(selection)
            .firstOrNull { timelineSegment ->
                boundedPositionMs >= timelineSegment.editedStartMs &&
                    boundedPositionMs <= timelineSegment.editedEndMs
            }
            ?: return selection.keptRanges.lastOrNull()?.endMs ?: 0L

        val offsetInsideSegmentMs = (boundedPositionMs - segment.editedStartMs)
            .coerceIn(0L, segment.durationMs)

        return segment.sourceRange.startMs + offsetInsideSegmentMs
    }

    fun canSplitAtEditedPosition(selection: VideoEditSelection, editedPositionMs: Long): Boolean {
        val segmentIndex = segmentIndexAtEditedPosition(selection, editedPositionMs) ?: return false
        val segment = selection.keptRanges.getOrNull(segmentIndex) ?: return false
        val splitPositionMs = sourcePositionAtEditedPosition(selection, editedPositionMs)

        return splitPositionMs - segment.startMs >= MIN_DURATION_MS &&
            segment.endMs - splitPositionMs >= MIN_DURATION_MS
    }

    fun splitAtEditedPosition(selection: VideoEditSelection, editedPositionMs: Long): VideoEditSelection {
        val segmentIndex = segmentIndexAtEditedPosition(selection, editedPositionMs) ?: return selection
        val segment = selection.keptRanges.getOrNull(segmentIndex) ?: return selection
        val splitPositionMs = sourcePositionAtEditedPosition(selection, editedPositionMs)

        if (splitPositionMs - segment.startMs < MIN_DURATION_MS) {
            return selection
        }

        if (segment.endMs - splitPositionMs < MIN_DURATION_MS) {
            return selection
        }

        val updatedRanges = selection.keptRanges.toMutableList()
        updatedRanges.removeAt(segmentIndex)
        updatedRanges.add(
            segmentIndex,
            VideoEditRange(startMs = segment.startMs, endMs = splitPositionMs)
        )
        updatedRanges.add(
            segmentIndex + 1,
            VideoEditRange(startMs = splitPositionMs, endMs = segment.endMs)
        )

        return VideoEditSelection(keptRanges = updatedRanges)
    }

    fun deleteSegment(selection: VideoEditSelection, segmentIndex: Int): VideoEditSelection {
        if (segmentIndex !in selection.keptRanges.indices) {
            return selection
        }

        if (selection.keptRanges.size <= 1) {
            return selection
        }

        val updatedRanges = selection.keptRanges.toMutableList()
        updatedRanges.removeAt(segmentIndex)

        if (updatedRanges.sumOf { range -> range.durationMs } < MIN_DURATION_MS) {
            return selection
        }

        return VideoEditSelection(keptRanges = updatedRanges)
    }
}
