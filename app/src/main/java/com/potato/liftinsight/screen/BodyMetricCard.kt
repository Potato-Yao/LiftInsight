package com.potato.liftinsight.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.potato.liftinsight.R
import com.potato.liftinsight.widget.MetricCard
import com.potato.liftinsight.widget.MetricCardInputDialog
import com.potato.liftinsight.widget.MetricCardInputType
import com.potato.liftinsight.widget.MetricCardOption

@Composable
internal fun BodyMetricCard(
    title: String,
    value: String,
    updatedAt: String,
    defaultValue: String,
    unitLabel: String?,
    inputType: MetricCardInputType,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    MetricCard(
        title = title,
        modifier = modifier,
        options = listOf(
            MetricCardOption(
                id = "clear",
                label = stringResource(R.string.body_clear_value_option)
            )
        ),
        popupPanel = { onDismiss ->
            MetricCardInputDialog(
                title = title,
                initialValue = value,
                defaultValue = defaultValue,
                unitLabel = unitLabel,
                inputType = inputType,
                onDismiss = onDismiss,
                onConfirm = onValueChange
            )
        },
        onOptionSelected = { option ->
            if (option.id == "clear") {
                onValueChange(defaultValue)
            }
        }
    ) {
        val displayValue = when (inputType) {
            is MetricCardInputType.SingleChoice -> {
                val selectedChoice = inputType.choices.firstOrNull { it.id == value }
                if (selectedChoice == null) {
                    value
                } else {
                    stringResource(selectedChoice.labelResId)
                }
            }

            else -> value
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val shownValue = displayValue.ifBlank { defaultValue }

            if (!unitLabel.isNullOrBlank() && inputType !is MetricCardInputType.SingleChoice) {
                Text(
                    text = buildAnnotatedString {
                        append(shownValue)
                        append(" ")
                        withStyle(
                            style = SpanStyle(
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            append(unitLabel)
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = shownValue,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = stringResource(R.string.body_updated_at_value, updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
