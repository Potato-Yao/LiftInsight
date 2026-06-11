package com.potato.liftinsight.record

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.potato.liftinsight.R
import com.potato.liftinsight.record.model.AnalysisVideoState
import com.potato.liftinsight.training.data.MetaHistoryRecord
import com.potato.liftinsight.video.ExportOverlayOptions
import com.potato.liftinsight.video.VideoProcessor

@Composable
internal fun ExportVideoDialog(
    onConfirm: (ExportOverlayOptions) -> Unit,
    onExportOriginal: () -> Unit,
    onDismiss: () -> Unit
) {
    var showSkeleton by remember { mutableStateOf(false) }
    var showAngleDisplay by remember { mutableStateOf(false) }
    var showAnglePlot by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.training_export_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.training_export_render_with_overlays),
                    style = MaterialTheme.typography.titleSmall
                )

                ExportToggleRow(
                    label = stringResource(R.string.training_export_overlay_skeleton),
                    checked = showSkeleton,
                    onCheckedChange = { showSkeleton = it }
                )
                ExportToggleRow(
                    label = stringResource(R.string.training_export_overlay_angle_display),
                    checked = showAngleDisplay,
                    onCheckedChange = { showAngleDisplay = it }
                )
                ExportToggleRow(
                    label = stringResource(R.string.training_export_overlay_angle_plot),
                    checked = showAnglePlot,
                    onCheckedChange = { showAnglePlot = it }
                )
                ExportToggleRow(
                    label = stringResource(R.string.training_export_overlay_barbell_trace),
                    checked = false,
                    onCheckedChange = { /* disabled */ },
                    enabled = false
                )

                HorizontalDivider()

                Button(
                    onClick = {
                        onConfirm(
                            ExportOverlayOptions(
                                showSkeleton = showSkeleton,
                                showAngleDisplay = showAngleDisplay,
                                showAnglePlot = showAnglePlot,
                                showBarbellTrace = false
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = showSkeleton || showAngleDisplay || showAnglePlot
                ) {
                    Text(text = stringResource(R.string.training_export_render_and_export))
                }

                TextButton(
                    onClick = onExportOriginal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.training_export_original))
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

@Composable
private fun ExportToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (enabled) it else it.alpha(0.5f) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
internal fun VideoPlayerOverlay(
    videoUri: Uri,
    onDismiss: () -> Unit
) {
    com.potato.liftinsight.motion.MotionVideoPlayer(
        videoUri = videoUri,
        onDismiss = onDismiss
    )
}

@Composable
internal fun AnalysisVideoOverlay(
    videoFileName: String,
    metahistoryId: Int?,
    videoProcessor: VideoProcessor,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnalysisVideoScreen(
            videoFileName = videoFileName,
            metahistoryId = metahistoryId,
            videoProcessor = videoProcessor,
            onDismiss = onDismiss,
            modifier = modifier.fillMaxSize()
        )
    }
}

@Composable
internal fun VideoEditorOverlay(
    videoFileName: String,
    videoProcessor: VideoProcessor,
    hasProcessedCopy: Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onAnalysisSaved: (AnalysisVideoState) -> Unit = {},
    initialAnalysisState: AnalysisVideoState = AnalysisVideoState()
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        TrainingVideoEditorDialog(
            videoFileName = videoFileName,
            videoProcessor = videoProcessor,
            hasProcessedCopy = hasProcessedCopy,
            onDismiss = onDismiss,
            onSaved = onSaved,
            onAnalysisSaved = onAnalysisSaved,
            initialAnalysisState = initialAnalysisState,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
internal fun CalibrationOverlay(
    record: MetaHistoryRecord,
    onDismiss: () -> Unit,
    onSave: (referenceLabel: String, pixelDistance: Double, distanceMeters: Double) -> Unit
) {
    ReferenceCalibrationDialog(
        record = record,
        onDismiss = onDismiss,
        onSave = onSave
    )
}

