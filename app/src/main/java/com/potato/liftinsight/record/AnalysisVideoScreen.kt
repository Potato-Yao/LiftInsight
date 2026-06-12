package com.potato.liftinsight.record

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.potato.liftinsight.R
import com.potato.liftinsight.training.data.LiftInsightDatabase
import com.potato.liftinsight.training.data.TimeseriesMetric
import com.potato.liftinsight.training.data.TimeseriesPoint
import com.potato.liftinsight.motion.MotionVideoPlayer
import com.potato.liftinsight.ui.component.VideoPreviewCard
import com.potato.liftinsight.video.VideoProcessor
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnalysisVideoScreen(
    videoFileName: String,
    metahistoryId: Int?,
    videoProcessor: VideoProcessor,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }
    var videoFile by remember { mutableStateOf<File?>(null) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }

    // Overlay toggle state (local, not persisted)
    var showSkeleton by remember { mutableStateOf(true) }
    var showAngleDisplay by remember { mutableStateOf(false) }
    var showAnglePlot by remember { mutableStateOf(false) }
    var showBarbellTrace by remember { mutableStateOf(false) }
    var rdpSmoothSkeleton by remember { mutableStateOf(false) }

    // RDP epsilon value
    var rdpEpsilon by remember { mutableStateOf(1.5) }

    // Fullscreen state
    var isFullscreen by remember { mutableStateOf(false) }

    // Pose data loaded from DB
    var poseFrames by remember { mutableStateOf<List<PoseFrameSnapshot>>(emptyList()) }
    var angleData by remember { mutableStateOf<Map<String, List<TimeseriesPoint>>>(emptyMap()) }
    var barbellFrames by remember { mutableStateOf<List<BarbellFrameSnapshot>>(emptyList()) }

    val scope = rememberCoroutineScope()

    val player = remember(videoFileName, context) {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                    currentPositionMs = player.currentPosition.coerceAtLeast(0L)

                    // Fallback if file-based detection failed
                    if (videoWidth <= 0 || videoHeight <= 0) {
                        val format = player.videoFormat
                        if (format != null && format.width > 0 && format.height > 0) {
                            videoWidth = format.width
                            videoHeight = format.height
                        }
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Sync current position - higher frequency
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            delay(16)  // ~60fps instead of ~20fps
        }
    }

    LaunchedEffect(videoFileName) {
        isLoading = true

        val file = withContext(Dispatchers.IO) {
            videoProcessor.getPlaybackVideoFile(videoFileName)
        }
        videoFile = file

        // Get video dimensions from file (same method as processing pipeline)
        if (file != null) {
            withContext(Dispatchers.IO) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    val width = retriever.extractMetadata(
                        android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                    )?.toIntOrNull() ?: 0
                    val height = retriever.extractMetadata(
                        android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                    )?.toIntOrNull() ?: 0
                    val rotation = retriever.extractMetadata(
                        android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                    )?.toIntOrNull() ?: 0

                    // Account for rotation — swap width/height if rotated 90 or 270
                    if (rotation == 90 || rotation == 270) {
                        videoWidth = height
                        videoHeight = width
                    } else {
                        videoWidth = width
                        videoHeight = height
                    }
                } catch (_: Exception) {
                    // fallback: use player dimensions later
                } finally {
                    retriever.release()
                }
            }
        }

        isLoading = false
    }

    // Load pose data from DB
    LaunchedEffect(metahistoryId) {
        if (metahistoryId == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val database = LiftInsightDatabase.from(context)
            val frames = database.poseFrameDao().getPoseFrames(metahistoryId)
            poseFrames = frames.map { entity ->
                PoseFrameSnapshot(
                    timestampMs = entity.timestampMs,
                    landmarks = parseLandmarksJson(entity.landmarksJson)
                )
            }
            val metrics = listOf(
                TimeseriesMetric.SPINE_ANGLE,
                TimeseriesMetric.LEFT_KNEE_ANGLE,
                TimeseriesMetric.RIGHT_KNEE_ANGLE,
                TimeseriesMetric.LEFT_LEG_SPINE_ANGLE,
                TimeseriesMetric.RIGHT_LEG_SPINE_ANGLE
            )
            val tsDao = database.timeseriesDao()
            angleData = metrics.associateWith { metric ->
                tsDao.getTimeSeries(metahistoryId, metric)
            }

            // Load barbell frames
            val bbFrames = database.barbellFrameDao().getBarbellFrames(metahistoryId)
            barbellFrames = bbFrames.map { entity ->
                BarbellFrameSnapshot(
                    timestampMs = entity.timestampMs,
                    x = entity.x,
                    y = entity.y,
                    radius = entity.radius
                )
            }

            // Load saved overlay settings
            val settings = database.planDao().getAnalysisSettings(metahistoryId)
            if (settings != null) {
                showSkeleton = settings.poseDetection
                showAngleDisplay = settings.angleDisplay
                showAnglePlot = settings.anglePlot
                showBarbellTrace = settings.barbellDetection
                rdpEpsilon = settings.rdpEpsilon
                rdpSmoothSkeleton = settings.rdpSmoothSkeleton
            }
        }
    }

    LaunchedEffect(videoFile) {
        val file = videoFile

        if (file == null) {
            return@LaunchedEffect
        }

        val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    // Pause the original player when entering fullscreen to prevent dual audio playback
    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            player.pause()
        }
    }

    // Find nearest pose frame (must be before LaunchedEffects that reference it)
    val nearestFrame = remember(currentPositionMs, poseFrames) {
        if (poseFrames.isEmpty()) return@remember null
        val idx = poseFrames.binarySearchBy(currentPositionMs) { it.timestampMs }
        val insertionPoint = if (idx >= 0) idx else -(idx + 1)
        val candidates = listOfNotNull(
            poseFrames.getOrNull(insertionPoint - 1),
            poseFrames.getOrNull(insertionPoint)
        )
        candidates.minByOrNull { kotlin.math.abs(it.timestampMs - currentPositionMs) }
    }

    // Find nearest angle values
    val currentAngles = remember(currentPositionMs, angleData) {
        angleData.mapValues { (_, points) ->
            if (points.isEmpty()) return@mapValues null
            val idx = points.binarySearchBy(currentPositionMs) { it.timestampMs }
            val insertionPoint = if (idx >= 0) idx else -(idx + 1)
            val candidates = listOfNotNull(
                points.getOrNull(insertionPoint - 1),
                points.getOrNull(insertionPoint)
            )
            candidates.minByOrNull { kotlin.math.abs(it.timestampMs - currentPositionMs) }?.value
        }
    }

    // Save settings when they change
    LaunchedEffect(showSkeleton, showAngleDisplay, showAnglePlot, showBarbellTrace, rdpEpsilon, rdpSmoothSkeleton) {
        if (metahistoryId != null) {
            saveAnalysisSettings(
                context = context,
                metahistoryId = metahistoryId,
                showSkeleton = showSkeleton,
                showAngleDisplay = showAngleDisplay,
                showAnglePlot = showAnglePlot,
                showBarbellTrace = showBarbellTrace,
                rdpEpsilon = rdpEpsilon,
                rdpSmoothSkeleton = rdpSmoothSkeleton
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(text = stringResource(R.string.training_analysis_video_title))
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.common_back)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.training_video_editor_loading),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            VideoPreviewCard(
                player = player,
                isPlaying = isPlaying,
                durationMs = durationMs,
                onPlayPause = {
                    if (isPlaying) player.pause() else player.play()
                },
                title = stringResource(R.string.training_analysis_preview_title),
                modifier = Modifier.fillMaxWidth(),
                onFullscreen = { isFullscreen = true },
                    videoOverlay = {
                        PoseOverlayCanvas(
                            poseFrame = nearestFrame,
                            currentAngles = currentAngles,
                            angleTimeSeries = angleData,
                            currentPositionMs = currentPositionMs,
                            totalDurationMs = durationMs,
                            videoWidth = videoWidth,
                            videoHeight = videoHeight,
                            showSkeleton = showSkeleton,
                            showAngleDisplay = showAngleDisplay,
                            showAnglePlot = showAnglePlot,
                            rdpEpsilon = rdpEpsilon,
                            allPoseFrames = poseFrames,
                            rdpSmoothSkeleton = rdpSmoothSkeleton,
                            showBarbellTrace = showBarbellTrace,
                            barbellFrames = barbellFrames,
                            selectableCircles = emptyList(),
                            onCircleTapped = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
            )

            // Overlay toggle bar
            OverlayToggleBar(
                showSkeleton = showSkeleton,
                showAngleDisplay = showAngleDisplay,
                showAnglePlot = showAnglePlot,
                showBarbellTrace = showBarbellTrace,
                showRdpSkeleton = rdpSmoothSkeleton,
                onToggleSkeleton = { showSkeleton = !showSkeleton },
                onToggleAngleDisplay = { showAngleDisplay = !showAngleDisplay },
                onToggleAnglePlot = { showAnglePlot = !showAnglePlot },
                onToggleBarbellTrace = { showBarbellTrace = !showBarbellTrace },
                onToggleRdpSkeleton = { rdpSmoothSkeleton = !rdpSmoothSkeleton }
            )

            // RDP smoothing settings (only visible when angle display, plot, or skeleton smoothing is on)
            if (showAngleDisplay || showAnglePlot || rdpSmoothSkeleton) {
                RdpSettingsCard(
                    rdpEpsilon = rdpEpsilon,
                    onEpsilonChanged = { rdpEpsilon = it }
                )
            }
        }
    }

        // Fullscreen overlay
        val fullscreenFile = videoFile
        if (isFullscreen && fullscreenFile != null) {
            MotionVideoPlayer(
                videoUri = Uri.fromFile(fullscreenFile),
                onDismiss = { isFullscreen = false },
                overlayContent = { fsCurrentPositionMs, fsDurationMs ->
                    // Find nearest pose frame for fullscreen player position
                    val fsNearestFrame = if (poseFrames.isEmpty()) {
                        null
                    } else {
                        val idx = poseFrames.binarySearchBy(fsCurrentPositionMs) { it.timestampMs }
                        val insertionPoint = if (idx >= 0) idx else -(idx + 1)
                        val candidates = listOfNotNull(
                            poseFrames.getOrNull(insertionPoint - 1),
                            poseFrames.getOrNull(insertionPoint)
                        )
                        candidates.minByOrNull { kotlin.math.abs(it.timestampMs - fsCurrentPositionMs) }
                    }

                    // Find nearest angle values for fullscreen player position
                    val fsCurrentAngles = angleData.mapValues { (_, points) ->
                        if (points.isEmpty()) return@mapValues null
                        val idx = points.binarySearchBy(fsCurrentPositionMs) { it.timestampMs }
                        val insertionPoint = if (idx >= 0) idx else -(idx + 1)
                        val candidates = listOfNotNull(
                            points.getOrNull(insertionPoint - 1),
                            points.getOrNull(insertionPoint)
                        )
                        candidates.minByOrNull { kotlin.math.abs(it.timestampMs - fsCurrentPositionMs) }?.value
                    }

                    PoseOverlayCanvas(
                        poseFrame = fsNearestFrame,
                        currentAngles = fsCurrentAngles,
                        angleTimeSeries = angleData,
                        currentPositionMs = fsCurrentPositionMs,
                        totalDurationMs = fsDurationMs,
                        videoWidth = videoWidth,
                        videoHeight = videoHeight,
                        showSkeleton = showSkeleton,
                        showAngleDisplay = showAngleDisplay,
                        showAnglePlot = showAnglePlot,
                        rdpEpsilon = rdpEpsilon,
                        allPoseFrames = poseFrames,
                        rdpSmoothSkeleton = rdpSmoothSkeleton,
                        showBarbellTrace = showBarbellTrace,
                        barbellFrames = barbellFrames,
                        selectableCircles = emptyList(),
                        onCircleTapped = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            )
        }
    }
}

@Composable
private fun OverlayToggleBar(
    showSkeleton: Boolean,
    showAngleDisplay: Boolean,
    showAnglePlot: Boolean,
    showBarbellTrace: Boolean,
    showRdpSkeleton: Boolean,
    onToggleSkeleton: () -> Unit,
    onToggleAngleDisplay: () -> Unit,
    onToggleAnglePlot: () -> Unit,
    onToggleBarbellTrace: () -> Unit,
    onToggleRdpSkeleton: () -> Unit
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
                text = stringResource(R.string.training_analysis_overlay_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OverlayToggleRow(
                label = stringResource(R.string.training_analysis_overlay_skeleton),
                checked = showSkeleton,
                onCheckedChange = { onToggleSkeleton() }
            )

            OverlayToggleRow(
                label = stringResource(R.string.training_analysis_overlay_angle_display),
                checked = showAngleDisplay,
                onCheckedChange = { onToggleAngleDisplay() }
            )

            OverlayToggleRow(
                label = stringResource(R.string.training_analysis_overlay_angle_plot),
                checked = showAnglePlot,
                onCheckedChange = { onToggleAnglePlot() }
            )

            OverlayToggleRow(
                label = stringResource(R.string.training_analysis_overlay_barbell_trace),
                checked = showBarbellTrace,
                onCheckedChange = { onToggleBarbellTrace() }
            )

            OverlayToggleRow(
                label = stringResource(R.string.training_analysis_overlay_rdp_skeleton),
                checked = showRdpSkeleton,
                onCheckedChange = { onToggleRdpSkeleton() }
            )
        }
    }
}

