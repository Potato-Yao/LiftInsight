package com.potato.liftinsight.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import com.potato.liftinsight.R
import com.potato.liftinsight.motion.model.MotionEditorMessage
import com.potato.liftinsight.motion.model.MotionEditorState
import com.potato.liftinsight.motion.model.MotionRowState
import com.potato.liftinsight.motion.model.MotionSectionState
import com.potato.liftinsight.motion.model.MotionState
import com.potato.liftinsight.ui.theme.LiftInsightMotion
import com.potato.liftinsight.ui.theme.LiftInsightTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MotionScreen(
    state: MotionState,
    onAddMotion: () -> Unit,
    onEditMotion: (Int) -> Unit,
    onBackFromEditor: () -> Unit,
    onMotionNameChange: (String) -> Unit,
    onSubmitMotion: () -> Unit,
    onDeleteMotion: () -> Unit,
    modifier: Modifier = Modifier,
    selectionTitle: String? = null,
    onBackFromSelection: (() -> Unit)? = null,
    onSelectMotion: ((Int) -> Unit)? = null
) {
    val editor = state.editor

    if (editor == null) {
        if (selectionTitle != null && onBackFromSelection != null && onSelectMotion != null) {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(text = selectionTitle)
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackFromSelection) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.common_back)
                                )
                            }
                        }
                    )
                }
            ) { innerPadding ->
                MotionLibraryList(
                    sections = state.sections,
                    onEditMotion = onEditMotion,
                    onSelectMotion = onSelectMotion,
                    isSelectionMode = true,
                    modifier = Modifier.padding(innerPadding)
                )
            }

            return
        }

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                FloatingActionButton(onClick = onAddMotion) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.motion_add_content_description)
                    )
                }
            }
        ) { innerPadding ->
            MotionLibraryList(
                sections = state.sections,
                onEditMotion = onEditMotion,
                onSelectMotion = null,
                isSelectionMode = false,
                modifier = Modifier.padding(innerPadding)
            )
        }

        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            MotionEditorActionButtons(
                isNewMotion = editor.isNewMotion,
                onSubmitMotion = onSubmitMotion,
                onDeleteMotion = onDeleteMotion
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (editor.isNewMotion) {
                                R.string.motion_create_top_bar_title
                            } else {
                                R.string.motion_edit_top_bar_title
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackFromEditor) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        MotionEditorContent(
            editor = editor,
            onMotionNameChange = onMotionNameChange,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
private fun MotionEditorActionButtons(
    isNewMotion: Boolean,
    onSubmitMotion: () -> Unit,
    onDeleteMotion: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!isNewMotion) {
            SmallFloatingActionButton(
                onClick = onDeleteMotion,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.motion_delete_action)
                )
            }
        }

        FloatingActionButton(onClick = onSubmitMotion) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.motion_submit_action)
            )
        }
    }
}

