package com.potato.liftinsight.record

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.potato.liftinsight.R
import com.potato.liftinsight.record.model.AnalysisVideoState
import com.potato.liftinsight.ui.component.VideoPreviewCard
import com.potato.liftinsight.video.VideoEditSelection
import com.potato.liftinsight.video.VideoEditSelections
import com.potato.liftinsight.video.VideoEditor
import com.potato.liftinsight.video.VideoEditorState
import com.potato.liftinsight.video.VideoProcessor
import com.potato.liftinsight.video.VideoTimelineSegment
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrainingVideoEditorDialog(
    videoFileName: String,
    videoProcessor: VideoProcessor,
    hasProcessedCopy: Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onAnalysisSaved: (AnalysisVideoState) -> Unit = {},
    initialAnalysisState: AnalysisVideoState = AnalysisVideoState(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember(videoFileName, context) {
        ExoPlayer.Builder(context).build()
    }
    val editorState = remember(videoFileName) {
        VideoEditorState(VideoEditSelection(emptyList()))
    }

    var durationMs by remember { mutableLongStateOf(0L) }
    var previewPositionMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isLoadingFiles by remember(videoFileName, hasProcessedCopy) { mutableStateOf(hasProcessedCopy) }
    var processedFile by remember(videoFileName, hasProcessedCopy) { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var analysisState by remember { mutableStateOf(initialAnalysisState) }

    val sourceFile = remember(videoFileName, context) {
        resolveSourceVideoFile(
            filesDir = context.filesDir,
            moviesDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES),
            videoFileName = videoFileName
        )
    }
    val previewFile = remember(sourceFile, processedFile) {
        processedFile ?: sourceFile
    }
    val selection = editorState.selection
    val timelineSegments = editorState.timelineSegments
    val selectedSegment = editorState.selectedSegment
    val hasEdit = remember(selection, durationMs) {
        durationMs > 0L && !VideoEditSelections.isWhole(selection, durationMs)
    }
    val canSplitAtCursor = remember(selection, previewPositionMs) {
        editorState.canSplitAt(previewPositionMs)
    }

    LaunchedEffect(videoFileName, hasProcessedCopy) {
        if (!hasProcessedCopy) {
            processedFile = null
            isLoadingFiles = false
            return@LaunchedEffect
        }

        isLoadingFiles = true
        processedFile = withContext(Dispatchers.IO) {
            videoProcessor.getProcessedVideoFile(videoFileName)
        }
        isLoadingFiles = false
    }

    LaunchedEffect(sourceFile) {
        val loadedDurationMs = VideoEditor.durationMs(sourceFile)

        durationMs = loadedDurationMs
        previewPositionMs = 0L
        editorState.reset(VideoEditSelections.whole(loadedDurationMs))
        errorMessage = null
    }

    LaunchedEffect(selection) {
        val boundedPositionMs = previewPositionMs.coerceIn(0L, selection.durationMs)

        if (boundedPositionMs != previewPositionMs) {
            previewPositionMs = boundedPositionMs
        }

        if (selection.keptRanges.isEmpty()) {
            editorState.selectSegment(null)
        } else {
            editorState.selectSegmentAtEditedPosition(boundedPositionMs)
        }
    }

    LaunchedEffect(previewFile, selection) {
        val boundedPositionMs = previewPositionMs.coerceIn(0L, selection.durationMs)

        player.setMediaSources(buildPreviewMediaSources(context, previewFile, selection), false)
        player.prepare()
        seekPlayerToEditedPosition(player, selection, boundedPositionMs)
        player.playWhenReady = isPlaying
    }

    LaunchedEffect(isPlaying) {
        player.playWhenReady = isPlaying
    }

    LaunchedEffect(player, isSaving, selection.durationMs) {
        while (true) {
            if (!isSaving) {
                if (isPlaying) {
                    val currentMediaItemIndex = player.currentMediaItemIndex

                    if (selection.keptRanges.isNotEmpty() && currentMediaItemIndex >= 0) {
                        val boundedPositionMs = currentEditedPlayerPosition(
                            player = player,
                            selection = selection,
                            fallbackPositionMs = previewPositionMs
                        ).coerceIn(0L, selection.durationMs)
                        previewPositionMs = boundedPositionMs
                    }
                }

                if (player.playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                } else {
                    isPlaying = player.playWhenReady && player.playbackState == Player.STATE_READY
                }
            }

            delay(100L)
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.training_video_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss, enabled = !isSaving) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            isSaving = true
                            errorMessage = null
                        },
                        enabled = (hasEdit || analysisState != initialAnalysisState) && !isSaving && durationMs > 0L
                    ) {
                        Text(text = stringResource(R.string.common_save))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLoadingFiles || selection.keptRanges.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(R.string.training_video_editor_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            VideoPreviewCard(
                player = player,
                isPlaying = isPlaying,
                durationMs = durationMs,
                onPlayPause = {
                    if (isPlaying) {
                        player.pause()
                        isPlaying = false
                    } else {
                        player.play()
                        isPlaying = true
                    }
                },
                currentPositionMs = previewPositionMs,
                showPositionOverlay = true,
                isSaving = isSaving,
                trailingHeaderContent = {
                    Text(
                        text = stringResource(
                            R.string.training_video_editor_preview_position,
                            formatDuration(previewPositionMs),
                            formatDuration(durationMs)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )

            TimelineEditorCard(
                segments = timelineSegments,
                totalDurationMs = selection.durationMs,
                cursorPositionMs = previewPositionMs,
                selectedSegmentIndex = editorState.selectedSegmentIndex,
                canSplitAtCursor = canSplitAtCursor,
                isSaving = isSaving,
                selectedSegment = selectedSegment,
                canUndo = editorState.canUndo,
                onCursorChange = { editedPositionMs ->
                    val boundedPositionMs = editedPositionMs.coerceIn(0L, editorState.selection.durationMs)
                    previewPositionMs = boundedPositionMs
                    editorState.selectSegmentAtEditedPosition(boundedPositionMs)
                    seekPlayerToEditedPosition(player, editorState.selection, boundedPositionMs)
                    player.pause()
                    isPlaying = false
                },
                onSplit = {
                    if (!editorState.splitAt(previewPositionMs)) {
                        return@TimelineEditorCard
                    }

                    errorMessage = null
                },
                onDeleteSelected = {
                    if (!editorState.deleteSelectedSegment()) {
                        return@TimelineEditorCard
                    }

                    previewPositionMs = previewPositionMs.coerceIn(0L, editorState.durationMs)
                    seekPlayerToEditedPosition(player, editorState.selection, previewPositionMs)
                    errorMessage = null
                },
                onUndo = {
                    if (!editorState.undo()) {
                        return@TimelineEditorCard
                    }

                    previewPositionMs = previewPositionMs.coerceIn(0L, editorState.durationMs)
                    editorState.selectSegmentAtEditedPosition(previewPositionMs)
                    seekPlayerToEditedPosition(player, editorState.selection, previewPositionMs)
                    errorMessage = null
                }
            )

            AnalysisOptionsCard(
                analysisState = analysisState,
                onTogglePoseDetection = {
                    analysisState = analysisState.togglePoseDetection()
                },
                onToggleBarbellDetection = {
                    analysisState = analysisState.toggleBarbellDetection()
                },
                onTogglePowerCalculation = {
                    analysisState = analysisState.togglePowerCalculation()
                }
            )

            errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (isSaving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }

    LaunchedEffect(isSaving) {
        if (!isSaving) {
            return@LaunchedEffect
        }

        player.pause()

        if (hasEdit) {
            val didSave = VideoEditor.applyEditInPlace(
                sourceFile = sourceFile,
                processedFile = processedFile,
                selection = selection
            )

            if (didSave) {
                onAnalysisSaved(analysisState)
                onSaved()
            } else {
                isSaving = false
                errorMessage = context.getString(R.string.training_video_editor_save_error)
            }
        } else {
            onAnalysisSaved(analysisState)
            onSaved()
        }
    }
}

@Composable
private fun TimelineEditorCard(
    segments: List<VideoTimelineSegment>,
    totalDurationMs: Long,
    cursorPositionMs: Long,
    selectedSegmentIndex: Int?,
    canSplitAtCursor: Boolean,
    isSaving: Boolean,
    selectedSegment: VideoTimelineSegment?,
    canUndo: Boolean,
    onCursorChange: (Long) -> Unit,
    onSplit: () -> Unit,
    onDeleteSelected: () -> Unit,
    onUndo: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.training_video_editor_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            TimelineEditor(
                segments = segments,
                totalDurationMs = totalDurationMs,
                cursorPositionMs = cursorPositionMs,
                selectedSegmentIndex = selectedSegmentIndex,
                canSplitAtCursor = canSplitAtCursor,
                enabled = !isSaving,
                onCursorChange = onCursorChange,
                onSplitAtCursor = onSplit
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.training_video_editor_selected_segment_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = selectedSegment?.let { segment ->
                            stringResource(
                                R.string.training_video_editor_selected_segment_value,
                                formatDuration(segment.sourceRange.startMs),
                                formatDuration(segment.sourceRange.endMs),
                                formatDuration(segment.durationMs)
                            )
                        } ?: stringResource(R.string.training_video_editor_no_segment_selected),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = onDeleteSelected,
                            enabled = selectedSegment != null && segments.size > 1 && !isSaving,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        TextButton(
                            onClick = onUndo,
                            enabled = canUndo && !isSaving,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.training_video_editor_undo_action))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineEditor(
    segments: List<VideoTimelineSegment>,
    totalDurationMs: Long,
    cursorPositionMs: Long,
    selectedSegmentIndex: Int?,
    canSplitAtCursor: Boolean,
    enabled: Boolean,
    onCursorChange: (Long) -> Unit,
    onSplitAtCursor: () -> Unit,
    modifier: Modifier = Modifier
) {
    var timelineWidthPx by remember { mutableIntStateOf(0) }

    fun updateCursor(offsetX: Float) {
        if (!enabled || totalDurationMs <= 0L || timelineWidthPx <= 0) {
            return
        }

        val ratio = (offsetX / timelineWidthPx.toFloat()).coerceIn(0f, 1f)
        onCursorChange((ratio * totalDurationMs).toLong())
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(24.dp)
            )
            .onSizeChanged { size ->
                timelineWidthPx = size.width
            }
            .pointerInput(totalDurationMs, timelineWidthPx, enabled) {
                detectTapGestures { tapOffset ->
                    updateCursor(tapOffset.x)
                }
            }
            .pointerInput(totalDurationMs, timelineWidthPx, enabled) {
                detectDragGestures(
                    onDragStart = { dragOffset ->
                        updateCursor(dragOffset.x)
                    },
                    onDrag = { change, _ ->
                        updateCursor(change.position.x)
                        change.consume()
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            segments.forEach { segment ->
                val isSelected = selectedSegmentIndex == segment.index

                Box(
                    modifier = Modifier
                        .weight(segment.durationMs.coerceAtLeast(1L).toFloat())
                        .fillMaxHeight()
                        .background(
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceBright
                            },
                            shape = RoundedCornerShape(18.dp)
                        )
                ) {
                    Text(
                        text = stringResource(
                            R.string.training_video_editor_segment_number,
                            segment.index + 1
                        ),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }

        if (timelineWidthPx > 0 && totalDurationMs > 0L) {
            segments.dropLast(1).forEach { segment ->
                val boundaryFraction =
                    (segment.editedEndMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                val markerOffsetPx = (boundaryFraction * timelineWidthPx).roundToInt()

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset(x = markerOffsetPx - 8, y = 0) }
                        .size(16.dp)
                        .pointerInput(segment.editedEndMs, enabled) {
                            detectTapGestures {
                                if (enabled) {
                                    onCursorChange(segment.editedEndMs)
                                }
                            }
                        }
                ) {}
            }
        }

        if (timelineWidthPx > 0 && totalDurationMs > 0L) {
            val cursorFraction = (cursorPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
            val cursorOffsetPx = (cursorFraction * timelineWidthPx).roundToInt()

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(x = cursorOffsetPx - 14, y = 0) }
                    .width(28.dp)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )

                if (canSplitAtCursor) {
                    FilledTonalIconButton(
                        onClick = onSplitAtCursor,
                        enabled = enabled,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-10).dp)
                            .size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCut,
                            contentDescription = stringResource(R.string.training_video_editor_split_action),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-6).dp)
                            .size(12.dp)
                    ) {}
                }
            }
        }
    }
}


@Composable
private fun AnalysisOptionsCard(
    analysisState: AnalysisVideoState,
    onTogglePoseDetection: () -> Unit,
    onToggleBarbellDetection: () -> Unit,
    onTogglePowerCalculation: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.training_analysis_selectors_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            AnalysisToggleRow(
                label = stringResource(R.string.training_analysis_pose_detection),
                checked = analysisState.poseDetection,
                onCheckedChange = { onTogglePoseDetection() }
            )

            AnalysisToggleRow(
                label = stringResource(R.string.training_analysis_barbell_detection),
                checked = analysisState.barbellDetection,
                enabled = analysisState.isBarbellDetectionEnabled,
                supportingText = if (!analysisState.isBarbellDetectionEnabled) {
                    stringResource(R.string.training_analysis_requires_pose_detection)
                } else {
                    null
                },
                onCheckedChange = { onToggleBarbellDetection() }
            )

            AnalysisToggleRow(
                label = stringResource(R.string.training_analysis_power_calculation),
                checked = analysisState.powerCalculation,
                enabled = analysisState.isPowerCalculationEnabled,
                supportingText = if (!analysisState.isPowerCalculationEnabled) {
                    stringResource(R.string.training_analysis_requires_barbell_detection)
                } else {
                    null
                },
                onCheckedChange = { onTogglePowerCalculation() }
            )
        }
    }
}

