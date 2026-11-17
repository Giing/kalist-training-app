package com.kalist.training.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kalist.training.R
import com.kalist.training.data.db.TargetType
import com.kalist.training.ui.components.BackButton
import com.kalist.training.ui.components.ConfirmDialog
import com.kalist.training.ui.components.NumberStepper
import com.kalist.training.ui.components.formatGoal
import com.kalist.training.ui.components.formatSets
import com.kalist.training.ui.components.formatValue
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.deleted.collect { onBack() } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text(stringResource(if (viewModel.justFinished) R.string.summary_title else R.string.detail_title)) },
                actions = {
                    if (!editing) {
                        IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                    }
                },
            )
        },
    ) { padding ->
        val s = summary ?: return@Scaffold
        val session = s.data.session
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(session.templateNameSnapshot, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    val parts = buildList {
                        add(session.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)))
                        if (session.startedAt != null && session.finishedAt != null) {
                            val minutes = Duration.between(session.startedAt, session.finishedAt).toMinutes()
                            add(stringResource(R.string.summary_duration, minutes))
                        }
                    }
                    Text(parts.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.personalBests > 0) {
                        AssistChip(
                            onClick = {},
                            label = { Text(pluralStringResource(R.plurals.summary_new_pbs, s.personalBests, s.personalBests)) },
                            leadingIcon = { Icon(Icons.Filled.EmojiEvents, null) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        )
                    }
                }
            }
            item {
                if (editing) {
                    EditDetails(session.rpe, session.notes.orEmpty()) { rpe, notes ->
                        viewModel.saveDetails(rpe, notes)
                        editing = false
                    }
                } else if (session.rpe != null || !session.notes.isNullOrBlank()) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            session.rpe?.let { Text(stringResource(R.string.summary_rpe, it), style = MaterialTheme.typography.titleSmall) }
                            session.notes?.let { Text(it) }
                        }
                    }
                }
            }
            items(s.exercises, key = { it.entry.sessionExercise.id }) { ex ->
                ExerciseSummaryCard(ex, editing, viewModel)
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.detail_delete_title),
            text = stringResource(R.string.detail_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = viewModel::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditDetails(rpe: Int?, notes: String, onSave: (Int?, String) -> Unit) {
    var r by rememberSaveable { mutableStateOf(rpe) }
    var n by rememberSaveable { mutableStateOf(notes) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.session_rpe_question))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..10).forEach { v ->
                    FilterChip(selected = r == v, onClick = { r = if (r == v) null else v }, label = { Text("$v") }, modifier = Modifier.height(48.dp))
                }
            }
            OutlinedTextField(value = n, onValueChange = { n = it }, label = { Text(stringResource(R.string.session_notes)) }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onSave(r, n) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_done)) }
        }
    }
}

@Composable
private fun ExerciseSummaryCard(ex: ExerciseSummary, editing: Boolean, viewModel: SessionDetailViewModel) {
    val e = ex.entry
    val type = e.exercise.targetType
    val se = e.sessionExercise
    Card(
        Modifier.fillMaxWidth(),
        colors = if (ex.isPersonalBest) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.exercise.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (ex.isPersonalBest) {
                    Icon(Icons.Filled.EmojiEvents, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(stringResource(R.string.summary_pb), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp))
                }
            }
            if (editing) {
                key(se.id) {
                    var variant by rememberSaveable { mutableStateOf(se.variant.orEmpty()) }
                    OutlinedTextField(
                        value = variant,
                        onValueChange = { variant = it; viewModel.setVariant(se.id, it) },
                        label = { Text(stringResource(R.string.session_variant)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                e.sortedLogs.forEach { log ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.summary_set_n, log.setIndex), Modifier.weight(1f))
                        NumberStepper(
                            value = log.value,
                            onValueChange = { viewModel.setValue(se.id, log.setIndex, it) },
                            label = stringResource(R.string.summary_set_n, log.setIndex),
                            range = 0..3600,
                            step = if (type == TargetType.TIME) 5 else 1,
                            compact = true,
                        )
                        IconButton(onClick = { viewModel.removeSet(se.id, log.setIndex) }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.cd_remove_set, log.setIndex))
                        }
                    }
                }
                TextButton(onClick = {
                    val next = (e.logs.maxOfOrNull { it.setIndex } ?: 0) + 1
                    viewModel.setValue(se.id, next, e.sortedLogs.lastOrNull()?.value ?: se.targetMinSnapshot)
                }) {
                    Icon(Icons.Filled.Add, null)
                    Text(stringResource(R.string.summary_add_set))
                }
            } else {
                se.variant?.let { Text(stringResource(R.string.summary_variant, it), style = MaterialTheme.typography.bodyMedium) }
                Text(formatSets(ex.values, type), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val goal = formatGoal(se.targetSetsSnapshot, se.targetMinSnapshot, se.targetMaxSnapshot, type, e.exercise.perSide)
                val details = buildList {
                    add(stringResource(R.string.summary_goal, goal))
                    if (ex.values.isNotEmpty()) add(stringResource(R.string.summary_total, formatValue(ex.total, type)))
                    ex.bestInSession?.let { add(stringResource(R.string.summary_best_set, formatValue(it, type))) }
                }
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
