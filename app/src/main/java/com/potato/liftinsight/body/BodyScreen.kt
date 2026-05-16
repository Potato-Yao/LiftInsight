package com.potato.liftinsight.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.potato.liftinsight.body.model.BodyMetricSection
import com.potato.liftinsight.body.model.BodyMetricState

@Composable
internal fun BodyScreen(
    metrics: List<BodyMetricState>,
    onMetricValueChange: (metricId: Int, newValue: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val summaryMetrics = metrics.filter { it.section == BodyMetricSection.SUMMARY }
    val strengthMetrics = metrics.filter { it.section == BodyMetricSection.STRENGTH }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BodyMetricGrid(
            metrics = summaryMetrics,
            onMetricValueChange = onMetricValueChange
        )

        HorizontalDivider()

        BodyMetricGrid(
            metrics = strengthMetrics,
            onMetricValueChange = onMetricValueChange
        )
    }
}

@Composable
private fun BodyMetricGrid(
    metrics: List<BodyMetricState>,
    onMetricValueChange: (metricId: Int, newValue: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        metrics.chunked(2).forEach { rowMetrics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                rowMetrics.forEach { metric ->
                    BodyMetricCard(
                        title = stringResource(metric.titleResId),
                        value = metric.value,
                        updatedAt = metric.updatedAt,
                        defaultValue = metric.defaultValue,
                        unitLabel = metric.unitResId?.let { stringResource(it) },
                        inputType = metric.acceptType,
                        modifier = Modifier.weight(1f),
                        onValueChange = { onMetricValueChange(metric.id, it) }
                    )
                }

                if (rowMetrics.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
