package com.potato.liftinsight.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

object LiftInsightMotion {
    const val ShortDuration = 160
    const val MediumDuration = 260
    const val LongDuration = 420

    val EnterEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val ExitEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
}
