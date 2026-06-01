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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.potato.liftinsight.R
import com.potato.liftinsight.video.VideoEditRange
import com.potato.liftinsight.video.VideoEditSelection
import com.potato.liftinsight.video.VideoEditSelections
import com.potato.liftinsight.video.VideoEditor
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember(videoFileName, context) {
        ExoPlayer.Builder(context).build()
    }

    var durationMs by remember { mutableLongStateOf(0L) }
    var previewPositionMs by remember { mutableLongStateOf(0L) }
    var currentSelection by remember { mutableStateOf<VideoEditSelection?>(null) }
    var selectedSegmentIndex by remember { mutableStateOf<Int?>(null) }
    var history by remember { mutableStateOf<List<VideoEditSelection>>(emptyList()) }
    var isPlaying by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isLoadingFiles by remember(videoFileName, hasProcessedCopy) { mutableStateOf(hasProcessedCopy) }
    var processedFile by remember(videoFileName, hasProcessedCopy) { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
    val selection = currentSelection ?: VideoEditSelections.whole(durationMs)
    val timelineSegments = remember(selection) {
        VideoEditSelections.timelineSegments(selection)
    }
    val hasEdit = remember(selection, durationMs) {
        !VideoEditSelections.isWhole(selection, durationMs)
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
        currentSelection = VideoEditSelections.whole(loadedDurationMs)
        selectedSegmentIndex = 0
        history = emptyList()
        errorMessage = null
    }

    LaunchedEffect(selection.durationMs, currentSelection) {
        val boundedPositionMs = previewPositionMs.coerceIn(0L, selection.durationMs)
        previewPositionMs = boundedPositionMs
        selectedSegmentIndex = VideoEditSelections.segmentIndexAtEditedPosition(selection, boundedPositionMs)
    }

    LaunchedEffect(previewFile, selection.keptRanges) {
        val boundedPositionMs = previewPositionMs.coerceIn(0L, selection.durationMs)

        player.setMediaSources(buildPreviewMediaSources(context, previewFile, selection), false)
        player.prepare()
        player.seekTo(boundedPositionMs)
        player.playWhenReady = isPlaying
    }

    LaunchedEffect(isPlaying) {
        player.playWhenReady = isPlaying
    }

    LaunchedEffect(player, isSaving, selection.durationMs) {
        while (true) {
            if (!isSaving) {
                val boundedPositionMs = player.currentPosition.coerceIn(0L, selection.durationMs)
                previewPositionMs = boundedPositionMs

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
                        enabled = hasEdit && !isSaving && durationMs > 0L
                    ) {
                        Text(text = stringResource(R.string.common_save))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLoadingFiles || currentSelection == null) {
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
            Surface(
                color = Color.Black,
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            this.player = player
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.training_video_editor_preview_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = stringResource(
                            R.string.training_video_editor_preview_position,
                            formatDuration(previewPositionMs),
                            formatDuration(selection.durationMs)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (isPlaying) {
                                    player.pause()
                                    isPlaying = false
                                } else {
                                    player.play()
                                    isPlaying = true
                                }
                            },
                            enabled = selection.durationMs > 0L && !isSaving
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = if (isPlaying) {
                                    stringResource(R.string.motion_video_pause)
                                } else {
                                    stringResource(R.string.motion_video_play)
                                }
                            )
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(28.dp)
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

                    SegmentedTimeline(
                        segments = timelineSegments,
                        totalDurationMs = selection.durationMs,
                        cursorPositionMs = previewPositionMs,
                        selectedSegmentIndex = selectedSegmentIndex,
                        onPositionChange = { editedPositionMs ->
                            val boundedPositionMs = editedPositionMs.coerceIn(0L, selection.durationMs)
                            previewPositionMs = boundedPositionMs
                            selectedSegmentIndex = VideoEditSelections.segmentIndexAtEditedPosition(
                                selection,
                                boundedPositionMs
                            )
                            player.seekTo(boundedPositionMs)
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                val previousSelection = selection
                                val updatedSelection = VideoEditSelections.splitAtEditedPosition(
                                    selection = selection,
                                    editedPositionMs = previewPositionMs
                                )

                                if (updatedSelection != previousSelection) {
                                    history = pushHistory(history, previousSelection)
                                    currentSelection = updatedSelection
                                    selectedSegmentIndex = VideoEditSelections.segmentIndexAtEditedPosition(
                                        updatedSelection,
                                        previewPositionMs
                                    )
                                    errorMessage = null
                                }
                            },
                            enabled = !isSaving && VideoEditSelections.canSplitAtEditedPosition(selection, previewPositionMs),
                            colors = IconButtonDefaults.filledTonalIconButtonColors()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCut,
                                contentDescription = stringResource(R.string.training_video_editor_split_action),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        FilledTonalIconButton(
                            onClick = {
                                val segmentIndex = selectedSegmentIndex ?: return@FilledTonalIconButton
                                val previousSelection = selection
                                val updatedSelection = VideoEditSelections.deleteSegment(
                                    selection = selection,
                                    segmentIndex = segmentIndex
                                )

                                if (updatedSelection != previousSelection) {
                                    history = pushHistory(history, previousSelection)
                                    currentSelection = updatedSelection
                                    previewPositionMs = previewPositionMs.coerceIn(0L, updatedSelection.durationMs)
                                    selectedSegmentIndex = VideoEditSelections.segmentIndexAtEditedPosition(
                                        updatedSelection,
                                        previewPositionMs
                                    )
                                    errorMessage = null
                                }
                            },
                            enabled = !isSaving && selectedSegmentIndex != null && selection.keptRanges.size > 1,
                            colors = IconButtonDefaults.filledTonalIconButtonColors()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.training_video_editor_delete_action),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        FilledTonalIconButton(
                            onClick = {
                                val previousSelection = history.lastOrNull() ?: return@FilledTonalIconButton

                                history = history.dropLast(1)
                                currentSelection = previousSelection
                                previewPositionMs = previewPositionMs.coerceIn(0L, previousSelection.durationMs)
                                selectedSegmentIndex = VideoEditSelections.segmentIndexAtEditedPosition(
                                    previousSelection,
                                    previewPositionMs
                                )
                                errorMessage = null
                            },
                            enabled = history.isNotEmpty() && !isSaving,
                            colors = IconButtonDefaults.filledTonalIconButtonColors()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = stringResource(R.string.training_video_editor_undo_action),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.training_video_editor_summary_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = stringResource(
                            R.string.training_video_editor_summary_value,
                            timelineSegments.size,
                            formatDuration(selection.durationMs),
                            formatDuration((durationMs - selection.durationMs).coerceAtLeast(0L))
                        ),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    if (hasProcessedCopy) {
                        Text(
                            text = stringResource(R.string.training_video_editor_processed_copy_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
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

        val didSave = VideoEditor.applyEditInPlace(
            sourceFile = sourceFile,
            processedFile = processedFile,
            selection = selection
        )

        if (didSave) {
            onSaved()
        } else {
            isSaving = false
            errorMessage = context.getString(R.string.training_video_editor_save_error)
        }
    }
}

@Composable
private fun SegmentedTimeline(
    segments: List<VideoTimelineSegment>,
    totalDurationMs: Long,
    cursorPositionMs: Long,
    selectedSegmentIndex: Int?,
    onPositionChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var timelineWidthPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(18.dp)
            )
            .onSizeChanged { size ->
                timelineWidthPx = size.width
            }
            .pointerInput(totalDurationMs, timelineWidthPx) {
                detectTapGestures { tapOffset ->
                    if (totalDurationMs <= 0L || timelineWidthPx <= 0) {
                        return@detectTapGestures
                    }

                    val ratio = (tapOffset.x / timelineWidthPx.toFloat()).coerceIn(0f, 1f)
                    onPositionChange((ratio * totalDurationMs).toLong())
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            segments.forEach { segment ->
                val weight = segment.durationMs.coerceAtLeast(1L).toFloat()

                Box(
                    modifier = Modifier
                        .weight(weight)
                        .fillMaxSize()
                        .background(
                            color = if (selectedSegmentIndex == segment.index) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                )
            }
        }

        val markerColor = MaterialTheme.colorScheme.outlineVariant

        if (timelineWidthPx > 0 && totalDurationMs > 0L) {
            segments.dropLast(1).forEach { segment ->
                val boundaryFraction =
                    (segment.editedEndMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                val markerOffsetPx = (boundaryFraction * timelineWidthPx).roundToInt()

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(x = markerOffsetPx - 1, y = 0) }
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(markerColor)
                )
            }
        }

        if (timelineWidthPx > 0 && totalDurationMs > 0L) {
            val cursorFraction = (cursorPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
            val cursorOffsetPx = (cursorFraction * timelineWidthPx).roundToInt()

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(x = cursorOffsetPx - 12, y = 0) }
                    .width(24.dp)
                    .fillMaxHeight()
                    .pointerInput(totalDurationMs, timelineWidthPx) {
                        var dragCursorPx = cursorOffsetPx.toFloat()

                        detectDragGestures(
                            onDragStart = {
                                if (totalDurationMs <= 0L || timelineWidthPx <= 0) {
                                    return@detectDragGestures
                                }

                                dragCursorPx = cursorOffsetPx.toFloat()
                            },
                            onDrag = { change, dragAmount ->
                                if (totalDurationMs <= 0L || timelineWidthPx <= 0) {
                                    return@detectDragGestures
                                }

                                dragCursorPx = (dragCursorPx + dragAmount.x)
                                    .coerceIn(0f, timelineWidthPx.toFloat())
                                val ratio =
                                    (dragCursorPx / timelineWidthPx.toFloat()).coerceIn(0f, 1f)
                                onPositionChange((ratio * totalDurationMs).toLong())
                                change.consume()
                            }
                        )
                    }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-6).dp)
                        .size(12.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(6.dp)
                        )
                )
            }
        }
    }
}

private fun pushHistory(
    history: List<VideoEditSelection>,
    selection: VideoEditSelection
): List<VideoEditSelection> {
    val updatedHistory = history + selection
    val overflow = updatedHistory.size - 10

    if (overflow <= 0) {
        return updatedHistory
    }

    return updatedHistory.drop(overflow)
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
