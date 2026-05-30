package com.potato.liftinsight.motion.model

/**
 * Tracks all gesture-driven UI state for [MotionVideoPlayer].
 *
 * Fields are independent so that seeking, speed-boosting, and icon
 * display can overlap without stepping on each other.
 */
data class MotionVideoGestureState(

    // ── Swipe seek ──────────────────────────────────────────────

    /** true while the user is actively dragging the horizontal seek gesture */
    val isSeeking: Boolean = false,

    /** 0f..1f — computed seek progress during the drag */
    val seekProgress: Float = 0f,

    /** formatted seek-time string shown in the translucent overlay, e.g. "01:23" */
    val seekTimeLabel: String = "",

    // ── Play / pause icon overlay ───────────────────────────────

    /** true while the play/pause icon overlay is visible after a tap */
    val showPlayPauseIcon: Boolean = false,

    // ── Long-press speed override ───────────────────────────────

    /** true while the user is long-pressing a speed-zone (top or bottom 40 %) */
    val isSpeedBoosting: Boolean = false,

    /** label shown in the speed badge, e.g. "0.5×" or "1.5×" */
    val boostSpeedLabel: String = "",

    /** Y offset (px) of the long-press touch for badge positioning */
    val boostOffsetY: Float = 0f,

    // ── Controls visibility ─────────────────────────────────────

    /** true when the controls overlay (close button) should be visible */
    val showControls: Boolean = true
)
