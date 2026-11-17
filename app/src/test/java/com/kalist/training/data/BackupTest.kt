package com.kalist.training.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kalist.training.TestClock
import com.kalist.training.data.backup.BackupRepository
import com.kalist.training.data.backup.ImportMode
import com.kalist.training.data.backup.InvalidBackupException
import com.kalist.training.data.backup.JournalExporter
import com.kalist.training.data.db.Exercise
import com.kalist.training.data.db.KalistDatabase
import com.kalist.training.data.db.Template
import com.kalist.training.data.db.TemplateExercise
import com.kalist.training.data.repo.ExerciseRepository
import com.kalist.training.data.repo.SessionRepository
import com.kalist.training.data.repo.TemplateRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class BackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = TestClock()
    private lateinit var source: KalistDatabase
    private lateinit var target: KalistDatabase

    private fun db() = Room.inMemoryDatabaseBuilder(context, KalistDatabase::class.java).allowMainThreadQueries().build()

    @Before fun setUp() = runTest {
        source = db()
        target = db()
        populate(source)
    }

    @After fun tearDown() { source.close(); target.close() }

    /** Two completed sessions of a template with push-ups and plank. */
    private suspend fun populate(db: KalistDatabase) {
        val exercises = ExerciseRepository(db.exerciseDao())
        val templates = TemplateRepository(db.templateDao())
        val sessions = SessionRepository(db, db.sessionDao(), db.templateDao(), clock)
        val pushups = exercises.save(Exercise(name = "Push-ups"))
        val plank = exercises.save(Exercise(name = "Plank; \"hold\"", targetType = com.kalist.training.data.db.TargetType.TIME))
        val t = templates.save(
            Template(name = "A", colorArgb = 1),
            listOf(
                TemplateExercise(templateId = 0, exerciseId = pushups, position = 0, targetSets = 3, targetMin = 6, targetMax = 12),
                TemplateExercise(templateId = 0, exerciseId = plank, position = 1, targetSets = 2, targetMin = 30, targetMax = 45),
            ),
        )
        listOf(LocalDate.of(2026, 1, 5) to listOf(8, 7, 6), LocalDate.of(2026, 1, 13) to listOf(9, 8, 8, 7, 6)).forEach { (date, values) ->
            clock.setDate(date)
            val id = sessions.startFromTemplate(t, date)
            val ex = sessions.getWithExercises(id)!!.sortedExercises
            values.forEachIndexed { i, v -> sessions.logSet(ex[0].sessionExercise.id, i + 1, v) }
            sessions.logSet(ex[1].sessionExercise.id, 1, 40)
            sessions.setVariant(ex[0].sessionExercise.id, "Knees")
            sessions.finish(id, rpe = 7, notes = "Good; tired")
        }
    }

    @Test fun replaceRestoresEverything() = runTest {
        val json = BackupRepository(source, clock).exportJson()
        BackupRepository(target, clock).import(json, ImportMode.REPLACE)
        val a = BackupRepository(source, clock).snapshot()
        val b = BackupRepository(target, clock).snapshot()
        assertThat(b.copy(exportedAt = 0)).isEqualTo(a.copy(exportedAt = 0))
    }

    @Test fun mergeTwiceDoesNotDuplicate() = runTest {
        val json = BackupRepository(source, clock).exportJson()
        val first = BackupRepository(source, clock).import(json, ImportMode.MERGE)
        assertThat(first.exercises).isEqualTo(0)
        assertThat(first.templates).isEqualTo(0)
        assertThat(first.sessions).isEqualTo(0)

        val into = BackupRepository(target, clock)
        into.import(json, ImportMode.MERGE)
        into.import(json, ImportMode.MERGE)
        val snap = into.snapshot()
        assertThat(snap.exercises).hasSize(2)
        assertThat(snap.templates).hasSize(1)
        assertThat(snap.sessions).hasSize(2)
        assertThat(snap.setLogs).hasSize(10)
    }

    @Test fun rejectsInvalidFiles() {
        val repo = BackupRepository(target, clock)
        assertThrows(InvalidBackupException::class.java) { repo.parse("{\"hello\": 1}") }
        assertThrows(InvalidBackupException::class.java) { repo.parse("not json") }
    }

    @Test fun journalRowsMatchTheExcelColumns() = runTest {
        val rows = JournalExporter(source).rows()
        assertThat(rows).hasSize(4)
        val second = rows[2]
        assertThat(second.week).isEqualTo(2)
        assertThat(second.sets).containsExactly(9, 8, 8, 7, 6).inOrder()
        assertThat(second.total).isEqualTo(38)
        assertThat(second.best).isEqualTo(9)
        assertThat(JournalExporter.header(rows)).containsExactly(
            "Date", "Week", "Session", "Exercise", "Variant", "Set 1", "Set 2", "Set 3", "Set 4", "Set 5", "Total", "Best set", "RPE", "Notes",
        ).inOrder()

        val csv = ByteArrayOutputStream().also { JournalExporter.writeCsv(rows, it) }.toString(Charsets.UTF_8)
        val lines = csv.removePrefix("﻿").split("\r\n")
        assertThat(lines[0]).startsWith("Date;Week;Session;Exercise;Variant;Set 1")
        assertThat(lines[1]).isEqualTo("2026-01-05;1;A;Push-ups;Knees;8;7;6;;;21;8;7;\"Good; tired\"")
        assertThat(lines[2]).contains("\"Plank; \"\"hold\"\"\"")
    }

    @Test fun xlsxIsAValidZipWithTheJournalSheet() = runTest {
        val rows = JournalExporter(source).rows()
        val bytes = ByteArrayOutputStream().also { JournalExporter.writeXlsx(rows, it) }.toByteArray()
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entries[it.name] = zip.readBytes().toString(Charsets.UTF_8) }
        }
        assertThat(entries.keys).containsAtLeast("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/worksheets/sheet1.xml", "xl/styles.xml")
        assertThat(entries.getValue("xl/workbook.xml")).contains("name=\"Journal\"")
        val sheet = entries.getValue("xl/worksheets/sheet1.xml")
        assertThat(sheet).contains("<c r=\"A1\" t=\"inlineStr\" s=\"1\"><is><t xml:space=\"preserve\">Date</t>")
        assertThat(sheet).contains("<c r=\"F2\"><v>8</v></c>")
        assertThat(sheet).contains("Plank; &quot;hold&quot;")
        assertThat(JournalExporter.columnName(0)).isEqualTo("A")
        assertThat(JournalExporter.columnName(25)).isEqualTo("Z")
        assertThat(JournalExporter.columnName(26)).isEqualTo("AA")
    }
}
