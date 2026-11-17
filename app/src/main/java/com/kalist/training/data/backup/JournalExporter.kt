package com.kalist.training.data.backup

import com.kalist.training.data.db.KalistDatabase
import com.kalist.training.data.db.SessionStatus
import com.kalist.training.domain.WeeklyStats
import java.io.OutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** One journal line: a completed session's exercise, matching the existing Excel "Journal" sheet. */
data class JournalRow(
    val date: LocalDate,
    /** Program week, 1 = the week of the first completed session. */
    val week: Int,
    val session: String,
    val exercise: String,
    val variant: String?,
    val sets: List<Int>,
    val rpe: Int?,
    val notes: String?,
) {
    val total: Int get() = sets.sum()
    val best: Int? get() = sets.maxOrNull()
}

@Singleton
class JournalExporter @Inject constructor(private val db: KalistDatabase) {

    suspend fun rows(): List<JournalRow> {
        val sessionDao = db.sessionDao()
        val sessions = sessionDao.getAll().filter { it.status == SessionStatus.COMPLETED }.sortedWith(compareBy({ it.date }, { it.startedAt }))
        if (sessions.isEmpty()) return emptyList()
        val firstWeek = WeeklyStats.weekStart(sessions.first().date)
        return sessions.flatMap { session ->
            val full = sessionDao.getWithExercises(session.id) ?: return@flatMap emptyList()
            val week = ChronoUnit.WEEKS.between(firstWeek, WeeklyStats.weekStart(session.date)).toInt() + 1
            full.sortedExercises.map { e ->
                JournalRow(
                    date = session.date,
                    week = week,
                    session = session.templateNameSnapshot,
                    exercise = e.exercise.name,
                    variant = e.sessionExercise.variant,
                    sets = e.sortedLogs.map { it.value },
                    rpe = session.rpe,
                    notes = session.notes,
                )
            }
        }
    }

    companion object {
        /** Set 1–4 like the original journal, more columns only if a row needs them. */
        fun header(rows: List<JournalRow>): List<String> {
            val setColumns = maxOf(4, rows.maxOfOrNull { it.sets.size } ?: 0)
            return listOf("Date", "Week", "Session", "Exercise", "Variant") +
                (1..setColumns).map { "Set $it" } + listOf("Total", "Best set", "RPE", "Notes")
        }

        fun cells(row: JournalRow, setColumns: Int): List<Any?> =
            listOf<Any?>(row.date.toString(), row.week, row.session, row.exercise, row.variant) +
                (0 until setColumns).map { row.sets.getOrNull(it) } +
                listOf(row.total, row.best, row.rpe, row.notes)

        /** Semicolon-separated with a UTF-8 BOM so Excel (including French locales) opens it directly. */
        fun writeCsv(rows: List<JournalRow>, out: OutputStream) {
            val header = header(rows)
            val setColumns = header.count { it.startsWith("Set ") }
            val writer = out.bufferedWriter(Charsets.UTF_8)
            writer.write("﻿")
            writer.write(header.joinToString(";") { csvCell(it) })
            writer.write("\r\n")
            rows.forEach { row ->
                writer.write(cells(row, setColumns).joinToString(";") { csvCell(it) })
                writer.write("\r\n")
            }
            writer.flush()
        }

        private fun csvCell(value: Any?): String {
            val text = value?.toString() ?: return ""
            return if (text.any { it == ';' || it == '"' || it == '\n' || it == '\r' || it == ',' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
        }

        /** A minimal valid .xlsx (one "Journal" sheet, bold header, numbers as numbers). */
        fun writeXlsx(rows: List<JournalRow>, out: OutputStream) {
            val header = header(rows)
            val setColumns = header.count { it.startsWith("Set ") }
            val zip = ZipOutputStream(out)
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""")
            entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            entry("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Journal" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            entry("xl/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs></styleSheet>""")
            val sheet = StringBuilder()
            sheet.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            sheet.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><sheetData>""")
            fun row(index: Int, values: List<Any?>, style: Int) {
                sheet.append("<row r=\"$index\">")
                values.forEachIndexed { col, v ->
                    val ref = columnName(col) + index
                    val s = if (style != 0) " s=\"$style\"" else ""
                    when (v) {
                        null -> Unit
                        is Number -> sheet.append("<c r=\"$ref\"$s><v>$v</v></c>")
                        else -> sheet.append("<c r=\"$ref\" t=\"inlineStr\"$s><is><t xml:space=\"preserve\">${xmlEscape(v.toString())}</t></is></c>")
                    }
                }
                sheet.append("</row>")
            }
            row(1, header, style = 1)
            rows.forEachIndexed { i, r -> row(i + 2, cells(r, setColumns), style = 0) }
            sheet.append("</sheetData></worksheet>")
            entry("xl/worksheets/sheet1.xml", sheet.toString())
            zip.finish()
            zip.flush()
        }

        internal fun columnName(index: Int): String {
            var n = index + 1
            val sb = StringBuilder()
            while (n > 0) {
                val rem = (n - 1) % 26
                sb.insert(0, 'A' + rem)
                n = (n - 1) / 26
            }
            return sb.toString()
        }

        private fun xmlEscape(text: String): String = buildString {
            text.forEach { c ->
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    else -> if (c.code < 0x20 && c != '\n' && c != '\t') append(' ') else append(c)
                }
            }
        }
    }
}