@Composable
private fun MotionLibraryList(
    sections: List<MotionSectionState>,
    onEditMotion: (Int) -> Unit,
    onSelectMotion: ((Int) -> Unit)?,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier
) {
    var showContent by remember { mutableStateOf(false) }
    val motionCount = sections.sumOf { section -> section.motions.size }

    LaunchedEffect(Unit) {
        showContent = true
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp,
            top = 12.dp,
            end = 24.dp,
            bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "motionTitle") {
            AnimatedVisibility(
                visible = showContent,
                enter = motionSectionEnter(delayMillis = 0),
                exit = ExitTransition.None
            ) {
                Text(
                    text = stringResource(R.string.nav_motion),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        item(key = "motionSummary") {
            AnimatedVisibility(
                visible = showContent,
                enter = motionSectionEnter(delayMillis = 50),
                exit = ExitTransition.None
            ) {
                MotionSummaryCard(
                    motionCount = motionCount,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item(key = "motionListHeader") {
            AnimatedVisibility(
                visible = showContent,
                enter = motionSectionEnter(delayMillis = 100),
                exit = ExitTransition.None
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.motion_library_section_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = stringResource(R.string.motion_library_section_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (sections.isEmpty()) {
            item(key = "emptyMotions") {
                AnimatedVisibility(
                    visible = showContent,
                    enter = motionSectionEnter(delayMillis = 135),
                    exit = ExitTransition.None
                ) {
                    EmptyMotionMessage(
                        text = stringResource(R.string.motion_list_empty_state),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            return@LazyColumn
        }

        var delayMillis = 135
        sections.forEach { section ->
            item(key = "section-${section.label}") {
                AnimatedVisibility(
                    visible = showContent,
                    enter = motionSectionEnter(delayMillis = delayMillis),
                    exit = ExitTransition.None
                ) {
                    Text(
                        text = section.label,
                        modifier = Modifier.padding(start = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            delayMillis += 35

            items(
                items = section.motions,
                key = { motion -> motion.id }
            ) { motion ->
                AnimatedVisibility(
                    visible = showContent,
                    enter = motionSectionEnter(delayMillis = delayMillis),
                    exit = ExitTransition.None
                ) {
                    MotionRow(
                        motion = motion,
                        onEdit = { onEditMotion(motion.id) },
                        onSelect = if (onSelectMotion == null) {
                            null
                        } else {
                            { onSelectMotion(motion.id) }
                        },
                        isSelectionMode = isSelectionMode
                    )
                }

                delayMillis += 35
            }
        }
    }
}

@Composable
private fun MotionSummaryCard(
    motionCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.motion_library_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                )

                Text(
                    text = stringResource(R.string.motion_library_count, motionCount),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Text(
                    text = stringResource(R.string.motion_library_card_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                )
            }

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
    }
}

@Composable
private fun MotionRow(
    motion: MotionRowState,
    onEdit: () -> Unit,
    onSelect: (() -> Unit)?,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onSelect != null, onClick = { onSelect?.invoke() }),
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
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

            Text(
                text = motion.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (isSelectionMode) {
                Text(
                    text = stringResource(R.string.motion_choose_action),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(
                            R.string.motion_edit_content_description,
                            motion.name
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun MotionEditorContent(
    editor: MotionEditorState,
    onMotionNameChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(editor.motionId) {
        showContent = true
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp,
            top = 12.dp,
            end = 24.dp,
            bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "editorTitle") {
            AnimatedVisibility(
                visible = showContent,
                enter = motionSectionEnter(delayMillis = 0),
                exit = ExitTransition.None
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(
                            if (editor.isNewMotion) {
                                R.string.motion_create_top_bar_title
                            } else {
                                R.string.motion_edit_top_bar_title
                            }
                        ),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = stringResource(R.string.motion_editor_section_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item(key = "motionName") {
            AnimatedVisibility(
                visible = showContent,
                enter = motionSectionEnter(delayMillis = 50),
                exit = ExitTransition.None
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.motion_editor_name_label),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                        )

                        OutlinedTextField(
                            value = editor.name,
                            onValueChange = onMotionNameChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = editor.message != null,
                            label = {
                                Text(text = stringResource(R.string.motion_editor_name_label))
                            },
                            supportingText = {
                                val message = editor.message ?: return@OutlinedTextField

                                Text(text = motionEditorMessageText(message))
                            }
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun EmptyMotionMessage(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun motionEditorMessageText(message: MotionEditorMessage): String {
    return when (message) {
        MotionEditorMessage.BlankName -> stringResource(R.string.motion_editor_blank_name_error)
        MotionEditorMessage.DuplicateName -> stringResource(R.string.motion_editor_duplicate_name_error)
        MotionEditorMessage.DeleteBlocked -> stringResource(R.string.motion_editor_delete_blocked_error)
        MotionEditorMessage.SaveFailed -> stringResource(R.string.motion_editor_save_error)
    }
}

private fun motionSectionEnter(delayMillis: Int): EnterTransition {
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

@Preview(showBackground = true)
@Composable
private fun MotionLibraryPreview() {
    LiftInsightTheme {
        MotionScreen(
            state = MotionState(
                motions = listOf(
                    MotionRowState(id = 1, name = "Back Squat"),
                    MotionRowState(id = 2, name = "Bench Press"),
                    MotionRowState(id = 3, name = "Clean Pull"),
                    MotionRowState(id = 4, name = "Snatch")
                ),
                sections = listOf(
                    MotionSectionState(
                        label = "B",
                        motions = listOf(
                            MotionRowState(id = 1, name = "Back Squat"),
                            MotionRowState(id = 2, name = "Bench Press")
                        )
                    ),
                    MotionSectionState(
                        label = "C",
                        motions = listOf(
                            MotionRowState(id = 3, name = "Clean Pull")
                        )
                    ),
                    MotionSectionState(
                        label = "S",
                        motions = listOf(
                            MotionRowState(id = 4, name = "Snatch")
                        )
                    )
                )
            ),
            onAddMotion = {},
            onEditMotion = {},
            onBackFromEditor = {},
            onMotionNameChange = {},
            onSubmitMotion = {},
            onDeleteMotion = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MotionEditorPreview() {
    LiftInsightTheme {
        MotionScreen(
            state = MotionState(
                editor = MotionEditorState(
                    motionId = 1,
                    name = "Snatch",
                    message = MotionEditorMessage.DeleteBlocked
                )
            ),
            onAddMotion = {},
            onEditMotion = {},
            onBackFromEditor = {},
            onMotionNameChange = {},
            onSubmitMotion = {},
            onDeleteMotion = {}
        )
    }
}

