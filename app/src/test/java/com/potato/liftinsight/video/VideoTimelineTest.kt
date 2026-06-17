package com.potato.liftinsight.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoTimelineTest {

    // -------------------------------------------------------------------------
    // analysisPositionForPlayback
    // -------------------------------------------------------------------------

    @Test
    fun `analysisPositionForPlayback with zero offset returns identity`() {
        val result = VideoTimeline.analysisPositionForPlayback(
            playbackPositionMs = 3500L,
            firstSampleOffsetMs = 0L
        )
        assertEquals(3500L, result)
    }

    @Test
    fun `analysisPositionForPlayback subtracts offset when playback is ahead`() {
        val result = VideoTimeline.analysisPositionForPlayback(
            playbackPositionMs = 3500L,
            firstSampleOffsetMs = 1000L
        )
        assertEquals(2500L, result)
    }

    @Test
    fun `analysisPositionForPlayback returns null when playback is before offset`() {
        val result = VideoTimeline.analysisPositionForPlayback(
            playbackPositionMs = 500L,
            firstSampleOffsetMs = 1000L
        )
        assertNull(result)
    }

    @Test
    fun `analysisPositionForPlayback returns 0 when playback exactly equals offset`() {
        val result = VideoTimeline.analysisPositionForPlayback(
            playbackPositionMs = 1000L,
            firstSampleOffsetMs = 1000L
        )
        assertEquals(0L, result)
    }

    // -------------------------------------------------------------------------
    // analysisDurationForPlayback
    // -------------------------------------------------------------------------

    @Test
    fun `analysisDurationForPlayback with zero offset returns identity`() {
        val result = VideoTimeline.analysisDurationForPlayback(
            playbackDurationMs = 10000L,
            firstSampleOffsetMs = 0L
        )
        assertEquals(10000L, result)
    }

    @Test
    fun `analysisDurationForPlayback subtracts offset`() {
        val result = VideoTimeline.analysisDurationForPlayback(
            playbackDurationMs = 10000L,
            firstSampleOffsetMs = 1000L
        )
        assertEquals(9000L, result)
    }

    @Test
    fun `analysisDurationForPlayback clamps to zero when offset exceeds duration`() {
        val result = VideoTimeline.analysisDurationForPlayback(
            playbackDurationMs = 500L,
            firstSampleOffsetMs = 1000L
        )
        assertEquals(0L, result)
    }

    // -------------------------------------------------------------------------
    // Combined mapping scenarios
    // -------------------------------------------------------------------------

    @Test
    fun `analysis playback position and duration form consistent window`() {
        val offset = 1000L
        val playbackDuration = 11000L

        val analysisPos = VideoTimeline.analysisPositionForPlayback(2500L, offset)
        val analysisDur = VideoTimeline.analysisDurationForPlayback(playbackDuration, offset)

        assertEquals(1500L, analysisPos)
        assertEquals(10000L, analysisDur)
    }

    @Test
    fun `analysis position null for all playback before first sample`() {
        val offset = 2000L
        assertNull(VideoTimeline.analysisPositionForPlayback(0L, offset))
        assertNull(VideoTimeline.analysisPositionForPlayback(1999L, offset))
        assertEquals(0L, VideoTimeline.analysisPositionForPlayback(2000L, offset))
    }
}
