package com.potato.liftinsight.record

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.potato.liftinsight.R
import com.potato.liftinsight.camera.CameraScreen
import com.potato.liftinsight.motion.MotionVideoPlayer
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.record.controller.TrainingHistoryController
import com.potato.liftinsight.record.model.TrainingHistoryState
import com.potato.liftinsight.training.data.MetaHistoryRecord
import com.potato.liftinsight.training.data.VideoProcessState
import com.potato.liftinsight.ui.theme.LiftInsightMotion
import com.potato.liftinsight.video.VideoProcessingStatus
import com.potato.liftinsight.video.VideoProcessor
import com.potato.liftinsight.video.imported.ImportedVideoAnalysisMode
import com.potato.liftinsight.video.imported.ImportedVideoSource
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun TrainingHistoryScreen(
    trainingPlanStore: TrainingPlanStore,
    videoProcessor: VideoProcessor,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val controller = remember(trainingPlanStore, videoProcessor) {
        TrainingHistoryController(
            trainingPlanStore = trainingPlanStore,
            videoProcessor = videoProcessor
        )
    }
    var state by remember(controller) { mutableStateOf<TrainingHistoryState>(controller.emptyState()) }
    var videoPlayerUri by remember { mutableStateOf<Uri?>(null) }
    var videoEditorRecord by remember { mutableStateOf<MetaHistoryRecord?>(null) }
    var videoEditorHasProcessedCopy by remember { mutableStateOf(false) }
    var exportDialogRecord by remember { mutableStateOf<MetaHistoryRecord?>(null) }
    var exportDialogHasProcessedCopy by remember { mutableStateOf(false) }
    var importTargetRecord by remember { mutableStateOf<MetaHistoryRecord?>(null) }
    var cameraTargetRecord by remember { mutableStateOf<MetaHistoryRecord?>(null) }
    var calibrationTargetRecord by remember { mutableStateOf<MetaHistoryRecord?>(null) }
    var showContent by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<MetaHistoryRecord?>(null) }
    var showBatchDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDayDeleteConfirmDialog by remember { mutableStateOf(false) }
    var dayToDelete by remember { mutableStateOf<String?>(null) }
    var showBinPermanentDeleteConfirmDialog by remember { mutableStateOf(false) }
    var binRecordToDelete by remember { mutableStateOf<MetaHistoryRecord?>(null) }
    var showBinRevertConfirmDialog by remember { mutableStateOf(false) }
    var binRecordToRevert by remember { mutableStateOf<MetaHistoryRecord?>(null) }
    var showBinBatchDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showBinBatchRevertConfirmDialog by remember { mutableStateOf(false) }
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
            state = controller.attachLocalVideo(state, context, uri, targetRecord)
        }
    }

    LaunchedEffect(Unit) {
        state = controller.initializeExpandedGroups(controller.loadState())
        showContent = true
    }

    LaunchedEffect(state.selectedRecord?.videoName, state.videoStatusRefreshKey) {
        val videoName = state.selectedRecord?.videoName

        if (videoName.isNullOrBlank()) {
            state = controller.clearVideoStatus(state)
            return@LaunchedEffect
        }

        state = controller.clearVideoStatus(state)

        while (true) {
            state = controller.refreshSelectedVideoStatus(state)

            if (state.selectedVideoStatus?.state != VideoProcessState.PROCESSING) {
                break
            }

            delay(1_000L)
        }
    }

    AnimatedContent(
        targetState = state.isBinMode,
        transitionSpec = {
            (fadeIn(
                animationSpec = tween(
                    durationMillis = LiftInsightMotion.MediumDuration,
                    delayMillis = 40,
                    easing = LiftInsightMotion.EnterEasing
                )
            ) + slideInHorizontally(
                animationSpec = tween(
                    durationMillis = LiftInsightMotion.LongDuration,
                    easing = LiftInsightMotion.EnterEasing
                ),
                initialOffsetX = { fullWidth -> if (targetState) fullWidth / 10 else -fullWidth / 10 }
            )) togetherWith
            (fadeOut(
                animationSpec = tween(
                    durationMillis = LiftInsightMotion.ShortDuration,
                    easing = LiftInsightMotion.ExitEasing
                )
            ) + slideOutHorizontally(
                animationSpec = tween(
                    durationMillis = LiftInsightMotion.ShortDuration,
                    easing = LiftInsightMotion.ExitEasing
                ),
                targetOffsetX = { fullWidth -> if (targetState) -fullWidth / 12 else fullWidth / 12 }
            ))
        },
        label = "trainingBinTransition"
    ) { isBinMode ->
        if (isBinMode) {
            BinScreen(
                state = state,
                controller = controller,
                onBack = { state = controller.exitBinMode(state) },
                onSelectRecord = { record -> state = controller.selectBinRecord(state, record) },
                onToggleBatchMode = { state = controller.toggleBatchMode(state) },
                onToggleRecordSelection = { recordId -> state = controller.toggleRecordSelection(state, recordId) },
                onSelectAll = {
                    val allIds = state.binRecords.map { it.id }.toSet()
                    state = state.copy(selectedRecordIds = allIds)
                },
                onPermanentDelete = { record ->
                    binRecordToDelete = record
                    showBinPermanentDeleteConfirmDialog = true
                },
                onRevert = { record ->
                    binRecordToRevert = record
                    showBinRevertConfirmDialog = true
                },
                onBatchPermanentDelete = { showBinBatchDeleteConfirmDialog = true },
                onBatchRevert = { showBinBatchRevertConfirmDialog = true },
                modifier = modifier
            )
        } else {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = if (state.isBatchMode) {
                                    stringResource(R.string.training_batch_selected_count, state.selectedRecordIds.size)
                                } else {
                                    stringResource(R.string.record_training_card_title)
                                }
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.common_back)
                                )
                            }
                        },
                        actions = {
                            if (state.isBatchMode) {
                                IconButton(onClick = {
                                    val allIds = state.records.map { it.id }.toSet()
                                    state = state.copy(
                                        selectedRecordIds = if (state.selectedRecordIds.size == state.records.size) {
                                            emptySet()
                                        } else {
                                            allIds
                                        }
                                    )
                                }) {
                                    Icon(
                                        imageVector = Icons.Rounded.SelectAll,
                                        contentDescription = stringResource(R.string.training_batch_select_all)
                                    )
                                }
    
                                IconButton(onClick = { showBatchDeleteConfirmDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteSweep,
                                        contentDescription = stringResource(R.string.training_batch_delete_selected)
                                    )
                                }
    
                                IconButton(onClick = { state = controller.toggleBatchMode(state) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = stringResource(R.string.training_batch_exit)
                                    )
                                }
                            } else {
                                IconButton(onClick = { state = controller.toggleBatchMode(state) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = stringResource(R.string.training_batch_mode)
                                    )
                                }
    
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        state = controller.loadBinState(state)
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteForever,
                                        contentDescription = stringResource(R.string.training_bin_title)
                                    )
                                }
                            }
                        }
                    )
                }
            ) { innerPadding ->
                val grouped = state.records.groupBy { it.date.take(10) }
    
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
                                    val isExpanded = dateKey in state.expandedDateGroups
    
                                    item(key = "header-$dateKey") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    state = controller.toggleDateGroup(state, dateKey)
                                                }
                                                .padding(top = 12.dp, bottom = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = formatDateLabel(dateKey),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "(${dayRecords.size})",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (state.isBatchMode) {
                                                    IconButton(
                                                        onClick = {
                                                            dayToDelete = dateKey
                                                            showDayDeleteConfirmDialog = true
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.DeleteSweep,
                                                            contentDescription = stringResource(R.string.training_batch_delete_day),
                                                            modifier = Modifier.size(18.dp),
                                                            tint = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                            }
                                            Icon(
                                                imageVector = if (isExpanded) {
                                                    Icons.Rounded.ExpandLess
                                                } else {
                                                    Icons.Rounded.ExpandMore
                                                },
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
    
                                    if (isExpanded) {
                                        items(
                                            items = dayRecords,
                                            key = { it.id }
                                        ) { record ->
                                            TrainingHistoryCard(
                                                record = record,
                                                isBatchMode = state.isBatchMode,
                                                isSelected = record.id in state.selectedRecordIds,
                                                onClick = {
                                                    if (state.isBatchMode) {
                                                        state = controller.toggleRecordSelection(state, record.id)
                                                    } else {
                                                        state = controller.selectRecord(state, record)
                                                    }
                                                }
                                            )
                                        }
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
        }
    }

    state.selectedRecord?.let { record ->
        TrainingHistoryDetailSheet(
            record = record,
            processState = processStateLabel(
                status = state.selectedVideoStatus,
                hasVideo = !record.videoName.isNullOrBlank()
            ),
            analysisState = analysisModeLabel(record),
            analysisSupportingText = analysisModeSupportingText(record),
            canProcessVideo = !record.videoName.isNullOrBlank() && state.selectedVideoStatus?.hasProcessedCopy != true && state.selectedVideoStatus?.isProcessing != true,
            canCalibrateVideo = !record.videoName.isNullOrBlank() && record.videoSource == ImportedVideoSource.LOCAL_FILE,
            onDismiss = { state = controller.dismissSelectedRecord(state) },
            onSaveDetails = { weight, rep, rpe ->
                coroutineScope.launch {
                    state = controller.updateRecordDetails(
                        state = state,
                        historyId = record.id,
                        weight = weight,
                        rep = rep,
                        rpe = rpe
                    )
                }
            },
            onDeleteRecord = {
                recordToDelete = record
                showDeleteConfirmDialog = true
            },
            onPlayVideo = {
                val videoName = record.videoName

                if (videoName.isNullOrBlank()) {
                    return@TrainingHistoryDetailSheet
                }

                state = controller.dismissSelectedRecord(state)

                coroutineScope.launch {
                    videoPlayerUri = controller.resolvePlaybackUri(videoName)
                }
            },
            onImportVideo = {
                importTargetRecord = record
            },
            onCalibrateVideo = {
                calibrationTargetRecord = record
            },
            onEditVideo = {
                videoEditorHasProcessedCopy = state.selectedVideoStatus?.hasProcessedCopy == true
                videoEditorRecord = record
                state = controller.dismissSelectedRecord(state)
            },
            onExportVideo = {
                val videoName = record.videoName

                if (videoName.isNullOrBlank()) {
                    return@TrainingHistoryDetailSheet
                }

                val hasProcessed = state.selectedVideoStatus?.hasProcessedCopy == true

                if (hasProcessed) {
                    exportDialogRecord = record
                    exportDialogHasProcessedCopy = true
                } else {
                    coroutineScope.launch {
                        val exportedUri = controller.exportOriginalVideo(context, videoName)

                        if (exportedUri != null) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.training_export_video_success),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.training_export_video_failure),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            },
            onProcessVideo = {
                record.videoName
                    ?.takeIf { it.isNotBlank() }
                    ?.let { videoName ->
                        controller.submitVideoProcessing(videoName)
                        state = controller.requestVideoStatusRefresh(state)
                    }
            }
        )
    }

    if (showDeleteConfirmDialog && recordToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                recordToDelete = null
            },
            title = { Text(text = stringResource(R.string.training_delete_confirm_title)) },
            text = { Text(text = stringResource(R.string.training_delete_confirm_message)) },
            confirmButton = {
                Button(onClick = {
                    val record = recordToDelete
                    showDeleteConfirmDialog = false
                    recordToDelete = null
                    if (record != null) {
                        state = controller.dismissSelectedRecord(state)
                        coroutineScope.launch {
                            state = controller.softDeleteRecord(state, record)
                        }
                    }
                }) {
                    Text(text = stringResource(R.string.training_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    recordToDelete = null
                }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showBatchDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirmDialog = false },
            title = { Text(text = stringResource(R.string.training_batch_delete_confirm_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.training_batch_delete_confirm_message,
                        state.selectedRecordIds.size
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    showBatchDeleteConfirmDialog = false
                    coroutineScope.launch {
                        state = controller.deleteSelectedRecords(state)
                    }
                }) {
                    Text(text = stringResource(R.string.training_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirmDialog = false }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showDayDeleteConfirmDialog && dayToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDayDeleteConfirmDialog = false
                dayToDelete = null
            },
            title = { Text(text = stringResource(R.string.training_batch_delete_day_confirm_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.training_batch_delete_day_confirm_message,
                        formatDateLabel(dayToDelete!!)
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    val dateKey = dayToDelete
                    showDayDeleteConfirmDialog = false
                    dayToDelete = null
                    if (dateKey != null) {
                        coroutineScope.launch {
                            state = controller.deleteDayRecords(state, dateKey)
                        }
                    }
                }) {
                    Text(text = stringResource(R.string.training_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDayDeleteConfirmDialog = false
                    dayToDelete = null
                }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showBinPermanentDeleteConfirmDialog && binRecordToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showBinPermanentDeleteConfirmDialog = false
                binRecordToDelete = null
            },
            title = { Text(text = stringResource(R.string.training_bin_permanent_delete_confirm_title)) },
            text = { Text(text = stringResource(R.string.training_bin_permanent_delete_confirm_message)) },
            confirmButton = {
                Button(onClick = {
                    val binRecord = binRecordToDelete
                    showBinPermanentDeleteConfirmDialog = false
                    binRecordToDelete = null
                    if (binRecord != null) {
                        state = controller.dismissBinRecord(state)
                        coroutineScope.launch {
                            state = controller.permanentlyDeleteBinRecord(state, binRecord.id)
                        }
                    }
                }) {
                    Text(text = stringResource(R.string.training_bin_permanent_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBinPermanentDeleteConfirmDialog = false
                    binRecordToDelete = null
                }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showBinRevertConfirmDialog && binRecordToRevert != null) {
        AlertDialog(
            onDismissRequest = {
                showBinRevertConfirmDialog = false
                binRecordToRevert = null
            },
            title = { Text(text = stringResource(R.string.training_bin_revert_confirm_title)) },
            text = { Text(text = stringResource(R.string.training_bin_revert_confirm_message)) },
            confirmButton = {
                Button(onClick = {
                    val binRecord = binRecordToRevert
                    showBinRevertConfirmDialog = false
                    binRecordToRevert = null
                    if (binRecord != null) {
                        state = controller.dismissBinRecord(state)
                        coroutineScope.launch {
                            state = controller.revertBinRecord(state, binRecord.id)
                        }
                    }
                }) {
                    Text(text = stringResource(R.string.training_bin_revert))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBinRevertConfirmDialog = false
                    binRecordToRevert = null
                }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showBinBatchDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBinBatchDeleteConfirmDialog = false },
            title = { Text(text = stringResource(R.string.training_bin_permanent_delete_batch_confirm_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.training_bin_permanent_delete_batch_confirm_message,
                        state.selectedRecordIds.size
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    showBinBatchDeleteConfirmDialog = false
                    coroutineScope.launch {
                        state = controller.permanentlyDeleteSelectedBinRecords(state)
                    }
                }) {
                    Text(text = stringResource(R.string.training_bin_permanent_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBinBatchDeleteConfirmDialog = false }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showBinBatchRevertConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBinBatchRevertConfirmDialog = false },
            title = { Text(text = stringResource(R.string.training_bin_revert_batch_confirm_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.training_bin_revert_batch_confirm_message,
                        state.selectedRecordIds.size
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    showBinBatchRevertConfirmDialog = false
                    coroutineScope.launch {
                        state = controller.revertSelectedBinRecords(state)
                    }
                }) {
                    Text(text = stringResource(R.string.training_bin_revert))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBinBatchRevertConfirmDialog = false }) {
                    Text(text = stringResource(R.string.common_cancel))
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
                            state = controller.attachCapturedVideo(state, targetRecord, videoName)
                        }
                    }
                },
                onBack = { cameraTargetRecord = null },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    exportDialogRecord?.let { record ->
        val videoName = record.videoName

        if (!videoName.isNullOrBlank()) {
            AlertDialog(
                onDismissRequest = {
                    exportDialogRecord = null
                    exportDialogHasProcessedCopy = false
                },
                title = { Text(text = stringResource(R.string.training_export_dialog_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (exportDialogHasProcessedCopy) {
                            Button(
                                onClick = {
                                    val name = videoName
                                    exportDialogRecord = null
                                    exportDialogHasProcessedCopy = false
                                    coroutineScope.launch {
                                        val exportedUri = controller.exportProcessedVideo(context, name)

                                        if (exportedUri != null) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.training_export_video_success),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.training_export_video_failure),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(R.string.training_export_processed_video))
                            }
                        }

                        Button(
                            onClick = {
                                val name = videoName
                                exportDialogRecord = null
                                exportDialogHasProcessedCopy = false
                                coroutineScope.launch {
                                    val exportedUri = controller.exportOriginalVideo(context, name)

                                    if (exportedUri != null) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.training_export_video_success),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.training_export_video_failure),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(R.string.training_export_original_video))
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = {
                        exportDialogRecord = null
                        exportDialogHasProcessedCopy = false
                    }) {
                        Text(text = stringResource(R.string.common_cancel))
                    }
                }
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
                        state = controller.requestVideoStatusRefresh(
                            controller.selectRecord(state, record)
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    calibrationTargetRecord?.let { record ->
        ReferenceCalibrationDialog(
            record = record,
            onDismiss = { calibrationTargetRecord = null },
            onSave = { referenceLabel, pixelDistance, distanceMeters ->
                calibrationTargetRecord = null
                coroutineScope.launch {
                    state = controller.updateReferenceCalibration(
                        state = state,
                        record = record,
                        referenceLabel = referenceLabel,
                        pixelDistance = pixelDistance,
                        distanceMeters = distanceMeters
                    )
                }
            }
        )
    }
}

@Composable
private fun TrainingHistoryCard(
    record: MetaHistoryRecord,
    isBatchMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (isBatchMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
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

                    if (!isBatchMode) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

            if (!isBatchMode) {
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
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TrainingHistoryDetailSheet(
    record: MetaHistoryRecord,
    processState: String,
    analysisState: String,
    analysisSupportingText: String?,
    canProcessVideo: Boolean,
    canCalibrateVideo: Boolean,
    onDismiss: () -> Unit,
    onSaveDetails: (weight: Double, rep: Int, rpe: Int) -> Unit,
    onDeleteRecord: () -> Unit,
    onPlayVideo: () -> Unit,
    onImportVideo: () -> Unit,
    onCalibrateVideo: () -> Unit,
    onEditVideo: () -> Unit,
    onExportVideo: () -> Unit,
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
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = onDeleteRecord) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = stringResource(
                                    R.string.training_delete_record_content_description
                                ),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }

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
                DetailChip(
                    label = stringResource(R.string.training_detail_video_analysis),
                    value = analysisState,
                    highlighted = record.importedVideoAnalysisMode == ImportedVideoAnalysisMode.REFERENCE_CALIBRATED,
                    supportingText = analysisSupportingText
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPlayVideo,
                    enabled = hasVideo,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (hasVideo) {
                            Icons.Rounded.PlayArrow
                        } else {
                            Icons.Rounded.VideocamOff
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.training_play_video),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Button(
                    onClick = onImportVideo,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.training_import_video),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onExportVideo,
                    enabled = hasVideo,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileDownload,
                        contentDescription = stringResource(R.string.training_export_video_content_description),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.training_export_video),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Button(
                    onClick = onEditVideo,
                    enabled = hasVideo,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCut,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.training_edit_video),
                        style = MaterialTheme.typography.labelLarge
                    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BinScreen(
    state: TrainingHistoryState,
    controller: TrainingHistoryController,
    onBack: () -> Unit,
    onSelectRecord: (MetaHistoryRecord) -> Unit,
    onToggleBatchMode: () -> Unit,
    onToggleRecordSelection: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onPermanentDelete: (MetaHistoryRecord) -> Unit,
    onRevert: (MetaHistoryRecord) -> Unit,
    onBatchPermanentDelete: () -> Unit,
    onBatchRevert: () -> Unit,
    modifier: Modifier = Modifier
) {
    val grouped = state.binRecords.groupBy { it.date.take(10) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isBatchMode) {
                            stringResource(R.string.training_batch_selected_count, state.selectedRecordIds.size)
                        } else {
                            stringResource(R.string.training_bin_title)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    if (state.isBatchMode) {
                        IconButton(onClick = onSelectAll) {
                            Icon(
                                imageVector = Icons.Rounded.SelectAll,
                                contentDescription = stringResource(R.string.training_batch_select_all)
                            )
                        }

                        IconButton(onClick = onBatchRevert) {
                            Icon(
                                imageVector = Icons.Rounded.RestoreFromTrash,
                                contentDescription = stringResource(R.string.training_bin_revert)
                            )
                        }

                        IconButton(onClick = onBatchPermanentDelete) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteForever,
                                contentDescription = stringResource(R.string.training_bin_permanent_delete)
                            )
                        }

                        IconButton(onClick = onToggleBatchMode) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.training_batch_exit)
                            )
                        }
                    } else {
                        IconButton(onClick = onToggleBatchMode) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.training_batch_mode)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (grouped.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.training_bin_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    grouped.forEach { (dateKey, dayRecords) ->
                        item(key = "bin-header-$dateKey") {
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
                                isBatchMode = state.isBatchMode,
                                isSelected = record.id in state.selectedRecordIds,
                                onClick = {
                                    if (state.isBatchMode) {
                                        onToggleRecordSelection(record.id)
                                    } else {
                                        onSelectRecord(record)
                                    }
                                }
                            )
                        }

                        item(key = "bin-spacer-$dateKey") {
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

    state.selectedBinRecord?.let { record ->
        BinDetailSheet(
            record = record,
            onDismiss = { controller.dismissBinRecord(state) },
            onRevert = { onRevert(record) },
            onPermanentDelete = { onPermanentDelete(record) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BinDetailSheet(
    record: MetaHistoryRecord,
    onDismiss: () -> Unit,
    onRevert: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = record.motionName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = stringResource(
                        R.string.training_detail_summary,
                        record.weight,
                        record.rep,
                        record.rpe
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

                    DetailRow(
                        label = stringResource(R.string.training_detail_weight),
                        value = stringResource(R.string.training_weight_value, record.weight)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                    DetailRow(
                        label = stringResource(R.string.training_detail_reps),
                        value = stringResource(R.string.training_detail_reps_value, record.rep)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                    DetailRow(
                        label = stringResource(R.string.training_detail_rpe),
                        value = stringResource(R.string.training_detail_rpe_value, record.rpe)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRevert,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestoreFromTrash,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.training_bin_revert),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Button(
                    onClick = onPermanentDelete,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.training_bin_permanent_delete),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    }
}

@Composable
private fun ReferenceCalibrationDialog(
    record: MetaHistoryRecord,
    onDismiss: () -> Unit,
    onSave: (referenceLabel: String, pixelDistance: Double, distanceMeters: Double) -> Unit
) {
    var referenceLabel by remember(record.id, record.importedReferenceLabel) {
        mutableStateOf(record.importedReferenceLabel)
    }
    var pixelDistanceInput by remember(record.id, record.importedReferencePixelDistance) {
        mutableStateOf(record.importedReferencePixelDistance?.toString().orEmpty())
    }
    var distanceMetersInput by remember(record.id, record.importedReferenceDistanceMeters) {
        mutableStateOf(record.importedReferenceDistanceMeters?.toString().orEmpty())
    }

    val pixelDistance = pixelDistanceInput.toDoubleOrNull()
    val distanceMeters = distanceMetersInput.toDoubleOrNull()
    val canSave = !referenceLabel.trim().isEmpty() && (pixelDistance ?: 0.0) > 0.0 && (distanceMeters ?: 0.0) > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.training_video_calibration_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.training_video_calibration_helper),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = referenceLabel,
                    onValueChange = { referenceLabel = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.training_video_calibration_label)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = pixelDistanceInput,
                    onValueChange = { pixelDistanceInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.training_video_calibration_pixel_distance)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = distanceMetersInput,
                    onValueChange = { distanceMetersInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.training_video_calibration_real_distance)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(referenceLabel.trim(), pixelDistance ?: 0.0, distanceMeters ?: 0.0)
                },
                enabled = canSave
            ) {
                Text(text = stringResource(R.string.common_save))
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

@Composable
private fun analysisModeLabel(record: MetaHistoryRecord): String {
    return when (record.importedVideoAnalysisMode) {
        ImportedVideoAnalysisMode.ESTIMATED -> stringResource(R.string.training_video_analysis_estimated)
        ImportedVideoAnalysisMode.REFERENCE_CALIBRATED -> stringResource(R.string.training_video_analysis_reference_calibrated)
    }
}

@Composable
private fun analysisModeSupportingText(record: MetaHistoryRecord): String? {
    if (record.videoName.isNullOrBlank()) {
        return null
    }

    return when (record.importedVideoAnalysisMode) {
        ImportedVideoAnalysisMode.ESTIMATED -> {
            if (record.videoSource == ImportedVideoSource.LOCAL_FILE) {
                stringResource(R.string.training_video_analysis_estimated_supporting)
            } else {
                stringResource(R.string.training_video_analysis_reference_supporting)
            }
        }

        ImportedVideoAnalysisMode.REFERENCE_CALIBRATED -> {
            val label = record.importedReferenceLabel.ifBlank { stringResource(R.string.training_video_analysis_reference_calibrated) }
            val pixelDistance = record.importedReferencePixelDistance
            val distanceMeters = record.importedReferenceDistanceMeters

            if (pixelDistance != null && distanceMeters != null) {
                stringResource(
                    R.string.training_video_calibration_summary,
                    label,
                    pixelDistance,
                    distanceMeters
                )
            } else {
                stringResource(R.string.training_video_analysis_reference_supporting)
            }
        }
    }
}
