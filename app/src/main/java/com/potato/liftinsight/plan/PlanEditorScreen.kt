package com.potato.liftinsight.plan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.potato.liftinsight.R
import com.potato.liftinsight.home.controller.PlanEditorState
import com.potato.liftinsight.plan.model.PlanMotionState
import com.potato.liftinsight.plan.model.TrainingPlanState
import com.potato.liftinsight.plan.model.normalizedPlanCurrentIndex
import com.potato.liftinsight.plan.model.sortPlansByLastApplied

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PlanPickerScreen(
    plans: List<TrainingPlanState>,
    currentPlanId: Int,
    onBack: () -> Unit,
    onSelectPlan: (Int) -> Unit,
    onSelectPlanDay: (Int, Int) -> Unit,
    onEditPlan: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showContent by remember { mutableStateOf(false) }
    val displayPlans = remember(plans, currentPlanId) {
        val sortedPlans = sortPlansByLastApplied(plans)
        val currentPlan = sortedPlans.firstOrNull { plan -> plan.id == currentPlanId }

        if (currentPlan == null) {
            sortedPlans
        } else {
            buildList {
                add(currentPlan)
                addAll(sortedPlans.filterNot { plan -> plan.id == currentPlanId })
            }
        }
    }
    val currentPlan = remember(plans, currentPlanId) {
        plans.firstOrNull { plan -> plan.id == currentPlanId }
    }

    LaunchedEffect(Unit) {
        showContent = true
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.plan_manage_top_bar_title))
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                end = 24.dp,
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "selectedPlan") {
                AnimatedPlanSection(visible = showContent) {
                    SelectedPlanDayCard(
                        plan = currentPlan,
                        onSelectDay = { dayIndex ->
                            if (currentPlan != null) {
                                onSelectPlanDay(currentPlan.id, dayIndex)
                            }
                        }
                    )
                }
            }

            if (displayPlans.isEmpty()) {
                item(key = "emptyPlans") {
                    AnimatedPlanSection(visible = showContent) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(28.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.plan_no_plans_available),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = displayPlans,
                    key = { _, plan -> plan.id }
                ) { _, plan ->
                    AnimatedPlanSection(visible = showContent) {
                        PlanChoiceRow(
                            plan = plan,
                            isCurrent = plan.id == currentPlanId,
                            onSelectPlan = { onSelectPlan(plan.id) },
                            onEditPlan = { onEditPlan(plan.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PlanEditorScreen(
    editor: PlanEditorState,
    onBack: () -> Unit,
    onUpdateTitle: (String) -> Unit,
    onUpdateCyclePeriod: (Int?) -> Unit,
    onSelectDay: (Int) -> Unit,
    onMoveMotionUp: (motionEntryId: Int) -> Unit,
    onMoveMotionDown: (motionEntryId: Int) -> Unit,
    onEditMotion: (motionEntryId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var draftPlanName by remember(editor.planId, editor.title) { mutableStateOf(editor.title) }
    var showCyclePeriodDialog by remember { mutableStateOf(false) }
    var draftCyclePeriodText by remember(editor.planId, editor.cyclePeriod) {
        mutableStateOf(editor.cyclePeriod?.toString().orEmpty())
    }
    val motionsForSelectedDay = remember(editor.selectedDayIndex, editor.motions) {
        val selectedDayIndex = editor.selectedDayIndex

        if (selectedDayIndex == null) {
            emptyList()
        } else {
            editor.motions.filter { motion -> motion.dayIndex == selectedDayIndex }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = {
                Text(text = stringResource(R.string.plan_rename_dialog_title))
            },
            text = {
                OutlinedTextField(
                    value = draftPlanName,
                    onValueChange = { draftPlanName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = editor.titleError,
                    label = {
                        Text(text = stringResource(R.string.plan_title_label))
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateTitle(draftPlanName)
                        showRenameDialog = false
                    }
                ) {
                    Text(text = stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showCyclePeriodDialog) {
        AlertDialog(
            onDismissRequest = { showCyclePeriodDialog = false },
            title = {
                Text(text = stringResource(R.string.plan_cycle_period_label))
            },
            text = {
                OutlinedTextField(
                    value = draftCyclePeriodText,
                    onValueChange = { value ->
                        draftCyclePeriodText = value.filter { character -> character.isDigit() }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = editor.cyclePeriodError,
                    label = {
                        Text(text = stringResource(R.string.plan_cycle_period_label))
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateCyclePeriod(draftCyclePeriodText.toIntOrNull())
                        showCyclePeriodDialog = false
                    }
                ) {
                    Text(text = stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCyclePeriodDialog = false }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (editor.isNewPlan) {
                                R.string.plan_create_top_bar_title
                            } else {
                                R.string.plan_detail_top_bar_title
                            }
                        )
                    )
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                end = 24.dp,
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "planTitle") {
                PlanTitleRow(
                    title = editor.title,
                    placeholder = stringResource(R.string.plan_title_placeholder),
                    isError = editor.titleError,
                    errorText = stringResource(R.string.plan_title_required_error),
                    onEdit = {
                        draftPlanName = editor.title
                        showRenameDialog = true
                    }
                )
            }

            item(key = "cyclePeriod") {
                PlanCyclePeriodRow(
                    cyclePeriod = editor.cyclePeriod,
                    isError = editor.cyclePeriodError,
                    onEdit = {
                        draftCyclePeriodText = editor.cyclePeriod?.toString().orEmpty()
                        showCyclePeriodDialog = true
                    }
                )
            }

            if (editor.cyclePeriod != null) {
                item(key = "daySelector") {
                    PlanDaySelectorCard(
                        cyclePeriod = editor.cyclePeriod,
                        selectedDayIndex = editor.selectedDayIndex,
                        onSelectDay = onSelectDay
                    )
                }
            }

            item(key = "motionsTitle") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.plan_detail_motions_section_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = stringResource(R.string.plan_detail_motions_section_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (editor.cyclePeriod == null) {
                item(key = "missingCycle") {
                    PlanEditorEmptyMessage(
                        text = stringResource(R.string.plan_set_cycle_period_first),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else if (editor.selectedDayIndex == null) {
                item(key = "missingDay") {
                    PlanEditorEmptyMessage(
                        text = stringResource(R.string.plan_select_day_to_manage_motions),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else if (motionsForSelectedDay.isEmpty()) {
                item(key = "emptyMotions") {
                    PlanEditorEmptyMessage(
                        text = stringResource(
                            R.string.plan_detail_empty_motions_for_day,
                            editor.selectedDayIndex
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                itemsIndexed(
                    items = motionsForSelectedDay,
                    key = { _, motion -> motion.entryId }
                ) { index, motion ->
                    PlanMotionRow(
                        motion = motion,
                        isMoveUpEnabled = index > 0,
                        isMoveDownEnabled = index < motionsForSelectedDay.lastIndex,
                        onMoveUp = { onMoveMotionUp(motion.entryId) },
                        onMoveDown = { onMoveMotionDown(motion.entryId) },
                        onEdit = { onEditMotion(motion.entryId) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MotionDetailScreen(
    planName: String,
    motion: PlanMotionState,
    onBack: () -> Unit,
    onSubmitMotion: (Int, Int, Double) -> Unit,
    onDeleteMotion: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draftSets by remember(motion.entryId) { mutableIntStateOf(motion.sets) }
    var draftRepsPerSet by remember(motion.entryId) { mutableIntStateOf(motion.repsPerSet) }
    var draftWeight by remember(motion.entryId) { mutableStateOf(motion.weight) }

    var showWeightDialog by remember(motion.entryId, motion.weight) { mutableStateOf(false) }
    var draftWeightText by remember(motion.entryId, motion.weight) {
        mutableStateOf(formatMotionWeightInput(motion.weight))
    }
    var weightError by remember(motion.entryId) { mutableStateOf(false) }

    if (showWeightDialog) {
        AlertDialog(
            onDismissRequest = {
                showWeightDialog = false
                weightError = false
            },
            title = {
                Text(text = stringResource(R.string.motion_detail_weight_dialog_title))
            },
            text = {
                OutlinedTextField(
                    value = draftWeightText,
                    onValueChange = { typed ->
                        draftWeightText = sanitizeDecimalInput(typed)
                        weightError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = weightError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = {
                        Text(text = stringResource(R.string.motion_detail_weight_label))
                    },
                    supportingText = {
                        if (weightError) {
                            Text(text = stringResource(R.string.motion_detail_weight_invalid_error))
                        }
                    },
                    suffix = {
                        Text(text = stringResource(R.string.body_unit_kg))
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val normalizedWeight = draftWeightText
                            .trim()
                            .takeIf { value -> value.isNotEmpty() }
                            ?.toDoubleOrNull()
                            ?: 0.0

                        if (normalizedWeight < 0.0) {
                            weightError = true
                            return@TextButton
                        }

                        draftWeight = normalizedWeight
                        showWeightDialog = false
                        weightError = false
                    }
                ) {
                    Text(text = stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showWeightDialog = false
                        weightError = false
                    }
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = planName)
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
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        onSubmitMotion(draftSets, draftRepsPerSet, draftWeight)
                        onBack()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.motion_submit_action)
                    )
                }

                SmallFloatingActionButton(
                    onClick = onDeleteMotion,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.motion_detail_delete_label)
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                end = 24.dp,
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "motionTitle") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.motion_detail_title_label),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                        )

                        Text(
                            text = motion.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            item(key = "sets") {
                MotionValueRow(
                    label = stringResource(R.string.motion_detail_sets_label),
                    value = draftSets,
                    onDecrease = { if (draftSets > 1) draftSets-- },
                    onIncrease = { draftSets++ }
                )
            }

            item(key = "reps") {
                MotionValueRow(
                    label = stringResource(R.string.motion_detail_reps_per_set_label),
                    value = draftRepsPerSet,
                    onDecrease = { if (draftRepsPerSet > 1) draftRepsPerSet-- },
                    onIncrease = { draftRepsPerSet++ }
                )
            }

            item(key = "weight") {
                MotionWeightRow(
                    label = stringResource(R.string.motion_detail_weight_label),
                    value = if (draftWeight <= 0.0) {
                        stringResource(R.string.motion_detail_weight_not_set)
                    } else {
                        stringResource(
                            R.string.motion_detail_weight_value,
                            formatMotionWeightDisplay(draftWeight)
                        )
                    },
                    onEdit = {
                        draftWeightText = formatMotionWeightInput(draftWeight)
                        weightError = false
                        showWeightDialog = true
                    }
                )
            }

        }
    }
}

@Composable
private fun MotionWeightRow(
    label: String,
    value: String,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
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
                .padding(start = 20.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.motion_detail_edit_weight_content_description)
                )
            }
        }
    }
}

@Composable
private fun PlanTitleRow(
    title: String,
    placeholder: String,
    isError: Boolean,
    errorText: String,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            width = 1.dp,
            color = borderColor
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 18.dp, end = 8.dp, bottom = if (isError) 8.dp else 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.plan_title_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    Text(
                        text = title.ifBlank { placeholder },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (title.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.plan_edit_title_content_description)
                    )
                }
            }

            if (isError) {
                Text(
                    text = errorText,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun PlanCyclePeriodRow(
    cyclePeriod: Int?,
    isError: Boolean,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            width = 1.dp,
            color = borderColor
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 18.dp, end = 8.dp, bottom = if (isError) 8.dp else 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.plan_cycle_period_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    Text(
                        text = if (cyclePeriod == null) {
                            stringResource(R.string.plan_cycle_period_placeholder)
                        } else {
                            stringResource(R.string.plan_cycle_period_value, cyclePeriod)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (cyclePeriod == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Rounded.EventRepeat,
                        contentDescription = stringResource(R.string.plan_edit_cycle_period_content_description)
                    )
                }
            }

            if (isError) {
                Text(
                    text = stringResource(R.string.plan_cycle_period_required_error),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanDaySelectorCard(
    cyclePeriod: Int,
    selectedDayIndex: Int?,
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.plan_day_picker_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
            )

            Text(
                text = if (selectedDayIndex == null) {
                    stringResource(R.string.plan_day_picker_placeholder)
                } else {
                    stringResource(R.string.plan_today_day_label, selectedDayIndex)
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (dayIndex in 1..cyclePeriod) {
                    val isSelected = dayIndex == selectedDayIndex

                    Surface(
                        modifier = Modifier.clickable { onSelectDay(dayIndex) },
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            }
                        )
                    ) {
                        Text(
                            text = dayIndex.toString(),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
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
    }
}

@Composable
private fun AnimatedPlanSection(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(),
        exit = androidx.compose.animation.ExitTransition.None
    ) {
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedPlanDayCard(
    plan: TrainingPlanState?,
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.plan_current_plan_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
            )

            Text(
                text = plan?.name ?: stringResource(R.string.plan_no_current_plan_selected),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            if (plan == null) {
                Text(
                    text = stringResource(R.string.plan_select_plan_to_set_day),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(
                            R.string.plan_day_of_cycle,
                            normalizedPlanCurrentIndex(plan),
                            plan.cyclePeriod
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (dayIndex in 1..plan.cyclePeriod) {
                            val isSelected = dayIndex == normalizedPlanCurrentIndex(plan)

                            Surface(
                                modifier = Modifier.clickable { onSelectDay(dayIndex) },
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    }
                                )
                            ) {
                                Text(
                                    text = dayIndex.toString(),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
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
            }
        }
    }
}

@Composable
private fun PlanChoiceRow(
    plan: TrainingPlanState,
    isCurrent: Boolean,
    onSelectPlan: () -> Unit,
    onEditPlan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelectPlan),
        color = if (isCurrent) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isCurrent) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = stringResource(
                        R.string.plan_day_of_cycle,
                        normalizedPlanCurrentIndex(plan),
                        plan.cyclePeriod
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            IconButton(onClick = onEditPlan) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(
                        R.string.plan_edit_content_description,
                        plan.name
                    )
                )
            }
        }
    }
}

@Composable
private fun PlanMotionRow(
    motion: PlanMotionState,
    isMoveUpEnabled: Boolean,
    isMoveDownEnabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
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
                .padding(start = 8.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                IconButton(
                    onClick = onMoveUp,
                    enabled = isMoveUpEnabled
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = stringResource(
                            R.string.plan_move_motion_up_content_description,
                            motion.title
                        )
                    )
                }

                IconButton(
                    onClick = onMoveDown,
                    enabled = isMoveDownEnabled
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(
                            R.string.plan_move_motion_down_content_description,
                            motion.title
                        )
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = motion.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = stringResource(
                        R.string.plan_motion_day_summary,
                        motion.dayIndex,
                        motion.sets,
                        motion.repsPerSet
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(
                        R.string.plan_edit_motion_content_description,
                        motion.title
                    )
                )
            }
        }
    }
}

@Composable
private fun MotionValueRow(
    label: String,
    value: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
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
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onDecrease,
                    enabled = value > 1
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = stringResource(
                            R.string.motion_detail_decrease_value_content_description,
                            label
                        )
                    )
                }

                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )

                IconButton(onClick = onIncrease) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(
                            R.string.motion_detail_increase_value_content_description,
                            label
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )

            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun PlanEditorEmptyMessage(
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

private fun formatMotionWeightInput(weight: Double): String {
    if (weight <= 0.0) {
        return ""
    }

    return formatMotionWeightDisplay(weight)
}

private fun formatMotionWeightDisplay(weight: Double): String {
    val roundedWeight = ((weight * 100.0).toInt()) / 100.0

    if (roundedWeight == roundedWeight.toInt().toDouble()) {
        return roundedWeight.toInt().toString()
    }

    return roundedWeight.toString().trimEnd('0').trimEnd('.')
}

private fun sanitizeDecimalInput(input: String): String {
    val normalizedInput = input.replace(',', '.')
    val builder = StringBuilder()
    var hasDecimalSeparator = false

    normalizedInput.forEach { character ->
        if (character.isDigit()) {
            builder.append(character)
            return@forEach
        }

        if (character == '.' && !hasDecimalSeparator) {
            if (builder.isEmpty()) {
                builder.append('0')
            }

            builder.append(character)
            hasDecimalSeparator = true
        }
    }

    return builder.toString()
}


