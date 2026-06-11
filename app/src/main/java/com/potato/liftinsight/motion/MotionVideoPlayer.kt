package com.potato.liftinsight.motion

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.potato.liftinsight.R
import com.potato.liftinsight.motion.controller.MotionVideoPlayerController
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SWIPE_SEEK_FACTOR = 2.0f
private const val LONG_PRESS_THRESHOLD_MS = 400L
private const val PLAY_PAUSE_ICON_DURATION_MS = 500L
private const val CONTROLS_HIDE_DELAY_MS = 2000L
private val PROGRESS_BAR_HEIGHT = 3.dp
private val PROGRESS_BAR_TOUCH_HEIGHT = 40.dp
private val THUMB_RADIUS = 6.dp
private const val TOP_ZONE_FRACTION = 0.40f
private const val BOTTOM_ZONE_FRACTION = 0.40f
private const val VERTICAL_SWIPE_THRESHOLD_FRACTION = 0.20f
private val SPEED_OPTIONS = listOf(0.25f, 0.5f, 1f, 1.25f, 1.5f)

@Composable
fun MotionVideoPlayer(
    videoUri: Uri,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onNextVideo: (() -> Unit)? = null,
    onPreviousVideo: (() -> Unit)? = null,
    overlayContent: (@Composable (currentPositionMs: Long, durationMs: Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val controller = remember(videoUri) { MotionVideoPlayerController(context, videoUri) }
    val state = controller.state

    var pollingIntervalMs by remember { mutableLongStateOf(16L) }

    LaunchedEffect(state.isPlaying, state.effectiveSpeed) {
        controller.syncPlayerState()
    }

    LaunchedEffect(Unit) {
        while (true) {
            controller.updateFromPlayer()
            delay(pollingIntervalMs)
        }
    }

    // Detect video frame rate once player is ready, then set polling interval
    DisposableEffect(controller.player) {
        val player = controller.player
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && pollingIntervalMs == 16L) {
                    val fps = player.videoFormat?.frameRate ?: 0f
                    if (fps > 0f) {
                        pollingIntervalMs = (1000f / fps).toLong().coerceIn(16L, 100L)
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(state.gestureState.showControls, state.isDraggingThumb) {
        if (state.gestureState.showControls && !state.isDraggingThumb) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controller.hideControls()
        }
    }

    LaunchedEffect(state.gestureState.showPlayPauseIcon) {
        if (state.gestureState.showPlayPauseIcon) {
            delay(PLAY_PAUSE_ICON_DURATION_MS)
            controller.hidePlayPauseIcon()
        }
    }

    DisposableEffect(Unit) {
        onDispose { controller.release() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = controller.player
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Caller-provided overlay (e.g., pose skeleton)
        overlayContent?.invoke(state.currentPosition, state.duration)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()

                        val startPosition = down.position
                        var lastPosition = down.position
                        var gestureDetermined = false
                        var isHorizontalSwipe = false
                        var isVerticalSwipe = false
                        var isLongPress = false
                        var totalVerticalDragPx = 0f
                        val touchSlopPx = viewConfiguration.touchSlop.toFloat()
                        val resumePlayback = state.isPlaying

                        while (true) {
                            val event = if (!gestureDetermined) {
                                withTimeoutOrNull(LONG_PRESS_THRESHOLD_MS) {
                                    awaitPointerEvent()
                                }
                            } else {
                                awaitPointerEvent()
                            }

                            if (event == null) {
                                gestureDetermined = true

                                val relativeY = startPosition.y / size.height.toFloat()
                                val speed = when {
                                    relativeY <= TOP_ZONE_FRACTION -> 0.5f
                                    relativeY >= (1f - BOTTOM_ZONE_FRACTION) -> 1.5f
                                    else -> null
                                }

                                if (speed != null) {
                                    isLongPress = true
                                    controller.beginSpeedBoost(speed, startPosition.y, context)
                                }

                                continue
                            }

                            val change = event.changes.firstOrNull() ?: break

                            if (!change.pressed) {
                                change.consume()
                                break
                            }

                            val dx = change.position.x - startPosition.x
                            val dy = change.position.y - startPosition.y

                            if (!gestureDetermined) {
                                val absDx = abs(dx)
                                val absDy = abs(dy)

                                if (absDx > touchSlopPx || absDy > touchSlopPx) {
                                    gestureDetermined = true

                                    if (absDx > absDy) {
                                        isHorizontalSwipe = true
                                        controller.beginSwipeSeek()
                                    } else {
                                        isVerticalSwipe = true
                                        totalVerticalDragPx = dy
                                    }
                                }
                            } else {
                                when {
                                    isHorizontalSwipe -> {
                                        change.consume()
                                        val delta = (change.position.x - lastPosition.x) /
                                            size.width.toFloat() * SWIPE_SEEK_FACTOR
                                        controller.updateSwipeSeek(delta)
                                    }

                                    isVerticalSwipe -> {
                                        change.consume()
                                        totalVerticalDragPx += change.position.y - lastPosition.y
                                    }

                                    isLongPress -> {
                                        change.consume()
                                        controller.updateSpeedBoostOffset(change.position.y)
                                    }
                                }
                            }

                            lastPosition = change.position
                        }

                        if (!gestureDetermined) {
                            controller.registerTap(down.uptimeMillis)
                        }

                        if (isHorizontalSwipe) {
                            controller.endSwipeSeek(resumePlayback)
                        }

                        if (isVerticalSwipe) {
                            val thresholdPx = size.height * VERTICAL_SWIPE_THRESHOLD_FRACTION
                            if (abs(totalVerticalDragPx) > thresholdPx) {
                                if (totalVerticalDragPx < 0f) {
                                    onNextVideo?.invoke()
                                } else {
                                    onPreviousVideo?.invoke()
                                }
                            }
                        }

                        if (isLongPress) {
                            controller.endSpeedBoost()
                        }

                        controller.showControls()
                    }
                }
        )

        AnimatedVisibility(
            visible = state.gestureState.showControls,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Surface(
                modifier = Modifier
                    .padding(16.dp)
                    .size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                onClick = onDismiss
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.motion_video_close),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.gestureState.isSeeking,
            enter = fadeIn(animationSpec = tween(100)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.65f)
            ) {
                Text(
                    text = state.gestureState.seekTimeLabel.ifEmpty { stringResource(R.string.motion_video_seek_default_time) },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        AnimatedVisibility(
            visible = state.gestureState.showPlayPauseIcon,
            enter = fadeIn(animationSpec = tween(100)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) {
                            stringResource(R.string.motion_video_pause)
                        } else {
                            stringResource(R.string.motion_video_play)
                        },
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.gestureState.isSpeedBoosting,
            enter = fadeIn(animationSpec = tween(100)),
            exit = fadeOut(animationSpec = tween(150))
        ) {
            Surface(
                modifier = Modifier.offset {
                    val badgeY = (state.gestureState.boostOffsetY - with(density) { 48.dp.toPx() })
                        .roundToInt()
                        .coerceAtLeast(0)
                    IntOffset(with(density) { 24.dp.roundToPx() }, badgeY)
                },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            ) {
                Text(
                    text = state.gestureState.boostSpeedLabel,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        val controlsAlpha by animateFloatAsState(
            targetValue = if (state.gestureState.showControls || state.isDraggingThumb) 1f else 0f,
            animationSpec = tween(200),
            label = "controlsAlpha"
        )

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            SpeedButtonRow(
                baseSpeed = state.basePlaybackSpeed,
                isVisible = state.gestureState.showControls || state.isDraggingThumb,
                onSpeedSelected = controller::setBasePlaybackSpeed
            )

            if (controlsAlpha > 0.01f || state.isDraggingThumb) {
                val progressColor = MaterialTheme.colorScheme.primary
                val trackColor = Color.White.copy(alpha = 0.35f)
                val effectiveProgress = if (state.isDraggingThumb) state.thumbDragProgress else state.progress
                val barHeightPx = with(density) { PROGRESS_BAR_HEIGHT.toPx() }
                val thumbRadiusPx = with(density) { THUMB_RADIUS.toPx() }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PROGRESS_BAR_TOUCH_HEIGHT)
                        .onSizeChanged { controller.setBarWidth(it.width.toFloat()) }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    controller.beginThumbDrag()
                                },
                                onDragEnd = {
                                    controller.endThumbDrag(state.isPlaying)
                                },
                                onDragCancel = {
                                    controller.cancelThumbDrag(state.isPlaying)
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    controller.updateThumbDrag(dragAmount / size.width.toFloat())
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isDraggingThumb) {
                        val seekMs = if (state.duration > 0L) {
                            (state.thumbDragProgress * state.duration).toLong().coerceIn(0L, state.duration)
                        } else {
                            0L
                        }

                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset {
                                    val thumbX = (state.thumbDragProgress * state.barWidthPx)
                                        .coerceIn(thumbRadiusPx, state.barWidthPx - thumbRadiusPx)
                                    val labelWidth = 50.dp.toPx()
                                    val dx = (thumbX - state.barWidthPx / 2f).coerceIn(
                                        -state.barWidthPx / 2f + labelWidth / 2f,
                                        state.barWidthPx / 2f - labelWidth / 2f
                                    )
                                    IntOffset(dx.roundToInt(), 0)
                                },
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = formatTime(seekMs),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PROGRESS_BAR_TOUCH_HEIGHT)
                            .graphicsLayer(alpha = controlsAlpha)
                    ) {
                        val lineY = size.height / 2f

                        drawLine(
                            color = trackColor,
                            start = Offset(0f, lineY),
                            end = Offset(size.width, lineY),
                            strokeWidth = barHeightPx
                        )

                        val progressWidth = size.width * effectiveProgress.coerceIn(0f, 1f)
                        if (progressWidth > 0f) {
                            drawLine(
                                color = progressColor,
                                start = Offset(0f, lineY),
                                end = Offset(progressWidth, lineY),
                                strokeWidth = barHeightPx
                            )
                        }

                        val thumbX = progressWidth.coerceIn(thumbRadiusPx, size.width - thumbRadiusPx)
                        drawCircle(
                            color = progressColor,
                            radius = thumbRadiusPx,
                            center = Offset(thumbX, lineY)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedButtonRow(
    baseSpeed: Float,
    isVisible: Boolean,
    onSpeedSelected: (Float) -> Unit
) {
    val context = LocalContext.current
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.6f,
        animationSpec = tween(200),
        label = "speedButtonsAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = alpha)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SPEED_OPTIONS.forEach { speed ->
            val label = speedLabel(context, speed)
            val isSelected = baseSpeed == speed
            val contentDescriptionRes = when (speed) {
                0.25f -> R.string.motion_video_speed_btn_025
                0.5f -> R.string.motion_video_speed_btn_05
                1f -> R.string.motion_video_speed_btn_1
                1.25f -> R.string.motion_video_speed_btn_125
                1.5f -> R.string.motion_video_speed_btn_15
                else -> R.string.motion_video_speed_btn_1
            }

            Surface(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .semantics {
                        contentDescription = context.getString(contentDescriptionRes)
                    }
                    .clickable { onSpeedSelected(speed) },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                }
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

internal fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun speedLabel(context: Context, speed: Float): String {
    val labelRes = when (speed) {
        0.1f -> R.string.playback_speed_label_01
        0.25f -> R.string.playback_speed_label_025
        0.5f -> R.string.playback_speed_label_05
        1f -> R.string.playback_speed_label_1
        1.25f -> R.string.playback_speed_label_125
        1.5f -> R.string.playback_speed_label_15
        else -> R.string.playback_speed_label_1
    }

    return context.getString(labelRes)
}
