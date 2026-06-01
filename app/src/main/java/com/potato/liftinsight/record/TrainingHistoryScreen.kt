package com.potato.liftinsight.record

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.potato.liftinsight.R
import com.potato.liftinsight.camera.CameraScreen
import com.potato.liftinsight.motion.MotionVideoPlayer
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.training.data.MetaHistoryRecord
import com.potato.liftinsight.training.data.VideoProcessState
import com.potato.liftinsight.ui.theme.LiftInsightMotion
import com.potato.liftinsight.video.VideoProcessingStatus
import com.potato.liftinsight.video.VideoProcessor
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun TrainingHistoryScreen(
    trainingPlanStore: TrainingPlanStore,
    videoProcessor: VideoProcessor,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var records by remember { mutableStateOf<List<MetaHistoryRecord>>(emptyList()) }
    var selectedRecord by remember { mutableStateOf<MetaHistoryRecord?>(null) }
    var selectedVideoStatus by remember { mutableStateOf<VideoProcessingStatus?>(null) }
    var videoPlayerUri by remember { mutableStateOf<Uri?>(null) }
    var videoEditorRecord by remember { mutableStateOf<MetaHistoryRecord?>(null) }
    var videoEditorHasProcessedCopy by remember { mutableStateOf(false) }
    var importTargetRecord by remember { mutableStateOf<MetaHistoryRecord?>(null) }
    var cameraTargetRecord by remember { mutableStateOf<MetaHistoryRecord?>(null) }
    var videoStatusRefreshKey by remember { mutableStateOf(0) }
    var showContent by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val localVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val targetRecord = importTargetRecord
        importTargetRecord = null

        if (uri == null || targetRecord == null) {
            return@rememberLauncherForActivityResult
        }

        coroutineScope.launch {
            val importedVideoName = withContext(Dispatchers.IO) {
                importVideoIntoAppStorage(
                    context = context,
                    uri = uri,
                    motionId = targetRecord.motionId,
                    setIndex = 1
                )
            }

            if (importedVideoName != null) {
                attachVideoToRecord(
                    trainingPlanStore = trainingPlanStore,
                    records = records,
                    selectedRecord = selectedRecord,
                    historyId = targetRecord.id,
                    videoName = importedVideoName,
                    onRecordsChange = { records = it },
                    onSelectedRecordChange = {
                        selectedRecord = it
                        selectedVideoStatus = null
                        videoStatusRefreshKey += 1
                    }
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        records = withContext(Dispatchers.IO) {
            trainingPlanStore.getMetaHistoryRecords()
        }
        showContent = true
    }

    LaunchedEffect(selectedRecord?.videoName, videoStatusRefreshKey) {
        val videoName = selectedRecord?.videoName

        if (videoName.isNullOrBlank()) {
            selectedVideoStatus = null
            return@LaunchedEffect
        }

        selectedVideoStatus = null

        while (true) {
            val status = withContext(Dispatchers.IO) {
                videoProcessor.getStatus(videoName)
            }

            selectedVideoStatus = status

            if (status.state != VideoProcessState.PROCESSING) {
                break
            }

            delay(1_000L)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.record_training_card_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val grouped = records.groupBy { it.date.take(10) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (grouped.isEmpty()) {
                AnimatedVisibility(
                    visible = showContent,
                    enter = trainingSectionEnter(delayMillis = 80),
                    exit = ExitTransition.None,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.training_no_records),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                AnimatedVisibility(
                    visible = showContent,
                    enter = trainingSectionEnter(delayMillis = 0),
                    exit = ExitTransition.None
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        grouped.forEach { (dateKey, dayRecords) ->
                            item(key = "header-$dateKey") {
                                Text(
                                    text = formatDateLabel(dateKey),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                            }

                            items(
                                items = dayRecords,
                                key = { it.id }
                            ) { record ->
                                TrainingHistoryCard(
                                    record = record,
                                    onClick = { selectedRecord = record }
                                )
                            }

                            item(key = "spacer-$dateKey") {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }

    selectedRecord?.let { record ->
        TrainingHistoryDetailSheet(
            record = record,
            processState = processStateLabel(
                status = selectedVideoStatus,
                hasVideo = !record.videoName.isNullOrBlank()
            ),
            canProcessVideo = !record.videoName.isNullOrBlank() && selectedVideoStatus?.hasProcessedCopy != true && selectedVideoStatus?.isProcessing != true,
            canEditVideo = !record.videoName.isNullOrBlank() && selectedVideoStatus?.isProcessing != true,
            onDismiss = { selectedRecord = null },
            onSaveDetails = { weight, rep, rpe ->
                coroutineScope.launch {
                    updateTrainingRecordDetails(
                        trainingPlanStore = trainingPlanStore,
                        records = records,
                        selectedRecord = selectedRecord,
                        historyId = record.id,
                        weight = weight,
                        rep = rep,
                        rpe = rpe,
                        onRecordsChange = { records = it },
                        onSelectedRecordChange = { selectedRecord = it }
                    )
                }
            },
            onPlayVideo = {
                val videoName = record.videoName

                if (videoName.isNullOrBlank()) {
                    return@TrainingHistoryDetailSheet
                }

                selectedRecord = null

                coroutineScope.launch {
                    val playbackFile = withContext(Dispatchers.IO) {
                        videoProcessor.getPlaybackVideoFile(videoName)
                    }

                    videoPlayerUri = playbackFile?.let(Uri::fromFile)
                }
            },
            onImportVideo = {
                importTargetRecord = record
            },
            onEditVideo = {
                videoEditorHasProcessedCopy = selectedVideoStatus?.hasProcessedCopy == true
                videoEditorRecord = record
                selectedRecord = null
            },
            onProcessVideo = {
                record.videoName
                    ?.takeIf { it.isNotBlank() }
                    ?.let { videoName ->
                        videoProcessor.submitForProcessing(videoName)
                        selectedVideoStatus = null
                        videoStatusRefreshKey += 1
                    }
            }
        )
    }

    importTargetRecord?.let {
        AlertDialog(
            onDismissRequest = { importTargetRecord = null },
            title = { Text(text = stringResource(R.string.training_import_video_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { localVideoPicker.launch("video/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.training_import_video_local))
                    }

                    Button(
                        onClick = {
                            cameraTargetRecord = importTargetRecord
                            importTargetRecord = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.training_import_video_camera))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { importTargetRecord = null }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    cameraTargetRecord?.let { record ->
        Dialog(
            onDismissRequest = { cameraTargetRecord = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            CameraScreen(
                motionTitle = record.motionName,
                motionId = record.motionId,
                setIndex = 1,
                setsInMotion = 1,
                expectedReps = record.rep,
                expectedWeight = record.weight,
                expectedIntensity = record.rpe.toDouble(),
                onRecordingFinished = { videoName ->
                    val targetRecord = cameraTargetRecord
                    cameraTargetRecord = null

                    if (targetRecord != null && !videoName.isNullOrBlank()) {
                        coroutineScope.launch {
                            attachVideoToRecord(
                                trainingPlanStore = trainingPlanStore,
                                records = records,
                                selectedRecord = selectedRecord,
                                historyId = targetRecord.id,
                                videoName = videoName,
                                onRecordsChange = { records = it },
                                onSelectedRecordChange = {
                                    selectedRecord = it
                                    selectedVideoStatus = null
                                    videoStatusRefreshKey += 1
                                }
                            )
                        }
                    }
                },
                onBack = { cameraTargetRecord = null },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    videoPlayerUri?.let { videoUri ->
        MotionVideoPlayer(
            videoUri = videoUri,
            onDismiss = { videoPlayerUri = null }
        )
    }

    videoEditorRecord?.let { record ->
        val videoName = record.videoName

        if (!videoName.isNullOrBlank()) {
            Dialog(
                onDismissRequest = { videoEditorRecord = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                TrainingVideoEditorDialog(
                    videoFileName = videoName,
                    videoProcessor = videoProcessor,
                    hasProcessedCopy = videoEditorHasProcessedCopy,
                    onDismiss = {
                        videoEditorRecord = null
                        videoEditorHasProcessedCopy = false
                    },
                    onSaved = {
                        videoEditorRecord = null
                        videoEditorHasProcessedCopy = false
                        selectedRecord = record
                        selectedVideoStatus = null
                        videoStatusRefreshKey += 1
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun TrainingHistoryCard(
    record: MetaHistoryRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FitnessCenter,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = record.motionName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = stringResource(
                        R.string.training_detail_summary,
                        record.weight,
                        record.rep,
                        record.rpe
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = stringResource(R.string.training_weight_value, record.weight),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = stringResource(R.string.training_rep_value, record.rep),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TrainingHistoryDetailSheet(
    record: MetaHistoryRecord,
    processState: String,
    canProcessVideo: Boolean,
    canEditVideo: Boolean,
    onDismiss: () -> Unit,
    onSaveDetails: (weight: Double, rep: Int, rpe: Int) -> Unit,
    onPlayVideo: () -> Unit,
    onImportVideo: () -> Unit,
    onEditVideo: () -> Unit,
    onProcessVideo: () -> Unit
) {
    val hasVideo = !record.videoName.isNullOrBlank()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isEditing by remember(record.id, record.weight, record.rep, record.rpe) { mutableStateOf(false) }
    var weightInput by remember(record.id, record.weight) { mutableStateOf(record.weight.toString()) }
    var repInput by remember(record.id, record.rep) { mutableStateOf(record.rep.toString()) }
    var rpeInput by remember(record.id, record.rpe) { mutableStateOf(record.rpe.toString()) }
    val parsedWeight = weightInput.toDoubleOrNull()
    val parsedRep = repInput.toIntOrNull()
    val parsedRpe = rpeInput.toIntOrNull()
    val canSaveDetails = parsedWeight != null && parsedRep != null && parsedRpe != null && (
        parsedWeight != record.weight || parsedRep != record.rep || parsedRpe != record.rpe
    )

    fun saveDetails() {
        val weight = parsedWeight
        val rep = parsedRep
        val rpe = parsedRpe

        if (weight != null && rep != null && rpe != null) {
            onSaveDetails(weight, rep, rpe)
            isEditing = false
        }
    }

    fun resetEditableFields() {
        weightInput = record.weight.toString()
        repInput = record.rep.toString()
        rpeInput = record.rpe.toString()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = record.motionName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            if (isEditing) {
                                saveDetails()
                            } else {
                                isEditing = true
                            }
                        },
                        enabled = if (isEditing) canSaveDetails else true
                    ) {
                        Icon(
                            imageVector = if (isEditing) {
                                Icons.Rounded.Check
                            } else {
                                Icons.Rounded.Edit
                            },
                            contentDescription = stringResource(
                                if (isEditing) {
                                    R.string.training_detail_save_content_description
                                } else {
                                    R.string.training_detail_edit_content_description
                                },
                                record.motionName
                            )
                        )
                    }
                }

                Text(
                    text = stringResource(
                        R.string.training_detail_summary,
                        parsedWeight ?: record.weight,
                        parsedRep ?: record.rep,
                        parsedRpe ?: record.rpe
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailChip(
                    label = stringResource(R.string.training_detail_date),
                    value = record.date
                )
                DetailChip(
                    label = stringResource(R.string.training_detail_video_status),
                    value = processState,
                    highlighted = hasVideo,
                    supportingText = if (canProcessVideo) {
                        stringResource(R.string.training_video_status_action)
                    } else {
                        null
                    },
                    onClick = if (canProcessVideo) onProcessVideo else null
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.training_detail_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (isEditing) {
                        OutlinedTextField(
                            value = weightInput,
                            onValueChange = { weightInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.training_detail_weight)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    } else {
                        DetailRow(
                            label = stringResource(R.string.training_detail_weight),
                            value = stringResource(R.string.training_weight_value, record.weight)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                    if (isEditing) {
                        OutlinedTextField(
                            value = repInput,
                            onValueChange = { repInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.training_detail_reps)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    } else {
                        DetailRow(
                            label = stringResource(R.string.training_detail_reps),
                            value = stringResource(R.string.training_detail_reps_value, record.rep)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                    if (isEditing) {
                        OutlinedTextField(
                            value = rpeInput,
                            onValueChange = { rpeInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.training_detail_rpe)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    } else {
                        DetailRow(
                            label = stringResource(R.string.training_detail_rpe),
                            value = stringResource(R.string.training_detail_rpe_value, record.rpe)
                        )
                    }
                }
            }

            Button(
                onClick = onPlayVideo,
                enabled = hasVideo,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (hasVideo) {
                        Icons.Rounded.PlayArrow
                    } else {
                        Icons.Rounded.VideocamOff
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.training_play_video))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onImportVideo,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.training_import_video))
                }

                Button(
                    onClick = onEditVideo,
                    enabled = canEditVideo,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCut,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.training_edit_video))
                }
            }

            if (!hasVideo) {
                Text(
                    text = stringResource(R.string.training_video_unavailable_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isEditing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            resetEditableFields()
                            isEditing = false
                        }
                    ) {
                        Text(text = stringResource(R.string.common_cancel))
                    }

                    Button(
                        onClick = ::saveDetails,
                        enabled = canSaveDetails
                    ) {
                        Text(text = stringResource(R.string.common_save))
                    }
                }
            } else {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DetailChip(
    label: String,
    value: String,
    highlighted: Boolean = false,
    supportingText: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val containerColor = if (highlighted) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Surface(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        ),
        color = containerColor,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            supportingText?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun formatDateLabel(dateKey: String): String {
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val parsedDate = parser.parse(dateKey) ?: return dateKey
    return formatter.format(parsedDate)
}

private fun trainingSectionEnter(delayMillis: Int): EnterTransition {
    return fadeIn(
        animationSpec = tween(
            durationMillis = LiftInsightMotion.MediumDuration,
            delayMillis = delayMillis,
            easing = LiftInsightMotion.EnterEasing
        )
    ) + slideInVertically(
        animationSpec = tween(
            durationMillis = LiftInsightMotion.LongDuration,
            delayMillis = delayMillis,
            easing = LiftInsightMotion.EnterEasing
        ),
        initialOffsetY = { fullHeight -> fullHeight / 12 }
    )
}

@Composable
private fun processStateLabel(
    status: VideoProcessingStatus?,
    hasVideo: Boolean
): String {
    if (!hasVideo) {
        return stringResource(R.string.training_process_state_no)
    }

    if (status?.hasProcessedCopy == true) {
        return stringResource(R.string.training_process_state_yes)
    }

    if (status?.isProcessing == true) {
        return stringResource(
            R.string.training_process_state_progress,
            status.progress.coerceIn(0, 99)
        )
    }

    return stringResource(R.string.training_process_state_not_processed)
}

private suspend fun attachVideoToRecord(
    trainingPlanStore: TrainingPlanStore,
    records: List<MetaHistoryRecord>,
    selectedRecord: MetaHistoryRecord?,
    historyId: Int,
    videoName: String,
    onRecordsChange: (List<MetaHistoryRecord>) -> Unit,
    onSelectedRecordChange: (MetaHistoryRecord?) -> Unit
) {
    val normalizedVideoName = videoName.trim()

    if (normalizedVideoName.isBlank()) {
        return
    }

    val didUpdate = withContext(Dispatchers.IO) {
        trainingPlanStore.updateMetaHistoryVideoName(historyId, normalizedVideoName)
    }

    if (!didUpdate) {
        return
    }

    val updatedRecords = records.map { record ->
        if (record.id == historyId) {
            record.copy(videoName = normalizedVideoName)
        } else {
            record
        }
    }
    onRecordsChange(updatedRecords)

    onSelectedRecordChange(
        selectedRecord
            ?.takeIf { it.id == historyId }
            ?.copy(videoName = normalizedVideoName)
            ?: updatedRecords.firstOrNull { it.id == historyId }
    )
}

private suspend fun updateTrainingRecordDetails(
    trainingPlanStore: TrainingPlanStore,
    records: List<MetaHistoryRecord>,
    selectedRecord: MetaHistoryRecord?,
    historyId: Int,
    weight: Double,
    rep: Int,
    rpe: Int,
    onRecordsChange: (List<MetaHistoryRecord>) -> Unit,
    onSelectedRecordChange: (MetaHistoryRecord?) -> Unit
) {
    val didUpdate = withContext(Dispatchers.IO) {
        trainingPlanStore.updateMetaHistoryDetails(
            historyId = historyId,
            weight = weight,
            rep = rep,
            rpe = rpe
        )
    }

    if (!didUpdate) {
        return
    }

    val updatedRecords = records.map { historyRecord ->
        if (historyRecord.id == historyId) {
            historyRecord.copy(weight = weight, rep = rep, rpe = rpe)
        } else {
            historyRecord
        }
    }
    onRecordsChange(updatedRecords)

    onSelectedRecordChange(
        selectedRecord
            ?.takeIf { it.id == historyId }
            ?.copy(weight = weight, rep = rep, rpe = rpe)
            ?: updatedRecords.firstOrNull { it.id == historyId }
    )
}

private fun importVideoIntoAppStorage(
    context: Context,
    uri: Uri,
    motionId: Int,
    setIndex: Int
): String? {
    val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        ?: context.filesDir

    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }

    val outputFile = File(outputDir, importedVideoFileName(motionId, setIndex))

    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            outputFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: return null

        outputFile.name
    } catch (_: IOException) {
        null
    }
}

private fun importedVideoFileName(motionId: Int, setIndex: Int): String {
    val formatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd-HH-mm-ss")
        .withZone(ZoneId.systemDefault())

    return "${formatter.format(Instant.now())}-${motionId}-${setIndex}.mp4"
}
