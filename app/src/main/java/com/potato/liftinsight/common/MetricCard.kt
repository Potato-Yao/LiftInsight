package com.potato.liftinsight.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.potato.liftinsight.R

data class MetricCardOption(
    val id: String,
    val label: String
)

data class MetricCardChoice(
    val id: String,
    val labelResId: Int
)

sealed interface MetricCardInputType {
    data object Text : MetricCardInputType

    data object Integer : MetricCardInputType

    data class SingleChoice(
        val choices: List<MetricCardChoice>
    ) : MetricCardInputType
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetricCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    highlighted: Boolean = false,
    options: List<MetricCardOption> = emptyList(),
    onClick: (() -> Unit)? = null,
    onOptionSelected: ((MetricCardOption) -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var showOptions by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(32.dp)
    val containerColor by animateColorAsState(
        targetValue = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(durationMillis = 220),
        label = "metricCardContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = if (highlighted) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        },
        animationSpec = tween(durationMillis = 220),
        label = "metricCardBorder"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (highlighted) 1.5.dp else 1.dp,
        animationSpec = tween(durationMillis = 220),
        label = "metricCardBorderWidth"
    )

    Card(
        modifier = modifier
            .then(
                if (onClick != null || options.isNotEmpty()) {
                    Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = {
                            if (options.isNotEmpty()) {
                                showOptions = true
                            }
                        }
                    )
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(width = borderWidth, color = borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 1.dp),
        shape = cardShape
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (leadingContent != null) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                leadingContent()
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (trailingContent != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            content = trailingContent
                        )
                    }
                }

                content()
            }

            DropdownMenu(
                expanded = showOptions,
                onDismissRequest = { showOptions = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(text = option.label) },
                        onClick = {
                            showOptions = false
                            onOptionSelected?.invoke(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MetricCardInputDialog(
    title: String,
    initialValue: String,
    defaultValue: String,
    unitLabel: String?,
    inputType: MetricCardInputType,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val startingValue = initialValue.ifBlank { defaultValue }
    var value by remember(initialValue, defaultValue) { mutableStateOf(startingValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (inputType) {
                    MetricCardInputType.Text -> {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }

                    MetricCardInputType.Integer -> {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { typed ->
                                if (typed.isEmpty() || typed.all { it.isDigit() }) {
                                    value = typed
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            suffix = {
                                if (!unitLabel.isNullOrBlank()) {
                                    Text(text = unitLabel)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    is MetricCardInputType.SingleChoice -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            inputType.choices.forEach { choice ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (value == choice.id) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainer
                                        }
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (value == choice.id) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                        }
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    onClick = { value = choice.id }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = value == choice.id,
                                            onClick = { value = choice.id }
                                        )
                                        Text(text = stringResource(choice.labelResId))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalized = value.trim().ifBlank { defaultValue }
                    onConfirm(normalized)
                    onDismiss()
                }
            ) {
                Text(text = stringResource(R.string.common_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    )
}
