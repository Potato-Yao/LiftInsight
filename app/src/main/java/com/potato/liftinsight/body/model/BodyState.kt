package com.potato.liftinsight.body.model

import com.potato.liftinsight.body.model.BodyMetricState
import com.potato.liftinsight.body.route.BodyRoute

data class BodyState(
    val bodyMetrics: List<BodyMetricState>,
    val bodyRoute: BodyRoute = BodyRoute.Overview
)
