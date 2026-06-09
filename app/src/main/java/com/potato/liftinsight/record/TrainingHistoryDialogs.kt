package com.potato.liftinsight.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.potato.liftinsight.R

@Composable
internal fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.training_delete_confirm_title)) },
        text = { Text(text = stringResource(R.string.training_delete_confirm_message)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = stringResource(R.string.training_delete_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
internal fun BatchDeleteConfirmDialog(
    selectedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.training_batch_delete_confirm_title)) },
        text = {
            Text(
                text = stringResource(
                    R.string.training_batch_delete_confirm_message,
                    selectedCount
                )
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = stringResource(R.string.training_delete_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
internal fun DayDeleteConfirmDialog(
    dateLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.training_batch_delete_day_confirm_title)) },
        text = {
            Text(
                text = stringResource(
                    R.string.training_batch_delete_day_confirm_message,
                    dateLabel
                )
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = stringResource(R.string.training_delete_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
internal fun BinPermanentDeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.training_bin_permanent_delete_confirm_title)) },
        text = { Text(text = stringResource(R.string.training_bin_permanent_delete_confirm_message)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = stringResource(R.string.training_bin_permanent_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
internal fun BinRevertConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.training_bin_revert_confirm_title)) },
        text = { Text(text = stringResource(R.string.training_bin_revert_confirm_message)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = stringResource(R.string.training_bin_revert))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
internal fun BinBatchDeleteConfirmDialog(
    selectedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.training_bin_permanent_delete_batch_confirm_title)) },
        text = {
            Text(
                text = stringResource(
                    R.string.training_bin_permanent_delete_batch_confirm_message,
                    selectedCount
                )
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = stringResource(R.string.training_bin_permanent_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
internal fun BinBatchRevertConfirmDialog(
    selectedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.training_bin_revert_batch_confirm_title)) },
        text = {
            Text(
                text = stringResource(
                    R.string.training_bin_revert_batch_confirm_message,
                    selectedCount
                )
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = stringResource(R.string.training_bin_revert))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
internal fun ImportVideoDialog(
    onImportLocal: () -> Unit,
    onImportCamera: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.training_import_video_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onImportLocal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.training_import_video_local))
                }

                Button(
                    onClick = onImportCamera,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.training_import_video_camera))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    )
}
