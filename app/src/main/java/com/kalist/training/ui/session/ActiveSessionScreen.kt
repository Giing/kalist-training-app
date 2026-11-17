package com.kalist.training.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kalist.training.R
import com.kalist.training.data.db.TargetType
import com.kalist.training.ui.components.NumberStepper
import com.kalist.training.ui.components.formatDuration
import com.kalist.training.ui.components.formatGoal
import com.kalist.training.ui.components.formatSets
import com.kalist.training.ui.components.formatValue
import com.kalist.training.ui.theme.BigNumber
import com.kalist.training.ui.theme.HugeNumber
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(
    onFinished: (Long) -> Unit,
    onLeave: () -> Unit,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    var leaveDialog by rememberSaveable { mutableStateOf(false) }
    var finishDialog by rememberSaveable { mutableStateOf(false) }

    // The screen stays on for the whole session.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ActiveSessionEvent.AskFinish -> finishDialog = true
                ActiveSessionEvent.Finished -> onFinished(viewModel.sessionId)
                ActiveSessionEvent.Abandoned -> onLeave()
            }
        }
    }

    BackHandler { leaveDialog = true }

    val ui = state
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { leaveDialog = true }) { Icon(Icons.Filled.Close, stringResource(R.string.session_leave)) }
                },
                title = {
                    Column {
                        Text(ui?.data?.session?.templateNameSnapshot.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ui?.step?.let {
                            Text(
                                stringResource(R.string.session_round_of, it.round, ui.totalRounds),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                actions = { TextButton(onClick = viewModel::requestFinish) { Text(stringResource(R.string.session_finish)) } },
            )
        },
    ) { padding ->
        if (ui == null) return@Scaffold
        Box(Modifier.fillMaxSize().padding(padding)) {
            SessionContent(ui, viewModel)
            AnimatedVisibility(visible = ui.restRemaining != null, enter = fadeIn(), exit = fadeOut()) {
                RestOverlay(ui, onSkip = viewModel::skipRest, onAdd = { viewModel.addRest(15) })
            }
        }
    }

    if (leaveDialog) {
        AlertDialog(
            onDismissRequest = { leaveDialog = false },
            title = { Text(stringResource(R.string.session_leave_title)) },
            text = { Text(stringResource(R.string.session_leave_body)) },
            confirmButton = {
                TextButton(onClick = { leaveDialog = false; onLeave() }) { Text(stringResource(R.string.session_resume_later)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { leaveDialog = false; viewModel.abandon() }) {
                        Text(stringResource(R.string.session_abandon), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { leaveDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        )
    }

    if (finishDialog && ui != null) {
        FinishDialog(
            remaining = ui.remainingSteps,
            onConfirm = { rpe, notes -> finishDialog = false; viewModel.finish(rpe, notes) },
            onDismiss = { finishDialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionContent(ui: ActiveSessionUi, viewModel: ActiveSessionViewModel) {
    val current = ui.current ?: return
    val step = ui.step ?: return
    val type = current.exercise.targetType
    val se = current.sessionExercise
    val ref = ui.reference

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Exercise header
        Text(
            current.exercise.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        se.noteSnapshot?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        // Variant, prefilled with the one used last time
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

        // Previous / Goal / Best
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReferenceCard(
                stringResource(R.string.session_previous),
                formatSets(ref?.previousValues.orEmpty(), type),
                ref?.previousVariant,
                Modifier.weight(1f),
            )
            ReferenceCard(
                stringResource(R.string.session_goal),
                formatGoal(se.targetSetsSnapshot, se.targetMinSnapshot, se.targetMaxSnapshot, type, current.exercise.perSide),
                null,
                Modifier.weight(1f),
            )
            ReferenceCard(
                stringResource(R.string.session_best),
                ref?.best?.let { formatValue(it.value, type) } ?: "—",
                ref?.best?.date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)),
                Modifier.weight(1f),
            )
        }

        if (ui.suggestHarder) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.TipsAndUpdates, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(
                        stringResource(R.string.session_try_harder, current.exercise.harderVariant.orEmpty()),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }

        HorizontalDivider()

        // Current set input
        Text(
            stringResource(R.string.session_set_of, step.round, se.targetSetsSnapshot) +
                if (current.exercise.perSide) " " + stringResource(R.string.per_side_suffix) else "",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (type == TargetType.TIME) {
            TimeInput(ui, viewModel)
        } else {
            NumberStepper(
                value = ui.input,
                onValueChange = viewModel::setInput,
                label = stringResource(R.string.session_reps),
                range = 0..999,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        Button(
            onClick = viewModel::logCurrent,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            enabled = ui.stopwatch == null,
        ) {
            Icon(Icons.Filled.Check, null, Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.session_log_set), fontSize = 22.sp)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = viewModel::previousStep, enabled = ui.cursor > 0, modifier = Modifier.height(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.Undo, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.session_back_step))
            }
            OutlinedButton(onClick = viewModel::skipSet, modifier = Modifier.height(48.dp)) {
                Icon(Icons.Filled.SkipNext, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.session_skip_set))
            }
            OutlinedButton(onClick = viewModel::skipExercise, modifier = Modifier.height(48.dp)) {
                Text(stringResource(R.string.session_skip_exercise))
            }
        }

        HorizontalDivider()
        Text(stringResource(R.string.session_all_exercises), style = MaterialTheme.typography.titleSmall)
        ui.exercises.forEachIndexed { index, e ->
            ExerciseProgressRow(ui, index, isCurrent = index == step.exerciseIndex, onClick = { viewModel.jumpTo(index) })
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TimeInput(ui: ActiveSessionUi, viewModel: ActiveSessionViewModel) {
    val seconds = ui.stopwatchSeconds
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (seconds != null) {
            val text = if (seconds < 0) "${-seconds}" else formatDuration(seconds.toInt())
            Text(
                text,
                style = HugeNumber,
                color = if (seconds < 0) MaterialTheme.colorScheme.tertiary
                else if (seconds >= ui.input) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(stringResource(R.string.session_target_time, formatDuration(ui.input)), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = viewModel::cancelStopwatch, modifier = Modifier.height(56.dp)) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = viewModel::stopStopwatch, modifier = Modifier.height(56.dp)) {
                    Icon(Icons.Filled.Stop, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.session_stop_timer))
                }
            }
        } else {
            NumberStepper(
                value = ui.input,
                onValueChange = viewModel::setInput,
                label = stringResource(R.string.session_seconds),
                range = 0..3600,
                step = 5,
                suffix = stringResource(R.string.unit_seconds_short),
                valueStyle = BigNumber,
            )
            Button(onClick = viewModel::startStopwatch, modifier = Modifier.height(56.dp)) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.session_start_timer))
            }
        }
    }
}

@Composable
private fun ReferenceCard(title: String, value: String, detail: String?, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(10.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun ExerciseProgressRow(ui: ActiveSessionUi, index: Int, isCurrent: Boolean, onClick: () -> Unit) {
    val e = ui.exercises[index]
    val sets = e.sessionExercise.targetSetsSnapshot
    val logged = (1..sets).map { ui.loggedValue(index, it) }
    val done = logged.all { it != null }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(28.dp).background(
                when {
                    done -> MaterialTheme.colorScheme.primary
                    e.sessionExercise.skipped -> MaterialTheme.colorScheme.outlineVariant
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                },
                CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
            else Text("${index + 1}", style = MaterialTheme.typography.labelMedium)
        }
        Column(Modifier.weight(1f)) {
            Text(e.exercise.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isCurrent) FontWeight.Bold else null)
            if (e.sessionExercise.skipped) Text(stringResource(R.string.session_skipped), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            logged.joinToString(" · ") { it?.toString() ?: "–" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RestOverlay(ui: ActiveSessionUi, onSkip: () -> Unit, onAdd: () -> Unit) {
    val remaining = ui.restRemaining ?: 0
    val next = ui.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.session_rest), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            val label = stringResource(R.string.cd_rest_remaining, formatDuration(remaining.toInt()))
            Text(
                formatDuration(remaining.toInt()).removeSuffix(" s"),
                style = HugeNumber.copy(fontSize = 120.sp, lineHeight = 124.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { contentDescription = label },
            )
            ui.step?.let {
                Text(stringResource(R.string.session_round_of, it.round, ui.totalRounds), style = MaterialTheme.typography.titleMedium)
            }
            next?.let { Text(stringResource(R.string.session_next, it.exercise.name), style = MaterialTheme.typography.titleMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onAdd, modifier = Modifier.height(64.dp)) { Text(stringResource(R.string.session_add_15s), fontSize = 18.sp) }
                Button(onClick = onSkip, modifier = Modifier.height(64.dp)) { Text(stringResource(R.string.session_skip_rest), fontSize = 18.sp) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FinishDialog(remaining: Int, onConfirm: (Int?, String?) -> Unit, onDismiss: () -> Unit) {
    var rpe by rememberSaveable { mutableStateOf<Int?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_finish_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (remaining > 0) {
                    Text(pluralStringResource(R.plurals.session_sets_remaining, remaining, remaining), color = MaterialTheme.colorScheme.tertiary)
                }
                Text(stringResource(R.string.session_rpe_question), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..10).forEach { value ->
                        FilterChip(
                            selected = rpe == value,
                            onClick = { rpe = if (rpe == value) null else value },
                            label = { Text("$value") },
                            modifier = Modifier.height(48.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.session_notes)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(rpe, notes) }) { Text(stringResource(R.string.session_finish)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.session_continue)) } },
    )
}
