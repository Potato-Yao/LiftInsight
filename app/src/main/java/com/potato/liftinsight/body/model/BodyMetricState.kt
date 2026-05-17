package com.potato.liftinsight.body.model

import com.potato.liftinsight.R
import com.potato.liftinsight.common.MetricCardChoice
import com.potato.liftinsight.common.MetricCardInputType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BodyMetricSection {
    SUMMARY,
    STRENGTH
}

data class BodyMetricState(
    val section: BodyMetricSection,
    val titleResId: Int,
    val acceptType: MetricCardInputType = MetricCardInputType.Text,
    val unitResId: Int? = null,
    val defaultValue: String = "-",
    val value: String = defaultValue,
    val updatedAt: String = "-",
    val id: Int = titleResId
)

fun defaultBodyMetrics(): List<BodyMetricState> = listOf(
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

fun updateBodyMetric(
    metrics: List<BodyMetricState>,
    metricId: Int,
    newValue: String
): List<BodyMetricState> {
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
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date())
}
