package com.kalist.training.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kalist.training.R
import com.kalist.training.data.db.Session
import com.kalist.training.data.db.SessionStatus
import com.kalist.training.data.db.Template
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onOpenActive: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    var startDialog by rememberSaveable { mutableStateOf(false) }
    var planFor by rememberSaveable { mutableStateOf<Long?>(null) } // epoch day

    LaunchedEffect(Unit) {
        viewModel.events.collect { if (it is CalendarEvent.OpenActive) onOpenActive(it.sessionId) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = viewModel::goToToday) { Icon(Icons.Outlined.Today, stringResource(R.string.calendar_today)) }
                },
            )
        },
        floatingActionButton = {
            if (state?.inProgress == null) {
                ExtendedFloatingActionButton(
                    onClick = { startDialog = true },
                    icon = { Icon(Icons.Filled.PlayArrow, null) },
                    text = { Text(stringResource(R.string.start_session)) },
                )
            }
        },
    ) { padding ->
        val ui = state ?: return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ui.inProgress?.let { running ->
                item(key = "resume") { ResumeBanner(running) { viewModel.open(running) } }
            }
            item(key = "stats") { StatsHeader(ui) }
            item(key = "grid") {
                MonthGrid(ui, onPrev = viewModel::previousMonth, onNext = viewModel::nextMonth, onSelect = viewModel::select)
            }
            item(key = "day-header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ui.selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (!ui.selected.isBefore(ui.today) && ui.templates.isNotEmpty()) {
                        FilledTonalButton(onClick = { planFor = ui.selected.toEpochDay() }) {
                            Icon(Icons.Filled.Add, null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.calendar_plan))
                        }
                    }
                }
            }
            if (ui.selectedSessions.isEmpty()) {
                item(key = "day-empty") {
                    Text(stringResource(R.string.calendar_no_sessions), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(ui.selectedSessions, key = { it.id }) { session ->
                DaySessionItem(
                    session = session,
                    today = ui.today,
                    templateExists = session.templateId != null,
                    onOpen = {
                        when (session.status) {
                            SessionStatus.COMPLETED -> onOpenSession(session.id)
                            SessionStatus.IN_PROGRESS -> viewModel.open(session)
                            else -> Unit
                        }
                    },
                    onStart = { viewModel.open(session) },
                    onSkip = { viewModel.skip(session) },
                    onUnskip = { viewModel.unskip(session) },
                    onDelete = { viewModel.delete(session) },
                )
            }
        }

        if (startDialog) {
            TemplatePickerDialog(
                title = stringResource(R.string.start_session),
                confirmLabel = stringResource(R.string.start_go),
                templates = ui.templates,
                suggested = ui.suggested,
                hint = null,
                onConfirm = { viewModel.startNew(it.id); startDialog = false },
                onDismiss = { startDialog = false },
            )
        }
        planFor?.let { day ->
            val date = LocalDate.ofEpochDay(day)
            TemplatePickerDialog(
                title = stringResource(R.string.calendar_plan_title, date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))),
                confirmLabel = stringResource(R.string.calendar_plan),
                templates = ui.templates,
                suggested = ui.suggested,
                hint = stringResource(R.string.calendar_running_reminder),
                onConfirm = { viewModel.plan(it.id, date); planFor = null },
                onDismiss = { planFor = null },
            )
        }
    }
}

@Composable
private fun ResumeBanner(session: Session, onResume: () -> Unit) {
    Card(
        onClick = onResume,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.calendar_in_progress), style = MaterialTheme.typography.labelLarge)
                Text(session.templateNameSnapshot, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Button(onClick = onResume) { Text(stringResource(R.string.calendar_resume)) }
        }
    }
}

