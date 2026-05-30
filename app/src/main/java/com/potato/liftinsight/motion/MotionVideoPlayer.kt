package com.potato.liftinsight.motion

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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.potato.liftinsight.R
import com.potato.liftinsight.motion.model.MotionVideoGestureState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Constants ────────────────────────────────────────────────────

/** Fraction of screen width that maps to full-duration seek during swipe */
private const val SWIPE_SEEK_FACTOR = 2.0f

/** Duration in ms before long-press triggers speed override */
private const val LONG_PRESS_THRESHOLD_MS = 400L

/** Duration in ms the play/pause icon stays visible after a double-tap */
private const val PLAY_PAUSE_ICON_DURATION_MS = 500L

/** Maximum interval between two taps to count as a double-tap */
private const val DOUBLE_TAP_WINDOW_MS = 300L

/** Auto-hide delay in ms after last interaction (close button, progress bar) */
private const val CONTROLS_HIDE_DELAY_MS = 2000L

/** Polling interval for position updates */
private const val POLLING_INTERVAL_MS = 100L

/** Progress bar line height in dp */
private val PROGRESS_BAR_HEIGHT = 3.dp

/** Progress bar touchable (draggable) height in dp */
private val PROGRESS_BAR_TOUCH_HEIGHT = 40.dp

/** Thumb radius in dp */
private val THUMB_RADIUS = 6.dp

/** Fraction of video height for the top speed-zone (0.5×) */
private const val TOP_ZONE_FRACTION = 0.40f

/** Fraction of video height for the bottom speed-zone (1.5×) */
private const val BOTTOM_ZONE_FRACTION = 0.40f

/** Minimum fraction of screen height for a vertical swipe to trigger next/prev */
private const val VERTICAL_SWIPE_THRESHOLD_FRACTION = 0.20f

/** Available playback speed options for the button row */
private val SPEED_OPTIONS = listOf(0.25f, 0.5f, 1f, 1.25f, 1.5f)

// ── Public API ───────────────────────────────────────────────────

/**
 * Gesture-rich video player built on Media3 ExoPlayer.
 *
 * ### Gesture interactions
 *
 * | Gesture                  | Behaviour                                                   |
 * |--------------------------|-------------------------------------------------------------|
 * | Single tap               | Toggle play/pause; large icon overlay fades after 0.5 s     |
 * | Horizontal swipe         | Seek through video; translucent overlay shows target time   |
 * | Vertical swipe           | Switch next/previous video (if callbacks provided)          |
 * | Long-press top 40 %      | Play at 0.5× speed while holding; badge near touch          |
 * | Long-press bottom 40 %   | Play at 1.5× speed while holding; badge near touch          |
 * | Speed button tap         | Set persistent playback speed; highlight active button      |
 * | Drag progress bar        | Real-time seek with floating timestamp above the thumb      |
 *
 * @param videoUri       content URI of the video to play (file://, content://, or https://).
 * @param onDismiss      called when the user taps the close button.
 * @param modifier       optional [Modifier] applied to the root [Box].
 * @param onNextVideo    optional callback when the user swipes up for next video.
 * @param onPreviousVideo optional callback when the user swipes down for previous video.
 */
