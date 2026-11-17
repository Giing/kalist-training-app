package com.kalist.training.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kalist.training.R
import com.kalist.training.data.db.TargetType
import com.kalist.training.ui.components.BackButton
import com.kalist.training.ui.components.EmptyState
import com.kalist.training.ui.components.formatDuration
import com.kalist.training.ui.components.formatSets
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseHistoryScreen(
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit,
    viewModel: ExerciseHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text(state?.exercise?.name ?: stringResource(R.string.history_title)) },
            )
        },
    ) { padding ->
        val ui = state ?: return@Scaffold
        if (ui.entries.isEmpty()) {
            EmptyState(stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_body), Modifier.padding(padding))
            return@Scaffold
        }
        val type = ui.exercise.targetType
        val format: (Int) -> String = { if (type == TargetType.TIME) formatDuration(it) else "$it" }
        val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.history_chart_title), style = MaterialTheme.typography.titleMedium)
                        ui.allTimeBest?.let {
                            Text(
                                stringResource(R.string.history_all_time_best, format(it.best), it.date.format(dateFormat)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (ui.entries.size >= 2) {
                            val latest = ui.entries.last()
                            BestSetChart(
                                entries = ui.entries,
                                formatValue = format,
                                description = stringResource(
                                    R.string.cd_history_chart, ui.entries.size,
                                    format(ui.entries.first().best), format(latest.best), format(ui.allTimeBest!!.best),
                                ),
                            )
                        } else {
                            Text(stringResource(R.string.history_need_two), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.history_sessions), style = MaterialTheme.typography.titleMedium) }
            items(ui.entries.reversed(), key = { it.sessionId }) { entry ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row {
                        Text(entry.date.format(dateFormat), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(formatSets(entry.values, type), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                    val details = listOfNotNull(
                        entry.variant,
                        stringResource(R.string.summary_best_set, format(entry.best)),
                        stringResource(R.string.summary_total, format(entry.total)),
                    ).joinToString(" · ")
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