@Composable
private fun OverlayToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = { onCheckedChange() }
        )
    }
}

@Composable
private fun RdpSettingsCard(
    rdpEpsilon: Double,
    onEpsilonChanged: (Double) -> Unit
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
                text = stringResource(R.string.training_analysis_smoothing_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            RdpTextInputRow(
                label = stringResource(R.string.training_analysis_smoothing_epsilon),
                value = rdpEpsilon,
                onValueChange = onEpsilonChanged
            )
        }
    }
}

@Composable
private fun RdpTextInputRow(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit
) {
    var textValue by remember { mutableStateOf("%.1f".format(value)) }
    var isError by remember { mutableStateOf(false) }

    // Sync text when value changes externally
    LaunchedEffect(value) {
        textValue = "%.1f".format(value)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(100.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        OutlinedTextField(
            value = textValue,
            onValueChange = { newText ->
                textValue = newText
                val parsed = newText.toDoubleOrNull()
                if (parsed != null && parsed >= 0.0) {
                    isError = false
                    onValueChange(parsed)
                } else {
                    isError = true
                }
            },
            isError = isError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f).defaultMinSize(minWidth = 0.dp),
            textStyle = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun saveAnalysisSettings(
    context: android.content.Context,
    metahistoryId: Int,
    showSkeleton: Boolean,
    showAngleDisplay: Boolean,
    showAnglePlot: Boolean,
    showBarbellTrace: Boolean,
    rdpEpsilon: Double,
    rdpSmoothSkeleton: Boolean
) {
    CoroutineScope(Dispatchers.IO).launch {
        val database = LiftInsightDatabase.from(context)
        database.planDao().updateAnalysisSettings(
            recordId = metahistoryId,
            poseDetection = showSkeleton,
            angleDisplay = showAngleDisplay,
            anglePlot = showAnglePlot,
            barbellDetection = showBarbellTrace,
            powerCalculation = false,
            rdpEpsilon = rdpEpsilon,
            rdpSmoothSkeleton = rdpSmoothSkeleton
        )
    }
}