@Composable
fun MotionVideoPlayer(
    videoUri: Uri,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onNextVideo: (() -> Unit)? = null,
    onPreviousVideo: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // ── Core player state ────────────────────────────────────────

    var isPlaying by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentPosition by remember { mutableLongStateOf(0L) }

    // ── Speed state ──────────────────────────────────────────────

    /** Speed set by the button row – persists after tap and after long-press ends. */
    var basePlaybackSpeed by remember { mutableFloatStateOf(1f) }

    /** Non-null while a long-press speed-zone gesture is active. Overrides [basePlaybackSpeed]. */
    var longPressSpeed by remember { mutableStateOf<Float?>(null) }

    /** The actual playback speed applied to the player. */
    val effectiveSpeed = longPressSpeed ?: basePlaybackSpeed

    // ── Gesture UI state ─────────────────────────────────────────

    var gestureState by remember { mutableStateOf(MotionVideoGestureState()) }

    // ── Double-tap detection ──────────────────────────────────────

    /** [SystemClock.uptimeMillis] of the previous tap, for double-tap detection. */
    var lastTapUptimeMs by remember { mutableLongStateOf(0L) }

    // ── Progress-bar drag state (separate for Canvas rendering) ──

    var thumbDragProgress by remember { mutableFloatStateOf(0f) }
    var isDraggingThumb by remember { mutableStateOf(false) }
    var barWidthPx by remember { mutableFloatStateOf(0f) }

    // ── ExoPlayer lifecycle ──────────────────────────────────────

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
        }
    }

    // Capture latest values for use inside gesture lambdas
    val updatedIsPlaying by rememberUpdatedState(isPlaying)
    val updatedProgress by rememberUpdatedState(progress)
    val updatedDuration by rememberUpdatedState(duration)
    val updatedCurrentPosition by rememberUpdatedState(currentPosition)
    val updatedLastTapUptimeMs by rememberUpdatedState(lastTapUptimeMs)
    // ── Keep player in sync with state ───────────────────────────

    LaunchedEffect(isPlaying) {
        player.playWhenReady = isPlaying
    }

    LaunchedEffect(effectiveSpeed) {
        player.setPlaybackSpeed(effectiveSpeed)
    }

    // ── Position polling ─────────────────────────────────────────

    LaunchedEffect(Unit) {
        while (true) {
            val stateOk = player.playbackState == Player.STATE_READY ||
                    player.playbackState == Player.STATE_BUFFERING

            if (stateOk && !isDraggingThumb && !gestureState.isSeeking && longPressSpeed == null) {
                duration = player.duration.coerceAtLeast(0L)
                currentPosition = player.currentPosition.coerceAtLeast(0L)
                if (duration > 0L) {
                    progress = currentPosition.toFloat() / duration.toFloat()
                }
                isPlaying = player.playWhenReady && player.playbackState == Player.STATE_READY
            }
            delay(POLLING_INTERVAL_MS)
        }
    }

    // ── Auto-hide controls ───────────────────────────────────────

    LaunchedEffect(gestureState.showControls, isDraggingThumb) {
        if (gestureState.showControls && !isDraggingThumb) {
            delay(CONTROLS_HIDE_DELAY_MS)
            gestureState = gestureState.copy(showControls = false)
        }
    }

    // ── Play/pause icon auto-dismiss ─────────────────────────────

    LaunchedEffect(gestureState.showPlayPauseIcon) {
        if (gestureState.showPlayPauseIcon) {
            delay(PLAY_PAUSE_ICON_DURATION_MS)
            gestureState = gestureState.copy(showPlayPauseIcon = false)
        }
    }

    // ── Cleanup ──────────────────────────────────────────────────

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    // ── Helpers ──────────────────────────────────────────────────

    fun showControlsTemporarily() {
        gestureState = gestureState.copy(showControls = true)
    }

    // ══════════════════════════════════════════════════════════════
    //  UI
    // ══════════════════════════════════════════════════════════════

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        // ── Video surface ────────────────────────────────────────

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Unified gesture layer ────────────────────────────────
        //
        //  All video-area gestures (tap, long-press, horizontal seek,
        //  vertical swipe) are handled in a single pointerInput block
        //  to resolve conflicts cleanly.
        //
        //  This layer sits *behind* buttons and the progress bar in
        //  z-order, so control touches are consumed first and never
        //  reach this handler.

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
                        var longPressSpeedValue: Float? = null
                        var localSeekProgress = 0f
                        var totalVerticalDragPx = 0f

                        val touchSlopPx = viewConfiguration.touchSlop.toFloat()

                        while (true) {
                            // Block until next pointer event, or long-press timeout fires.
                            // withTimeoutOrNull is available inside AwaitPointerEventScope.
                            val event = if (!gestureDetermined) {
                                withTimeoutOrNull(LONG_PRESS_THRESHOLD_MS) {
                                    awaitPointerEvent()
                                }
                            } else {
                                awaitPointerEvent()
                            }

                            if (event == null) {
                                // ── Long-press timeout fired ────────
                                gestureDetermined = true

                                val viewHeight = size.height.toFloat()
                                val relativeY = startPosition.y / viewHeight
                                longPressSpeedValue = when {
                                    relativeY <= TOP_ZONE_FRACTION -> 0.5f
                                    relativeY >= (1f - BOTTOM_ZONE_FRACTION) -> 1.5f
                                    else -> null
                                }

                                if (longPressSpeedValue != null) {
                                    isLongPress = true

                                    gestureState = gestureState.copy(
                                        isSpeedBoosting = true,
                                        boostSpeedLabel = speedLabel(longPressSpeedValue!!),
                                        boostOffsetY = startPosition.y
                                    )
                                    longPressSpeed = longPressSpeedValue
                                }

                                // Wait for more events (lift or move)
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
                                    // Movement detected – cancel long-press timer
                                    gestureDetermined = true

                                    if (absDx > absDy) {
                                        // Horizontal swipe → seek
                                        isHorizontalSwipe = true
                                        localSeekProgress = updatedProgress

                                        player.playWhenReady = false

                                        gestureState = gestureState.copy(
                                            isSeeking = true,
                                            seekProgress = localSeekProgress
                                        )
                                    } else {
                                        // Vertical swipe → next/prev video
                                        isVerticalSwipe = true
                                        totalVerticalDragPx = dy
                                    }
                                }
                            } else {
                                when {
                                    isHorizontalSwipe -> {
                                        change.consume()
                                        val width = size.width.toFloat()
                                        val delta = (change.position.x - lastPosition.x) /
                                                width * SWIPE_SEEK_FACTOR
                                        localSeekProgress =
                                            (localSeekProgress + delta).coerceIn(0f, 1f)

                                        val dur = updatedDuration
                                        val seekMs = if (dur > 0L) {
                                            (localSeekProgress * dur).toLong().coerceIn(0L, dur)
                                        } else 0L

                                        gestureState = gestureState.copy(
                                            seekProgress = localSeekProgress,
                                            seekTimeLabel = formatTime(seekMs)
                                        )

                                        if (dur > 0L) player.seekTo(seekMs)
                                    }
                                    isVerticalSwipe -> {
                                        change.consume()
                                        totalVerticalDragPx += change.position.y - lastPosition.y
                                    }
                                    isLongPress -> {
                                        change.consume()
                                        gestureState = gestureState.copy(
                                            boostOffsetY = change.position.y
                                        )
                                    }
                                }
                            }

                            lastPosition = change.position
                        }

                        // ── Gesture ended ────────────────────────

                        if (!gestureDetermined) {
                            // Distinguish single vs double tap:
                            //  - single tap → no playback action, just show controls
                            //  - double tap → toggle play/pause
                            val thisTapUptime = down.uptimeMillis
                            if (thisTapUptime - updatedLastTapUptimeMs < DOUBLE_TAP_WINDOW_MS) {
                                lastTapUptimeMs = 0L
                                val currentlyPlaying = player.playWhenReady
                                player.playWhenReady = !currentlyPlaying
                                isPlaying = !currentlyPlaying
                                gestureState = gestureState.copy(showPlayPauseIcon = true)
                            } else {
                                lastTapUptimeMs = thisTapUptime
                            }
                        }

                        if (isHorizontalSwipe) {
                            val dur = updatedDuration
                            if (dur > 0L) {
                                val seekTo = (localSeekProgress * dur).toLong()
                                    .coerceIn(0L, dur)
                                player.seekTo(seekTo)
                                currentPosition = seekTo
                            }
                            player.playWhenReady = updatedIsPlaying

                            gestureState = gestureState.copy(
                                isSeeking = false,
                                seekTimeLabel = ""
                            )
                        }

                        if (isVerticalSwipe) {
                            val thresholdPx =
                                size.height * VERTICAL_SWIPE_THRESHOLD_FRACTION
                            if (abs(totalVerticalDragPx) > thresholdPx) {
                                if (totalVerticalDragPx < 0f) {
                                    onNextVideo?.invoke()
                                } else {
                                    onPreviousVideo?.invoke()
                                }
                            }
                        }

                        if (isLongPress) {
                            longPressSpeed = null
                            gestureState = gestureState.copy(isSpeedBoosting = false)
                        }

                        showControlsTemporarily()
                    }
                }
        )

        // ── Close button ─────────────────────────────────────────

        AnimatedVisibility(
            visible = gestureState.showControls,
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

        // ── Seek overlay (during horizontal swipe) ───────────────

        AnimatedVisibility(
            visible = gestureState.isSeeking,
            enter = fadeIn(animationSpec = tween(100)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.65f)
            ) {
                Text(
                    text = gestureState.seekTimeLabel.ifEmpty { "00:00" },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        // ── Play / pause icon overlay (centre tap) ───────────────

        AnimatedVisibility(
            visible = gestureState.showPlayPauseIcon,
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
                        imageVector = if (isPlaying) Icons.Rounded.Pause
                        else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) {
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

        // ── Speed boost badge (long-press on top/bottom zone) ────

        AnimatedVisibility(
            visible = gestureState.isSpeedBoosting,
            enter = fadeIn(animationSpec = tween(100)),
            exit = fadeOut(animationSpec = tween(150))
        ) {
            Surface(
                modifier = Modifier
                    .offset {
                        val badgeY =
                            (gestureState.boostOffsetY - with(density) { 48.dp.toPx() })
                                .roundToInt()
                                .coerceAtLeast(0)
                        IntOffset(
                            with(density) { 24.dp.roundToPx() },
                            badgeY
                        )
                    },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            ) {
                Text(
                    text = gestureState.boostSpeedLabel,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        // ── Bottom controls: speed buttons + progress bar ────────

        val controlsAlpha by animateFloatAsState(
            targetValue = if (gestureState.showControls || isDraggingThumb) 1f else 0f,
            animationSpec = tween(200),
            label = "controlsAlpha"
        )

        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            // Speed preset buttons – always tappable with conditional alpha
            SpeedButtonRow(
                baseSpeed = basePlaybackSpeed,
                isVisible = gestureState.showControls || isDraggingThumb,
                onSpeedSelected = { speed ->
                    basePlaybackSpeed = speed
                    showControlsTemporarily()
                }
            )

            // Interactive progress bar
            if (controlsAlpha > 0.01f || isDraggingThumb) {
                val progressColor = MaterialTheme.colorScheme.primary
                val trackColor = Color.White.copy(alpha = 0.35f)
                val effectiveProgress =
                    if (isDraggingThumb) thumbDragProgress else progress
                val barHeightPx = with(density) { PROGRESS_BAR_HEIGHT.toPx() }
                val thumbRadiusPx = with(density) { THUMB_RADIUS.toPx() }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PROGRESS_BAR_TOUCH_HEIGHT)
                        .onSizeChanged { barWidthPx = it.width.toFloat() }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    isDraggingThumb = true
                                    thumbDragProgress = updatedProgress
                                    player.playWhenReady = false
                                },
                                onDragEnd = {
                                    isDraggingThumb = false
                                    val dur = updatedDuration
                                    if (dur > 0L) {
                                        val seekTo = (thumbDragProgress * dur).toLong()
                                            .coerceIn(0L, dur)
                                        player.seekTo(seekTo)
                                        currentPosition = seekTo
                                        if (dur > 0L) {
                                            progress = seekTo.toFloat() / dur.toFloat()
                                        }
                                    }
                                    player.playWhenReady = updatedIsPlaying
                                    showControlsTemporarily()
                                },
                                onDragCancel = {
                                    isDraggingThumb = false
                                    player.playWhenReady = updatedIsPlaying
                                    showControlsTemporarily()
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()

                                    val dur = updatedDuration
                                    if (dur <= 0L) return@detectHorizontalDragGestures

                                    val width = size.width.toFloat()
                                    val delta = dragAmount / width
                                    thumbDragProgress = (thumbDragProgress + delta)
                                        .coerceIn(0f, 1f)

                                    val seekMs = (thumbDragProgress * dur).toLong()
                                        .coerceIn(0L, dur)
                                    player.seekTo(seekMs)
                                    currentPosition = seekMs
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Floating timestamp above the thumb
                    if (isDraggingThumb) {
                        val dur = updatedDuration
                        val seekMs = if (dur > 0L) {
                            (thumbDragProgress * dur).toLong().coerceIn(0L, dur)
                        } else 0L

                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset {
                                    val widthPx = barWidthPx
                                    val thumbX = (thumbDragProgress * widthPx)
                                        .coerceIn(thumbRadiusPx, widthPx - thumbRadiusPx)
                                    val labelWidth = 50.dp.toPx()
                                    val dx = (thumbX - widthPx / 2f).coerceIn(
                                        -widthPx / 2f + labelWidth / 2f,
                                        widthPx / 2f - labelWidth / 2f
                                    )
                                    IntOffset(dx.roundToInt(), 0)
                                },
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = formatTime(seekMs),
                                modifier = Modifier.padding(
                                    horizontal = 8.dp,
                                    vertical = 4.dp
                                ),
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

                        // Track
                        drawLine(
                            color = trackColor,
                            start = Offset(0f, lineY),
                            end = Offset(size.width, lineY),
                            strokeWidth = barHeightPx
                        )

                        // Filled progress
                        val progressWidth =
                            size.width * effectiveProgress.coerceIn(0f, 1f)
                        if (progressWidth > 0f) {
                            drawLine(
                                color = progressColor,
                                start = Offset(0f, lineY),
                                end = Offset(progressWidth, lineY),
                                strokeWidth = barHeightPx
                            )
                        }

                        // Thumb
                        val thumbX = progressWidth.coerceIn(
                            thumbRadiusPx,
                            size.width - thumbRadiusPx
                        )
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

// ── Speed button row ──────────────────────────────────────────────

@Composable
private fun SpeedButtonRow(
    baseSpeed: Float,
    isVisible: Boolean,
    onSpeedSelected: (Float) -> Unit
) {
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
            val label = speedLabel(speed)
            val isSelected = baseSpeed == speed

            val contentDescriptionRes = when (speed) {
                0.25f -> R.string.motion_video_speed_btn_025
                0.5f -> R.string.motion_video_speed_btn_05
                1f -> R.string.motion_video_speed_btn_1
                1.25f -> R.string.motion_video_speed_btn_125
                1.5f -> R.string.motion_video_speed_btn_15
                else -> R.string.motion_video_speed_btn_1
            }
            val contentDescription = stringResource(contentDescriptionRes)

            Surface(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .semantics { this.contentDescription = contentDescription }
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
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 10.dp),
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

// ── Formatting helpers ────────────────────────────────────────────

/**
 * Formats milliseconds as `mm:ss` or `HH:mm:ss` for videos >= 1 hour.
 */
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

/**
 * Returns a human-readable label for a playback speed multiplier.
 */
private fun speedLabel(speed: Float): String =
    if (speed == 1f) "1×" else "${speed}×"
