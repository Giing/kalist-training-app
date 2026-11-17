package com.kalist.training.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.net.Uri
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kalist.training.R
import com.kalist.training.data.backup.ImportMode

@Composable
fun DataSection(viewModel: DataViewModel = hiltViewModel()) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingImport by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportJson)
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport = uri?.toString()
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JournalFormat.CSV.mime)) { uri ->
        uri?.let { viewModel.exportJournal(it, JournalFormat.CSV) }
    }
    val exportXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JournalFormat.XLSX.mime)) { uri ->
        uri?.let { viewModel.exportJournal(it, JournalFormat.XLSX) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Text(stringResource(R.string.settings_data), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.settings_data_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        DataButton(Icons.Outlined.FileDownload, stringResource(R.string.data_export_json), !busy) { exportJson.launch(viewModel.backupFileName()) }
        DataButton(Icons.Outlined.FileUpload, stringResource(R.string.data_import_json), !busy) {
            importJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
        DataButton(Icons.Outlined.TableChart, stringResource(R.string.data_export_csv), !busy) { exportCsv.launch(viewModel.journalFileName(JournalFormat.CSV)) }
        DataButton(Icons.Outlined.TableChart, stringResource(R.string.data_export_xlsx), !busy) { exportXlsx.launch(viewModel.journalFileName(JournalFormat.XLSX)) }
        OutlinedButton(onClick = { confirmReset = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.data_reset), color = MaterialTheme.colorScheme.error)
        }
    }

    pendingImport?.let { uriString ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.data_import_title)) },
            text = { Text(stringResource(R.string.data_import_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.importJson(Uri.parse(uriString), ImportMode.MERGE); pendingImport = null }) {
                    Text(stringResource(R.string.data_import_merge))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.importJson(Uri.parse(uriString), ImportMode.REPLACE); pendingImport = null }) {
                    Text(stringResource(R.string.data_import_replace), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.data_reset_title)) },
            text = { Text(stringResource(R.string.data_reset_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.resetApp(); confirmReset = false }) {
                    Text(stringResource(R.string.data_reset_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun DataButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null)
        Spacer(Modifier.width(8.dp))
        Text(label, Modifier.weight(1f))
    }
}
