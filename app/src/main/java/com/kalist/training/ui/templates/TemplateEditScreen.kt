package com.kalist.training.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kalist.training.R
import com.kalist.training.data.db.Equipment
import com.kalist.training.data.db.Exercise
import com.kalist.training.data.db.TargetType
import com.kalist.training.ui.components.BackButton
import com.kalist.training.ui.components.NumberStepper
import com.kalist.training.ui.components.equipmentIcon
import com.kalist.training.ui.components.equipmentLabel
import com.kalist.training.ui.components.formatGoal
import com.kalist.training.ui.components.targetTypeLabel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TemplateEditScreen(
    onDone: () -> Unit,
    viewModel: TemplateEditViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    var pickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.saved.collect { onDone() } }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to -> viewModel.move(from.key, to.key) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onDone) },
                title = { Text(stringResource(if (viewModel.isNew) R.string.template_new else R.string.template_edit)) },
                actions = { TextButton(onClick = viewModel::save) { Text(stringResource(R.string.action_save)) } },
            )
        },
    ) { padding ->
        if (!form.loaded) return@Scaffold
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "fields") { TemplateFields(form, viewModel) }
            item(key = "exercises-header") {
                Text(stringResource(R.string.template_exercises), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            items(form.items, key = { it.key }) { item ->
                ReorderableItem(reorderState, key = item.key) { dragging ->
                    val index = form.items.indexOf(item)
                    val moveUp = stringResource(R.string.action_move_up)
                    val moveDown = stringResource(R.string.action_move_down)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (dragging) Modifier.shadow(8.dp, MaterialTheme.shapes.medium) else Modifier)
                            .semantics {
                                customActions = buildList {
                                    if (index > 0) add(CustomAccessibilityAction(moveUp) { viewModel.moveBy(item.key, -1); true })
                                    if (index < form.items.lastIndex) add(CustomAccessibilityAction(moveDown) { viewModel.moveBy(item.key, 1); true })
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.DragHandle,
                                    contentDescription = stringResource(R.string.cd_reorder),
                                    modifier = Modifier.draggableHandle().size(48.dp).padding(12.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text("${index + 1}. ${item.exercise.name}", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        formatGoal(item.targetSets, item.targetMin, item.targetMax, item.exercise.targetType, item.exercise.perSide),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { viewModel.removeItem(item.key) }) {
                                    Icon(Icons.Filled.Close, stringResource(R.string.cd_remove_exercise, item.exercise.name))
                                }
                            }
                            val unit = if (item.exercise.targetType == TargetType.TIME) stringResource(R.string.unit_seconds_short) else null
                            val step = if (item.exercise.targetType == TargetType.TIME) 5 else 1
                            ItemStepper(stringResource(R.string.template_target_sets), item.targetSets, 1..10) { v -> viewModel.updateItem(item.key) { it.copy(targetSets = v) } }
                            ItemStepper(stringResource(R.string.template_target_min), item.targetMin, 0..600, step, unit) { v ->
                                viewModel.updateItem(item.key) { it.copy(targetMin = v, targetMax = maxOf(v, it.targetMax)) }
                            }
                            ItemStepper(stringResource(R.string.template_target_max), item.targetMax, 0..600, step, unit) { v ->
                                viewModel.updateItem(item.key) { it.copy(targetMax = v, targetMin = minOf(v, it.targetMin)) }
                            }
                            OutlinedTextField(
                                value = item.note,
                                onValueChange = { v -> viewModel.updateItem(item.key) { it.copy(note = v) } },
                                label = { Text(stringResource(R.string.template_note)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            item(key = "add") {
                OutlinedButton(onClick = { pickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Text(stringResource(R.string.template_add_exercise), Modifier.padding(start = 8.dp))
                }
            }
            item(key = "save") {
                Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }

    if (pickerOpen) {
        val exercises by viewModel.pickableExercises.collectAsStateWithLifecycle()
        ExercisePickerSheet(
            exercises = exercises,
            onPick = { viewModel.addExercise(it); pickerOpen = false },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun ItemStepper(label: String, value: Int, range: IntRange, step: Int = 1, suffix: String? = null, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        NumberStepper(value = value, onValueChange = onChange, label = label, range = range, step = step, suffix = suffix, compact = true)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateFields(form: TemplateForm, viewModel: TemplateEditViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = form.name,
            onValueChange = { v -> viewModel.update { it.copy(name = v, showNameError = false) } },
            label = { Text(stringResource(R.string.template_name)) },
            isError = form.showNameError,
            supportingText = if (form.showNameError) {{ Text(stringResource(R.string.error_name_required)) }} else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.description,
            onValueChange = { v -> viewModel.update { it.copy(description = v) } },
            label = { Text(stringResource(R.string.template_description)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.template_color), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TemplateColors.forEachIndexed { index, color ->
                val selected = color == form.colorArgb
                val label = stringResource(R.string.cd_color_n, index + 1)
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .border(if (selected) 3.dp else 0.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                        .clickable(role = Role.RadioButton) { viewModel.update { it.copy(colorArgb = color) } }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) { if (selected) Icon(Icons.Filled.Check, null, tint = Color.White) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.template_rounds), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            NumberStepper(form.rounds, viewModel::setRounds, stringResource(R.string.template_rounds), range = 1..10, compact = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.template_rest), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            NumberStepper(
                form.restSeconds, { v -> viewModel.update { it.copy(restSeconds = v) } }, stringResource(R.string.template_rest),
                range = 0..600, step = 15, suffix = stringResource(R.string.unit_seconds_short), compact = true,
            )
        }
        Row(
            Modifier.fillMaxWidth().toggleable(form.inRotation, role = Role.Switch) { v -> viewModel.update { it.copy(inRotation = v) } },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.template_in_rotation), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.template_in_rotation_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = form.inRotation, onCheckedChange = null)
        }
        HorizontalDivider()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExercisePickerSheet(exercises: List<Exercise>, onPick: (Exercise) -> Unit, onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf<Equipment?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.template_add_exercise), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(R.string.filter_all)) })
            Equipment.entries.forEach { eq ->
                FilterChip(selected = filter == eq, onClick = { filter = if (filter == eq) null else eq }, label = { Text(equipmentLabel(eq)) })
            }
        }
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            items(exercises.filter { filter == null || it.equipment == filter }, key = { it.id }) { exercise ->
                ListItem(
                    modifier = Modifier.clickable { onPick(exercise) },
                    headlineContent = { Text(exercise.name) },
                    supportingContent = { Text(targetTypeLabel(exercise.targetType) + " · " + equipmentLabel(exercise.equipment)) },
                    leadingContent = { Icon(equipmentIcon(exercise.equipment), null) },
                )
            }
        }
    }
}
