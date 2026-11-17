package com.kalist.training.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kalist.training.R
import com.kalist.training.data.db.Template
import com.kalist.training.data.db.TemplateSummary
import com.kalist.training.ui.components.ConfirmDialog
import com.kalist.training.ui.components.EmptyState
import com.kalist.training.ui.components.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    viewModel: TemplatesViewModel = hiltViewModel(),
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Template?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_templates)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.template_new)) },
            )
        },
    ) { padding ->
        val list = templates ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(stringResource(R.string.templates_empty_title), stringResource(R.string.templates_empty_body), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list, key = { it.template.id }) { summary ->
                TemplateCard(
                    summary = summary,
                    onClick = { onOpen(summary.template.id) },
                    onDuplicate = { viewModel.duplicate(summary.template) },
                    onDelete = { toDelete = summary.template },
                )
            }
        }
    }

    toDelete?.let { template ->
        ConfirmDialog(
            title = stringResource(R.string.template_delete_title, template.name),
            text = stringResource(R.string.template_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(template.id) },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun TemplateCard(summary: TemplateSummary, onClick: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    val t = summary.template
    var menu by remember { mutableStateOf(false) }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(8.dp).fillMaxHeight().background(Color(t.colorArgb)))
            Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t.name, style = MaterialTheme.typography.titleMedium)
                    if (t.inRotation) Icon(Icons.Outlined.Sync, stringResource(R.string.template_in_rotation), Modifier.padding(start = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    listOf(
                        pluralStringResource(R.plurals.exercise_count, summary.exerciseCount, summary.exerciseCount),
                        pluralStringResource(R.plurals.round_count, t.rounds, t.rounds),
                        stringResource(R.string.template_rest_value, formatDuration(t.restSeconds)),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                t.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_duplicate)) }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
