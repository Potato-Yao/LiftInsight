package com.potato.liftinsight.motion.controller

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.potato.liftinsight.R
import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.motion.formatTime
import com.potato.liftinsight.motion.model.MotionVideoGestureState

data class MotionVideoPlayerState(
    val isPlaying: Boolean = true,
    val progress: Float = 0f,
    val duration: Long = 0L,
    val currentPosition: Long = 0L,
    val basePlaybackSpeed: Float = 1f,
    val longPressSpeed: Float? = null,
    val lastTapUptimeMs: Long = 0L,
    val thumbDragProgress: Float = 0f,
    val isDraggingThumb: Boolean = false,
    val barWidthPx: Float = 0f,
    val gestureState: MotionVideoGestureState = MotionVideoGestureState()
) {
    val effectiveSpeed: Float
        get() = longPressSpeed ?: basePlaybackSpeed
}

class MotionVideoPlayerController(
    context: Context,
    videoUri: Uri,
    private val logger: AppLogger = AndroidAppLogger
) {
    val player = ExoPlayer.Builder(context).build().apply {
        playWhenReady = true
        setMediaItem(MediaItem.fromUri(videoUri))
        prepare()
    }

    var state by mutableStateOf(MotionVideoPlayerState())
        private set

    init {
        logger.info(TAG, "Prepared motion video player: videoUri=$videoUri")
    }

    fun syncPlayerState() {
        player.playWhenReady = state.isPlaying
        player.setPlaybackSpeed(state.effectiveSpeed)
    }

    fun updateFromPlayer() {
        val stateOk = player.playbackState == Player.STATE_READY ||
            player.playbackState == Player.STATE_BUFFERING

        if (!stateOk || state.isDraggingThumb || state.gestureState.isSeeking || state.longPressSpeed != null) {
            return
        }

        val duration = player.duration.coerceAtLeast(0L)
        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        val progress = if (duration > 0L) currentPosition.toFloat() / duration.toFloat() else 0f

        state = state.copy(
            duration = duration,
            currentPosition = currentPosition,
            progress = progress,
            isPlaying = player.playWhenReady && player.playbackState == Player.STATE_READY
        )
    }

    fun showControls() {
        state = state.copy(gestureState = state.gestureState.copy(showControls = true))
    }

    fun hideControls() {
        state = state.copy(gestureState = state.gestureState.copy(showControls = false))
    }

    fun hidePlayPauseIcon() {
        state = state.copy(gestureState = state.gestureState.copy(showPlayPauseIcon = false))
    }

    fun setBasePlaybackSpeed(speed: Float) {
        logger.debug(TAG, "Updating playback speed: speed=$speed")
        state = state.copy(basePlaybackSpeed = speed)
        syncPlayerState()
        showControls()
    }

    fun setBarWidth(width: Float) {
        state = state.copy(barWidthPx = width)
    }

    fun beginSwipeSeek() {
        logger.debug(TAG, "Beginning swipe seek")
        player.playWhenReady = false
        state = state.copy(
            gestureState = state.gestureState.copy(
                isSeeking = true,
                seekProgress = state.progress
            )
        )
    }

    fun updateSwipeSeek(deltaProgress: Float) {
        val seekProgress = (state.gestureState.seekProgress + deltaProgress).coerceIn(0f, 1f)
        val seekMs = if (state.duration > 0L) {
            (seekProgress * state.duration).toLong().coerceIn(0L, state.duration)
        } else {
            0L
        }

        if (state.duration > 0L) {
            player.seekTo(seekMs)
        }

        state = state.copy(
            gestureState = state.gestureState.copy(
                seekProgress = seekProgress,
                seekTimeLabel = formatTime(seekMs)
            )
        )
    }

    fun endSwipeSeek(resumePlayback: Boolean) {
        val seekTo = if (state.duration > 0L) {
            (state.gestureState.seekProgress * state.duration).toLong().coerceIn(0L, state.duration)
        } else {
            0L
        }

        if (state.duration > 0L) {
            player.seekTo(seekTo)
        }

        logger.debug(TAG, "Ending swipe seek: seekTo=$seekTo, resumePlayback=$resumePlayback")
        player.playWhenReady = resumePlayback
        state = state.copy(
            currentPosition = seekTo,
            gestureState = state.gestureState.copy(
                isSeeking = false,
                seekTimeLabel = ""
            )
        )
    }

    fun beginSpeedBoost(speed: Float, touchY: Float, context: Context) {
        logger.debug(TAG, "Beginning speed boost: speed=$speed")
        state = state.copy(
            longPressSpeed = speed,
            gestureState = state.gestureState.copy(
                isSpeedBoosting = true,
                boostSpeedLabel = speedLabel(context, speed),
                boostOffsetY = touchY
            )
        )
        syncPlayerState()
    }

    fun updateSpeedBoostOffset(touchY: Float) {
        state = state.copy(gestureState = state.gestureState.copy(boostOffsetY = touchY))
    }

    fun endSpeedBoost() {
        logger.debug(TAG, "Ending speed boost")
        state = state.copy(
            longPressSpeed = null,
            gestureState = state.gestureState.copy(isSpeedBoosting = false)
        )
        syncPlayerState()
    }

    fun registerTap(uptimeMs: Long): Boolean {
        if (state.lastTapUptimeMs == 0L || uptimeMs - state.lastTapUptimeMs >= DOUBLE_TAP_WINDOW_MS) {
            logger.trace(TAG, "Registered first tap: uptimeMs=$uptimeMs")
            state = state.copy(lastTapUptimeMs = uptimeMs)
            return false
        }

        val nextPlaying = !player.playWhenReady
        logger.debug(TAG, "Double tap toggled playback: isPlaying=$nextPlaying")
        player.playWhenReady = nextPlaying
        state = state.copy(
            isPlaying = nextPlaying,
            lastTapUptimeMs = 0L,
            gestureState = state.gestureState.copy(showPlayPauseIcon = true)
        )
        return true
    }

    fun beginThumbDrag() {
        logger.debug(TAG, "Beginning thumb drag")
        player.playWhenReady = false
        state = state.copy(
            isDraggingThumb = true,
            thumbDragProgress = state.progress
        )
    }

    fun updateThumbDrag(deltaProgress: Float) {
        if (state.duration <= 0L) {
            return
        }

        val thumbDragProgress = (state.thumbDragProgress + deltaProgress).coerceIn(0f, 1f)
        val seekMs = (thumbDragProgress * state.duration).toLong().coerceIn(0L, state.duration)

        player.seekTo(seekMs)
        state = state.copy(
            thumbDragProgress = thumbDragProgress,
            currentPosition = seekMs
        )
    }

    fun endThumbDrag(resumePlayback: Boolean) {
        val seekTo = if (state.duration > 0L) {
            (state.thumbDragProgress * state.duration).toLong().coerceIn(0L, state.duration)
        } else {
            0L
        }
        val progress = if (state.duration > 0L) seekTo.toFloat() / state.duration.toFloat() else 0f

        if (state.duration > 0L) {
            player.seekTo(seekTo)
        }

        logger.debug(TAG, "Ending thumb drag: seekTo=$seekTo, resumePlayback=$resumePlayback")
        player.playWhenReady = resumePlayback
        state = state.copy(
            isDraggingThumb = false,
            currentPosition = seekTo,
            progress = progress
        )
        showControls()
    }

    fun cancelThumbDrag(resumePlayback: Boolean) {
        logger.debug(TAG, "Cancelling thumb drag: resumePlayback=$resumePlayback")
        player.playWhenReady = resumePlayback
        state = state.copy(isDraggingThumb = false)
        showControls()
    }

    fun release() {
        logger.info(TAG, "Releasing motion video player")
        player.release()
    }

    companion object {
        const val DOUBLE_TAP_WINDOW_MS = 300L
        private const val TAG = "MotionVideoPlayer"
    }
}

private fun speedLabel(context: Context, speed: Float): String {
    val labelRes = when (speed) {
        0.1f -> R.string.playback_speed_label_01
        0.25f -> R.string.playback_speed_label_025
        0.5f -> R.string.playback_speed_label_05
        1f -> R.string.playback_speed_label_1
        1.25f -> R.string.playback_speed_label_125
        1.5f -> R.string.playback_speed_label_15
        else -> R.string.playback_speed_label_1
    }

    return context.getString(labelRes)
}
