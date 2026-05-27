package com.potato.liftinsight.motion.route

sealed interface MotionRoute {
    data object Library : MotionRoute

    data object Editor : MotionRoute
}
