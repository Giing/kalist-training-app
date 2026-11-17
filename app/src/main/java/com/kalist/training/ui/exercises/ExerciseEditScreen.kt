package com.kalist.training.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kalist.training.R
import com.kalist.training.data.db.Equipment
import com.kalist.training.data.db.TargetType
import com.kalist.training.data.repo.RemovalResult
import com.kalist.training.ui.components.BackButton
import com.kalist.training.ui.components.ConfirmDialog
import com.kalist.training.ui.components.equipmentIcon
import com.kalist.training.ui.components.equipmentLabel
import com.kalist.training.ui.components.targetTypeLabel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditScreen(
    onDone: () -> Unit,
    onOpenHistory: (Long) -> Unit,
    viewModel: ExerciseEditViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val usedInSessions by viewModel.usedInSessions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ExerciseEditEvent.Saved -> onDone()
                is ExerciseEditEvent.Removed -> {
                    val msg = if (event.result == RemovalResult.ARCHIVED) R.string.exercise_archived_instead else R.string.exercise_deleted
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    onDone()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onDone) },
                title = { Text(stringResource(if (viewModel.isNew) R.string.exercise_new else R.string.exercise_edit)) },
                actions = {
                    if (!viewModel.isNew) {
                        IconButton(onClick = { onOpenHistory(viewModel.exerciseId) }) {
                            Icon(Icons.Outlined.ShowChart, stringResource(R.string.history_title))
                        }
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (form.archived) R.string.action_unarchive else R.string.action_archive)) },
                                onClick = { menuOpen = false; viewModel.setArchived(!form.archived) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                onClick = { menuOpen = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (form.archived) {
                Text(stringResource(R.string.exercise_archived_notice), color = MaterialTheme.colorScheme.tertiary)
            }
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v, showNameError = false) } },
                label = { Text(stringResource(R.string.exercise_name)) },
                isError = form.showNameError,
                supportingText = if (form.showNameError) {{ Text(stringResource(R.string.error_name_required)) }} else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.exercise_target_type), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TargetType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = form.targetType == type,
                        onClick = { viewModel.update { it.copy(targetType = type) } },
                        shape = SegmentedButtonDefaults.itemShape(index, TargetType.entries.size),
                    ) { Text(targetTypeLabel(type)) }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = form.perSide, role = Role.Switch) { v -> viewModel.update { it.copy(perSide = v) } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.exercise_per_side), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.exercise_per_side_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = form.perSide, onCheckedChange = null)
            }

            Text(stringResource(R.string.exercise_equipment), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Equipment.entries.forEach { eq ->
                    FilterChip(
                        selected = form.equipment == eq,
                        onClick = { viewModel.update { it.copy(equipment = eq) } },
                        label = { Text(equipmentLabel(eq)) },
                        leadingIcon = { Icon(equipmentIcon(eq), null) },
                    )
                }
            }

            OutlinedTextField(
                value = form.easierVariant,
                onValueChange = { v -> viewModel.update { it.copy(easierVariant = v) } },
                label = { Text(stringResource(R.string.exercise_easier)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.harderVariant,
                onValueChange = { v -> viewModel.update { it.copy(harderVariant = v) } },
                label = { Text(stringResource(R.string.exercise_harder)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> viewModel.update { it.copy(description = v) } },
                label = { Text(stringResource(R.string.exercise_description)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.exercise_delete_title),
            text = stringResource(if (usedInSessions) R.string.exercise_delete_used else R.string.exercise_delete_body),
            confirmLabel = stringResource(if (usedInSessions) R.string.action_archive else R.string.action_delete),
            onConfirm = viewModel::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}
