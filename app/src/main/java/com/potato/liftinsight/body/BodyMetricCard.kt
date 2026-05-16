package com.potato.liftinsight.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.potato.liftinsight.R
import com.potato.liftinsight.common.MetricCard
import com.potato.liftinsight.common.MetricCardInputDialog
import com.potato.liftinsight.common.MetricCardInputType
import com.potato.liftinsight.common.MetricCardOption

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
    var showEditor by remember(title) { mutableStateOf(false) }
    val isEdited = value != defaultValue

    MetricCard(
        title = title,
        subtitle = stringResource(R.string.body_updated_at_value, updatedAt),
        modifier = modifier,
        highlighted = isEdited,
        options = listOf(
            MetricCardOption(
                id = "clear",
                label = stringResource(R.string.body_clear_value_option)
            )
        ),
        onClick = { showEditor = true },
        trailingContent = {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
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

        val shownValue = displayValue.ifBlank { defaultValue }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!unitLabel.isNullOrBlank() && inputType !is MetricCardInputType.SingleChoice) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = shownValue,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = unitLabel,
                        modifier = Modifier.padding(bottom = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = shownValue,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    if (showEditor) {
        MetricCardInputDialog(
            title = title,
            initialValue = value,
            defaultValue = defaultValue,
            unitLabel = unitLabel,
            inputType = inputType,
            onDismiss = { showEditor = false },
            onConfirm = onValueChange
        )
    }
}

