package com.kalist.training.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kalist.training.R
import com.kalist.training.data.backup.BackupRepository
import com.kalist.training.data.backup.ImportMode
import com.kalist.training.data.backup.InvalidBackupException
import com.kalist.training.data.backup.JournalExporter
import com.kalist.training.data.repo.Seeder
import com.kalist.training.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

enum class JournalFormat(val extension: String, val mime: String) {
    CSV("csv", "text/csv"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
}

/** Local data management through the Storage Access Framework (no network involved). */
@HiltViewModel
class DataViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backup: BackupRepository,
    private val journal: JournalExporter,
    private val seeder: Seeder,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun backupFileName() = "kalist-backup-${LocalDate.now()}.json"
    fun journalFileName(format: JournalFormat) = "kalist-journal-${LocalDate.now()}.${format.extension}"

    fun exportJson(uri: Uri) = run(R.string.data_export_done) {
        val text = backup.exportJson()
        write(uri) { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    fun importJson(uri: Uri, mode: ImportMode) = run(null) {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } ?: error("Cannot open file")
        val result = backup.import(text, mode)
        _messages.send(context.getString(R.string.data_import_done, result.exercises, result.templates, result.sessions))
    }

    fun exportJournal(uri: Uri, format: JournalFormat) = run(R.string.data_export_done) {
        val rows = journal.rows()
        write(uri) { out ->
            when (format) {
                JournalFormat.CSV -> JournalExporter.writeCsv(rows, out)
                JournalFormat.XLSX -> JournalExporter.writeXlsx(rows, out)
            }
        }
    }

    fun resetApp() = run(R.string.data_reset_done) {
        backup.clearAll()
        settings.clear()
        seeder.markUnseeded()
        seeder.seedIfNeeded()
    }

    private suspend fun write(uri: Uri, block: (java.io.OutputStream) -> Unit) = withContext(Dispatchers.IO) {
        // "wt" truncates when overwriting an existing document.
        (context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open file")).use(block)
    }

    private fun run(successMessage: Int?, block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        try {
            block()
            successMessage?.let { _messages.send(context.getString(it)) }
        } catch (e: InvalidBackupException) {
            _messages.send(context.getString(R.string.data_invalid_backup))
        } catch (e: Exception) {
            _messages.send(context.getString(R.string.data_error, e.message ?: e.javaClass.simpleName))
        } finally {
            _busy.value = false
        }
    }
}
