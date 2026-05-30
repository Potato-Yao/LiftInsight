package com.potato.liftinsight.body.route

sealed interface BodyRoute {
    data object Overview : BodyRoute
    data object Body : BodyRoute
    data object Training : BodyRoute
}

fun bodyRouteDepth(route: BodyRoute): Int = when (route) {
    BodyRoute.Overview -> 0
    BodyRoute.Body -> 1
    BodyRoute.Training -> 1
}
