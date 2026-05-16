package com.potato.liftinsight.screen

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
import com.potato.liftinsight.R
import com.potato.liftinsight.widget.MetricCardChoice
import com.potato.liftinsight.widget.MetricCardInputType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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

internal enum class BodyMetricSection {
    SUMMARY,
    STRENGTH
}

internal data class BodyMetricState(
    val section: BodyMetricSection,
    val titleResId: Int,
    val acceptType: MetricCardInputType = MetricCardInputType.Text,
    val unitResId: Int? = null,
    val defaultValue: String = "-",
    val value: String = defaultValue,
    val updatedAt: String = "-",
    val id: Int = titleResId
)

internal fun defaultBodyMetrics(): List<BodyMetricState> = listOf(
    BodyMetricState(
        section = BodyMetricSection.SUMMARY,
        titleResId = R.string.body_age,
        acceptType = MetricCardInputType.Integer,
        unitResId = R.string.body_unit_year,
        defaultValue = "18"
    ),
    BodyMetricState(
        section = BodyMetricSection.SUMMARY,
        titleResId = R.string.body_sex,
        acceptType = MetricCardInputType.SingleChoice(
            choices = listOf(
                MetricCardChoice("male", R.string.body_sex_male),
                MetricCardChoice("female", R.string.body_sex_female)
            )
        ),
        defaultValue = "male"
    ),
    BodyMetricState(
        section = BodyMetricSection.SUMMARY,
        titleResId = R.string.body_weight,
        acceptType = MetricCardInputType.Integer,
        unitResId = R.string.body_unit_kg,
        defaultValue = "0"
    ),
    BodyMetricState(
        section = BodyMetricSection.SUMMARY,
        titleResId = R.string.body_height,
        acceptType = MetricCardInputType.Integer,
        unitResId = R.string.body_unit_cm,
        defaultValue = "0"
    ),
    BodyMetricState(
        section = BodyMetricSection.SUMMARY,
        titleResId = R.string.body_maximum_power,
        acceptType = MetricCardInputType.Integer,
        unitResId = R.string.body_unit_w,
        defaultValue = "0"
    ),
    BodyMetricState(
        section = BodyMetricSection.SUMMARY,
        titleResId = R.string.body_strength_index,
        acceptType = MetricCardInputType.Integer,
        defaultValue = "0"
    ),
    BodyMetricState(
        section = BodyMetricSection.STRENGTH,
        titleResId = R.string.body_maximum_cj,
        acceptType = MetricCardInputType.Integer,
        unitResId = R.string.body_unit_kg,
        defaultValue = "0"
    ),
    BodyMetricState(
        section = BodyMetricSection.STRENGTH,
        titleResId = R.string.body_maximum_snatch,
        acceptType = MetricCardInputType.Integer,
        unitResId = R.string.body_unit_kg,
        defaultValue = "0"
    ),
    BodyMetricState(
        section = BodyMetricSection.STRENGTH,
        titleResId = R.string.body_back_squat,
        acceptType = MetricCardInputType.Integer,
        unitResId = R.string.body_unit_kg,
        defaultValue = "0"
    ),
    BodyMetricState(
        section = BodyMetricSection.STRENGTH,
        titleResId = R.string.body_front_squat,
        acceptType = MetricCardInputType.Integer,
        unitResId = R.string.body_unit_kg,
        defaultValue = "0"
    ),
    BodyMetricState(
        section = BodyMetricSection.STRENGTH,
        titleResId = R.string.body_deadlift,
        acceptType = MetricCardInputType.Integer,
        unitResId = R.string.body_unit_kg,
        defaultValue = "0"
    )
)

internal fun updateBodyMetric(
    metrics: List<BodyMetricState>,
    metricId: Int,
    newValue: String
) : List<BodyMetricState> {
    val metricIndex = metrics.indexOfFirst { it.id == metricId }

    if (metricIndex < 0) {
        return metrics
    }

    return metrics.toMutableList().apply {
        this[metricIndex] = this[metricIndex].copy(
            value = newValue,
            updatedAt = timestampNow()
        )
    }
}

private fun timestampNow(): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    return LocalDateTime.now().format(formatter)
}
