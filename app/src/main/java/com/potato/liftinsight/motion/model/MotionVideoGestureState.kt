package com.potato.liftinsight.motion.model

data class MotionVideoGestureState(
    val isSeeking: Boolean = false,
    val seekProgress: Float = 0f,
    val seekTimeLabel: String = "",
    val showPlayPauseIcon: Boolean = false,
    val isSpeedBoosting: Boolean = false,
    val boostSpeedLabel: String = "",
    val boostOffsetY: Float = 0f,
    val showControls: Boolean = true
)
