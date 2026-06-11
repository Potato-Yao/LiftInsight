package com.potato.liftinsight.record

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.potato.liftinsight.R
import com.potato.liftinsight.record.model.AnalysisVideoState
import com.potato.liftinsight.training.data.MetaHistoryRecord
import com.potato.liftinsight.video.VideoProcessor

@Composable
internal fun ExportVideoDialog(
    hasProcessedCopy: Boolean,
    onExportProcessed: () -> Unit,
    onExportOriginal: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.training_export_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasProcessedCopy) {
                    Button(
                        onClick = onExportProcessed,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.training_export_processed_video))
                    }
                }

                Button(
                    onClick = onExportOriginal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.training_export_original_video))
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