@Composable
private fun StatsHeader(ui: CalendarUi) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.calendar_this_week), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.calendar_week_progress, ui.sessionsThisWeek, ui.weeklyGoal),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                LinearProgressIndicator(
                    progress = { (ui.sessionsThisWeek.toFloat() / ui.weeklyGoal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.primary)
                    Text("${ui.streakWeeks}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Text(pluralStringResource(R.plurals.calendar_streak_weeks, ui.streakWeeks), style = MaterialTheme.typography.labelMedium)
            }
        }
        ui.suggested?.let { next ->
            Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.calendar_next_suggestion), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(10.dp).background(Color(next.colorArgb), CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(next.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MonthGrid(ui: CalendarUi, onPrev: () -> Unit, onNext: () -> Unit, onSelect: (LocalDate) -> Unit) {
    val locale = Locale.getDefault()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.calendar_prev_month)) }
            Text(
                ui.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.calendar_next_month)) }
        }
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { dow ->
                Text(
                    dow.getDisplayName(TextStyle.NARROW, locale),
                    modifier = Modifier.weight(1f).clearAndSetSemantics {},
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (week in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (d in 0 until 7) {
                    val date = ui.gridStart.plusDays((week * 7 + d).toLong())
                    DayCell(
                        date = date,
                        inMonth = date.month == ui.month.month,
                        isToday = date == ui.today,
                        isSelected = date == ui.selected,
                        sessions = ui.sessionsByDate[date].orEmpty(),
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    sessions: List<Session>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = sessions.count { it.status == SessionStatus.COMPLETED }
    val planned = sessions.count { it.status == SessionStatus.PLANNED || it.status == SessionStatus.IN_PROGRESS }
    val description = buildString {
        append(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)))
        if (done > 0) append(", ").append(pluralStringResource(R.plurals.cd_sessions_done, done, done))
        if (planned > 0) append(", ").append(pluralStringResource(R.plurals.cd_sessions_planned, planned, planned))
    }
    Column(
        modifier
            .aspectRatio(1.15f)
            .padding(2.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .then(if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else null,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                    inMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.outline
                },
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(8.dp)) {
            sessions.filter { it.status != SessionStatus.SKIPPED }.take(3).forEach { s ->
                SessionDot(Color(s.templateColorSnapshot), filled = s.status == SessionStatus.COMPLETED)
            }
        }
    }
}

@Composable
fun SessionDot(color: Color, filled: Boolean, size: Int = 8) {
    Box(
        Modifier
            .size(size.dp)
            .then(if (filled) Modifier.background(color, CircleShape) else Modifier.border(1.5.dp, color, CircleShape))
    )
}

@Composable
private fun DaySessionItem(
    session: Session,
    today: LocalDate,
    templateExists: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onSkip: () -> Unit,
    onUnskip: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val statusLabel = stringResource(
        when (session.status) {
            SessionStatus.PLANNED -> R.string.status_planned
            SessionStatus.IN_PROGRESS -> R.string.status_in_progress
            SessionStatus.COMPLETED -> R.string.status_completed
            SessionStatus.SKIPPED -> R.string.status_skipped
        }
    )
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { SessionDot(Color(session.templateColorSnapshot), filled = session.status == SessionStatus.COMPLETED, size = 16) },
            headlineContent = { Text(session.templateNameSnapshot, fontWeight = FontWeight.Bold) },
            supportingContent = {
                val extra = when {
                    session.status == SessionStatus.COMPLETED && session.rpe != null -> " · " + stringResource(R.string.summary_rpe, session.rpe)
                    session.status == SessionStatus.PLANNED && !templateExists -> " · " + stringResource(R.string.calendar_template_deleted)
                    else -> ""
                }
                Text(statusLabel + extra)
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (session.status) {
                        SessionStatus.PLANNED -> if (templateExists && !session.date.isAfter(today)) {
                            Button(onClick = onStart) { Text(stringResource(R.string.calendar_start)) }
                        } else if (templateExists) {
                            OutlinedButton(onClick = onStart) { Text(stringResource(R.string.calendar_start)) }
                        }
                        SessionStatus.IN_PROGRESS -> Button(onClick = onStart) { Text(stringResource(R.string.calendar_resume)) }
                        else -> Unit
                    }
                    if (session.status == SessionStatus.PLANNED || session.status == SessionStatus.SKIPPED) {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (session.status == SessionStatus.PLANNED) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.calendar_mark_skipped)) }, onClick = { menu = false; onSkip() })
                                } else {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.calendar_mark_planned)) }, onClick = { menu = false; onUnskip() })
                                }
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; onDelete() })
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun TemplatePickerDialog(
    title: String,
    confirmLabel: String,
    templates: List<Template>,
    suggested: Template?,
    hint: String?,
    onConfirm: (Template) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf(suggested?.id ?: templates.firstOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (templates.isEmpty()) Text(stringResource(R.string.start_no_templates))
                templates.forEach { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .selectable(selected = selectedId == t.id, role = Role.RadioButton) { selectedId = t.id }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedId == t.id, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(12.dp).background(Color(t.colorArgb), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(t.name, Modifier.weight(1f))
                        if (t.id == suggested?.id) SuggestionChip(onClick = { selectedId = t.id }, label = { Text(stringResource(R.string.start_suggested)) })
                    }
                }
                hint?.let {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.DirectionsRun, null, tint = MaterialTheme.colorScheme.tertiary)
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = templates.any { it.id == selectedId },
                onClick = { templates.firstOrNull { it.id == selectedId }?.let(onConfirm) },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
