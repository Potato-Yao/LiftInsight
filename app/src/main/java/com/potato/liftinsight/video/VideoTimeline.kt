package com.potato.liftinsight.video

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

/**
 * Helper that maps between ExoPlayer playback time and the normalized
 * analysis-data timeline used for pose frames, angles, and barbell frames.
 *
 * Processing stores analysis data with timestamps normalized so that the
 * first extractable video sample is at 0 ms.  When a video's first sample
 * is not at the start of the media timeline (common with variable frame-rate
 * videos or those that begin with a b-frame / seek-intro), the analysis
 * overlay would otherwise appear out of sync with the visible video.
 *
 * Usage:
 *   val firstSampleMs = VideoTimeline.readFirstVideoSampleTimeMs(videoFile)
 *   val analysisPos = VideoTimeline.analysisPositionForPlayback(playerPosMs, firstSampleMs)
 *   if (analysisPos != null) {
 *       // use analysisPos for pose/angle/barbell lookups
 *   }
 */
object VideoTimeline {

    /**
     * Reads the presentation timestamp (in milliseconds) of the first video
     * sample in the given file using [MediaExtractor].
     *
     * Returns 0 if the file is absent, has no video track, or the extractor
     * cannot read the first sample.  A return value of 0 means no offset is
     * needed.
     */
    fun readFirstVideoSampleTimeMs(videoFile: File): Long {
        if (!videoFile.exists()) return 0L

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(videoFile.absolutePath)
            val trackIndex = selectFirstVideoTrack(extractor) ?: return 0L
            extractor.selectTrack(trackIndex)
            val sampleTimeUs = extractor.sampleTime
            return if (sampleTimeUs >= 0L) sampleTimeUs / 1000L else 0L
        } catch (_: Exception) {
            return 0L
        } finally {
            extractor.release()
        }
    }

    /**
     * Maps a raw ExoPlayer playback position (ms) into the analysis-data
     * timeline by subtracting the first-sample offset.
     *
     * Returns `null` when the playback position is still before the first
     * sample — callers should hide the overlay until the video catches up.
     */
    fun analysisPositionForPlayback(playbackPositionMs: Long, firstSampleOffsetMs: Long): Long? {
        val mapped = playbackPositionMs - firstSampleOffsetMs
        return if (mapped < 0L) null else mapped
    }

    /**
     * Maps the raw video duration (ms) into the analysis-data timeline
     * duration.  Clamped to at least 0 so the analysis plot never gets
     * a negative width.
     */
    fun analysisDurationForPlayback(playbackDurationMs: Long, firstSampleOffsetMs: Long): Long {
        return (playbackDurationMs - firstSampleOffsetMs).coerceAtLeast(0L)
    }

    private fun selectFirstVideoTrack(extractor: MediaExtractor): Int? {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) return index
        }
        return null
    }
}
