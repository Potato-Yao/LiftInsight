package com.potato.liftinsight.body.route

sealed interface BodyRoute {
    data object Overview : BodyRoute
}
