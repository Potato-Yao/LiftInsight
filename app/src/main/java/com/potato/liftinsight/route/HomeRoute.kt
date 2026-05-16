package com.potato.liftinsight.route

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.potato.liftinsight.bar.LiftInsightBottomBar
import com.potato.liftinsight.screen.BodyScreen
import com.potato.liftinsight.screen.HomeScreen
import com.potato.liftinsight.screen.defaultBodyMetrics
import com.potato.liftinsight.screen.updateBodyMetric
import com.potato.liftinsight.ui.theme.LiftInsightTheme

@Composable
fun LiftInsightHomeRoute() {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val bodyMetrics = remember { mutableStateListOf(*defaultBodyMetrics().toTypedArray()) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            LiftInsightBottomBar(
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it }
            )
        }
    ) { innerPadding ->
        when (selectedTabIndex) {
            2 -> BodyScreen(
                metrics = bodyMetrics,
                onMetricValueChange = { metricId, newValue ->
                    bodyMetrics.apply {
                        clear()
                        addAll(
                            updateBodyMetric(
                                metrics = this,
                                metricId = metricId,
                                newValue = newValue
                            )
                        )
                    }
                },
                modifier = Modifier.padding(innerPadding)
            )
            else -> HomeScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LiftInsightHomeRoutePreview() {
    LiftInsightTheme {
        LiftInsightHomeRoute()
    }
}
