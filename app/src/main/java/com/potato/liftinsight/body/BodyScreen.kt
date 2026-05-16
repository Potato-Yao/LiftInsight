package com.potato.liftinsight.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.potato.liftinsight.R
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
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.nav_body),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        BodyMetricSection(
            title = stringResource(R.string.body_section_profile),
            metrics = summaryMetrics,
            onMetricValueChange = onMetricValueChange
        )

        BodyMetricSection(
            title = stringResource(R.string.body_section_best_lifts),
            metrics = strengthMetrics,
            onMetricValueChange = onMetricValueChange
        )
    }
}

@Composable
private fun BodyMetricSection(
    title: String,
    metrics: List<BodyMetricState>,
    onMetricValueChange: (metricId: Int, newValue: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        metrics.forEach { metric ->
            BodyMetricCard(
                title = stringResource(metric.titleResId),
                value = metric.value,
                updatedAt = metric.updatedAt,
                defaultValue = metric.defaultValue,
                unitLabel = metric.unitResId?.let { stringResource(it) },
                inputType = metric.acceptType,
                modifier = Modifier.fillMaxWidth(),
                onValueChange = { onMetricValueChange(metric.id, it) }
            )
        }
    }
}
