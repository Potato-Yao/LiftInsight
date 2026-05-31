package com.potato.liftinsight.record

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import android.net.Uri
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.potato.liftinsight.R
import com.potato.liftinsight.motion.MotionVideoPlayer
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.training.data.MetaHistoryRecord
import com.potato.liftinsight.training.data.VideoProcessState
import com.potato.liftinsight.ui.theme.LiftInsightMotion
import com.potato.liftinsight.video.VideoProcessingStatus
import com.potato.liftinsight.video.VideoProcessor
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
    var records by remember { mutableStateOf<List<MetaHistoryRecord>>(emptyList()) }
    var selectedRecord by remember { mutableStateOf<MetaHistoryRecord?>(null) }
    var selectedVideoStatus by remember { mutableStateOf<VideoProcessingStatus?>(null) }
    var videoPlayerUri by remember { mutableStateOf<Uri?>(null) }
    var showContent by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        records = withContext(Dispatchers.IO) {
            trainingPlanStore.getMetaHistoryRecords()
        }
        showContent = true
    }

    LaunchedEffect(selectedRecord?.videoName) {
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
            onDismiss = { selectedRecord = null },
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
            }
        )
    }

    videoPlayerUri?.let { videoUri ->
        MotionVideoPlayer(
            videoUri = videoUri,
            onDismiss = { videoPlayerUri = null }
        )
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
    onDismiss: () -> Unit,
    onPlayVideo: () -> Unit
) {
    val hasVideo = !record.videoName.isNullOrBlank()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                DetailChip(
                    label = stringResource(R.string.training_detail_video_status),
                    value = processState,
                    highlighted = hasVideo
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

            if (!hasVideo) {
                Text(
                    text = stringResource(R.string.training_video_unavailable_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    modifier: Modifier = Modifier
) {
    val containerColor = if (highlighted) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Surface(
        modifier = modifier,
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
        }
    }
}

private fun formatDateLabel(dateKey: String): String {
    val parts = dateKey.split("-")
    if (parts.size != 3) return dateKey
    val year = parts[0]
    val month = parts[1].toIntOrNull() ?: return dateKey
    val day = parts[2].toIntOrNull() ?: return dateKey
    val months = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    val monthName = months.getOrElse(month - 1) { month.toString() }
    return "$monthName $day, $year"
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

    return stringResource(R.string.training_process_state_no)
}
