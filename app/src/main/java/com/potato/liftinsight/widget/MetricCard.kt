package com.potato.liftinsight.widget

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    options: List<MetricCardOption> = emptyList(),
    onClick: (() -> Unit)? = null,
    onOptionSelected: ((MetricCardOption) -> Unit)? = null,
    popupPanel: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var showPopupPanel by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.combinedClickable(
            onClick = {
                if (popupPanel != null) {
                    showPopupPanel = true
                }

                onClick?.invoke()
            },
            onLongClick = {
                if (options.isNotEmpty()) {
                    showOptions = true
                }
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )

            content()

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

    if (showPopupPanel) {
        popupPanel?.invoke { showPopupPanel = false }
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
        title = { Text(text = title) },
        text = {
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
                                onClick = { value = choice.id }
                            ) {
                                androidx.compose.foundation.layout.Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
