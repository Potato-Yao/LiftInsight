package com.potato.liftinsight.record

import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.potato.liftinsight.R
import kotlinx.coroutines.delay

private const val SEEK_FACTOR = 0.002f

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun VideoPlayerDialog(
    videoFileName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var showControls by remember { mutableStateOf(true) }
    var controlsHideJob by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    val videoFile = remember(videoFileName) {
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        if (moviesDir != null) {
            java.io.File(moviesDir, videoFileName)
        } else {
            java.io.File(context.filesDir, videoFileName)
        }
    }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            setMediaItem(MediaItem.fromUri(videoFile.toURI().toString()))
            prepare()
        }
    }

    val updatedProgress by rememberUpdatedState(progress)
    val updatedDuration by rememberUpdatedState(player.duration)
    val updatedIsPlaying by rememberUpdatedState(isPlaying)

    LaunchedEffect(isPlaying) {
        player.playWhenReady = isPlaying
    }

    LaunchedEffect(playbackSpeed) {
        player.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (!isDragging) {
                val duration = player.duration
                if (duration > 0) {
                    progress = player.currentPosition.toFloat() / duration.toFloat()
                }
                isPlaying = player.playWhenReady && player.playbackState == Player.STATE_READY
            }
            delay(100L)
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            controlsHideJob = false
            delay(3000L)
            if (!controlsHideJob && !isDragging) {
                showControls = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable { showControls = !showControls }
        )

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Surface(
                modifier = Modifier
                    .padding(16.dp)
                    .size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                onClick = {
                    onDismiss()
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color.Black.copy(alpha = 0.6f)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val progressColor = MaterialTheme.colorScheme.primary
                val trackColor = Color.White.copy(alpha = 0.4f)
                val boxColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(boxColor)
                        .pointerInput(showControls) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    player.playWhenReady = false
                                },
                                onDragEnd = {
                                    isDragging = false
                                    player.playWhenReady = updatedIsPlaying
                                },
                                onDragCancel = {
                                    isDragging = false
                                    player.playWhenReady = updatedIsPlaying
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    val duration = updatedDuration
                                    if (duration > 0) {
                                        val width = size.width.toFloat()
                                        val progressChange =
                                            dragAmount / (width / SEEK_FACTOR)
                                        val newProgress =
                                            (updatedProgress + progressChange)
                                                .coerceIn(0f, 1f)
                                        progress = newProgress
                                        player.seekTo(
                                            (newProgress * duration).toLong()
                                        )
                                    }
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val lineY = size.height * 0.85f
                        val strokeWidth = 3.dp.toPx()
                        val progressWidth =
                            size.width * progress.coerceIn(0f, 1f)

                        drawLine(
                            color = trackColor,
                            start = Offset(0f, lineY),
                            end = Offset(size.width, lineY),
                            strokeWidth = strokeWidth
                        )
                        drawLine(
                            color = progressColor,
                            start = Offset(0f, lineY),
                            end = Offset(progressWidth, lineY),
                            strokeWidth = strokeWidth
                        )

                        val thumbRadius = 5.dp.toPx()
                        drawCircle(
                            color = progressColor,
                            radius = thumbRadius,
                            center = Offset(
                                progressWidth.coerceIn(thumbRadius, size.width - thumbRadius),
                                lineY
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        onClick = {
                            if (!isPlaying && progress >= 0.99f) {
                                player.seekTo(0)
                                progress = 0f
                            }
                            isPlaying = !isPlaying
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val speeds = listOf(0.1f, 0.25f, 0.5f, 1f, 1.25f, 1.5f)
                    val speedLabels = listOf(
                        stringResource(R.string.playback_speed_label_01),
                        stringResource(R.string.playback_speed_label_025),
                        stringResource(R.string.playback_speed_label_05),
                        stringResource(R.string.playback_speed_label_1),
                        stringResource(R.string.playback_speed_label_125),
                        stringResource(R.string.playback_speed_label_15)
                    )

                    speeds.forEachIndexed { index, speed ->
                        val isSelected = playbackSpeed == speed
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { playbackSpeed = speed },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                            }
                        ) {
                            Text(
                                text = speedLabels[index],
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
