package com.kalist.training.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kalist.training.R
import com.kalist.training.data.db.Exercise
import com.kalist.training.data.db.TargetType
import com.kalist.training.ui.components.EmptyState
import com.kalist.training.ui.components.equipmentIcon
import com.kalist.training.ui.components.equipmentLabel
import com.kalist.training.ui.components.targetTypeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    viewModel: ExercisesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_exercises)) },
                actions = {
                    if (state.archived.isNotEmpty()) {
                        TextButton(onClick = viewModel::toggleArchived) {
                            Text(stringResource(if (state.showArchived) R.string.exercises_hide_archived else R.string.exercises_show_archived))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.exercise_new)) },
            )
        },
    ) { padding ->
        if (!state.loading && state.active.isEmpty() && state.archived.isEmpty()) {
            EmptyState(stringResource(R.string.exercises_empty_title), stringResource(R.string.exercises_empty_body), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            items(state.active, key = { it.id }) { ExerciseRow(it, onOpen) }
            if (state.showArchived && state.archived.isNotEmpty()) {
                item(key = "archived-header") {
                    Text(
                        stringResource(R.string.exercises_archived),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(state.archived, key = { it.id }) { ExerciseRow(it, onOpen) }
            }
        }
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, onOpen: (Long) -> Unit) {
    val details = buildList {
        add(targetTypeLabel(exercise.targetType))
        if (exercise.perSide) add(stringResource(R.string.exercise_per_side_short))
        add(equipmentLabel(exercise.equipment))
    }.joinToString(" · ")
    ListItem(
        modifier = Modifier.clickable { onOpen(exercise.id) },
        headlineContent = { Text(exercise.name) },
        supportingContent = { Text(details) },
        leadingContent = {
            Icon(
                if (exercise.targetType == TargetType.TIME) Icons.Outlined.Timer else Icons.Outlined.Repeat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            Column(verticalArrangement = Arrangement.Center) {
                if (exercise.archived) Icon(Icons.Outlined.Inventory2, stringResource(R.string.exercise_archived_badge))
                else Icon(equipmentIcon(exercise.equipment), equipmentLabel(exercise.equipment), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