@Composable
private fun AnalysisToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    supportingText: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                modifier = Modifier.weight(1f)
            )

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }

        supportingText?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}


private fun buildPreviewMediaSources(
    context: android.content.Context,
    file: File,
    selection: VideoEditSelection
): List<MediaSource> {
    val dataSourceFactory = DefaultDataSource.Factory(context)
    val mediaSourceFactory = ProgressiveMediaSource.Factory(dataSourceFactory)
    val mediaUri = Uri.fromFile(file)

    return selection.keptRanges.map { range ->
        val baseSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(mediaUri))

        ClippingMediaSource(
            baseSource,
            range.startMs * 1_000L,
            range.endMs * 1_000L
        )
    }
}

private fun seekPlayerToEditedPosition(
    player: Player,
    selection: VideoEditSelection,
    editedPositionMs: Long
) {
    val segments = VideoEditSelections.timelineSegments(selection)
    val segmentIndex = VideoEditSelections.segmentIndexAtEditedPosition(selection, editedPositionMs)
        ?: return
    val segment = segments.getOrNull(segmentIndex) ?: return
    val offsetInsideSegmentMs = (editedPositionMs - segment.editedStartMs)
        .coerceIn(0L, segment.durationMs)

    player.seekTo(segmentIndex, offsetInsideSegmentMs)
}

private fun currentEditedPlayerPosition(
    player: Player,
    selection: VideoEditSelection,
    fallbackPositionMs: Long
): Long {
    val segments = VideoEditSelections.timelineSegments(selection)
    val segment = segments.getOrNull(player.currentMediaItemIndex)
        ?: return fallbackPositionMs.coerceIn(0L, selection.durationMs)

    return (segment.editedStartMs + player.currentPosition)
        .coerceIn(segment.editedStartMs, segment.editedEndMs)
}

private fun resolveSourceVideoFile(filesDir: File, moviesDir: File?, videoFileName: String): File {
    val moviesFile = moviesDir?.let { directory -> File(directory, videoFileName) }

    if (moviesFile?.exists() == true) {
        return moviesFile
    }

    return File(filesDir, videoFileName)
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1_000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return String.format("%02d:%02d", minutes, seconds)
}
