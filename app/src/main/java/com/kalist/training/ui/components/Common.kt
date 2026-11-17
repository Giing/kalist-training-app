package com.kalist.training.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kalist.training.R
import com.kalist.training.data.db.Equipment
import com.kalist.training.data.db.TargetType

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun equipmentLabel(equipment: Equipment): String = stringResource(
    when (equipment) {
        Equipment.NONE -> R.string.equipment_none
        Equipment.BAR -> R.string.equipment_bar
        Equipment.TABLE -> R.string.equipment_table
        Equipment.CHAIR_BENCH -> R.string.equipment_chair_bench
        Equipment.BAND -> R.string.equipment_band
    }
)

fun equipmentIcon(equipment: Equipment): ImageVector = when (equipment) {
    Equipment.NONE -> Icons.Outlined.Block
    Equipment.BAR -> Icons.Outlined.HorizontalRule
    Equipment.TABLE -> Icons.Outlined.TableRestaurant
    Equipment.CHAIR_BENCH -> Icons.Outlined.Chair
    Equipment.BAND -> Icons.Outlined.Gesture
}

@Composable
fun targetTypeLabel(type: TargetType): String =
    stringResource(if (type == TargetType.REPS) R.string.target_reps else R.string.target_time)

/** "3 × 6–12", "3 × 30–45 s", "3 × 8 / côté". */
@Composable
fun formatGoal(sets: Int, min: Int, max: Int, type: TargetType, perSide: Boolean): String {
    val range = if (min == max) "$min" else "$min–$max"
    val unit = if (type == TargetType.TIME) " " + stringResource(R.string.unit_seconds_short) else ""
    val side = if (perSide) " " + stringResource(R.string.per_side_suffix) else ""
    return "$sets × $range$unit$side"
}

/** "8 · 7 · 6" or "30 · 30 s". */
@Composable
fun formatSets(values: List<Int>, type: TargetType): String {
    if (values.isEmpty()) return "—"
    val unit = if (type == TargetType.TIME) " " + stringResource(R.string.unit_seconds_short) else ""
    return values.joinToString(" · ") + unit
}

@Composable
fun formatValue(value: Int, type: TargetType): String =
    if (type == TargetType.TIME) formatDuration(value) else "$value"

/** 45 -> "45 s", 75 -> "1:15". */
fun formatDuration(seconds: Int): String =
    if (seconds < 60) "$seconds s" else "%d:%02d".format(seconds / 60, seconds % 60)
