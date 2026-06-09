package com.potato.liftinsight.record

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.potato.liftinsight.record.model.AnalysisVideoState
import com.potato.liftinsight.ui.component.VideoPreviewCard
import com.potato.liftinsight.video.VideoProcessor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnalysisVideoScreen(
    videoFileName: String,
    videoProcessor: VideoProcessor,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var analysisState by remember { mutableStateOf(AnalysisVideoState()) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }
    var videoFile by remember { mutableStateOf<File?>(null) }

    val player = remember(videoFileName, context) {
        ExoPlayer.Builder(context).build()
    }

    // Listen for player state changes
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(videoFileName) {
        isLoading = true

        videoFile = withContext(Dispatchers.IO) {
            videoProcessor.getPlaybackVideoFile(videoFileName)
        }

        isLoading = false
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                },
                actions = {
                    TextButton(onClick = onConfirm) {
                        Text(text = stringResource(R.string.common_save))
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
                    } else {
                        player.play()
                    }
                },
                title = stringResource(R.string.training_analysis_preview_title)
            )

            AnalysisOptionsCard(
                analysisState = analysisState,
                onTogglePoseDetection = {
                    analysisState = analysisState.togglePoseDetection()
                },
                onToggleAngleDisplay = {
                    analysisState = analysisState.toggleAngleDisplay()
                },
                onToggleAnglePlot = {
                    analysisState = analysisState.toggleAnglePlot()
                },
                onToggleBarbellDetection = {
                    analysisState = analysisState.toggleBarbellDetection()
                },
                onTogglePowerCalculation = {
                    analysisState = analysisState.togglePowerCalculation()
                }
            )
        }
    }
}

@Composable
private fun AnalysisOptionsCard(
    analysisState: AnalysisVideoState,
    onTogglePoseDetection: () -> Unit,
    onToggleAngleDisplay: () -> Unit,
    onToggleAnglePlot: () -> Unit,
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
                label = stringResource(R.string.training_analysis_angle_display),
                checked = analysisState.angleDisplay,
                onCheckedChange = { onToggleAngleDisplay() }
            )

            AnalysisToggleRow(
                label = stringResource(R.string.training_analysis_angle_plot),
                checked = analysisState.anglePlot,
                onCheckedChange = { onToggleAnglePlot() }
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
