package com.kalist.training.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.kalist.training.data.db.KalistDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KalistDatabase::class.java)

    @Test
    fun migrate1To2KeepsDataAndAddsSkipped() {
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO exercise (id, name, targetType, perSide, equipment, archived, createdAt) VALUES (1, 'Push-ups', 'REPS', 0, 'NONE', 0, 0)")
            execSQL("INSERT INTO session (id, templateId, templateNameSnapshot, templateColorSnapshot, date, status, roundsSnapshot, restSecondsSnapshot, cursorStep) VALUES (1, NULL, 'A', 0, 20000, 'COMPLETED', 3, 90, 0)")
            execSQL("INSERT INTO session_exercise (id, sessionId, exerciseId, position, targetSetsSnapshot, targetMinSnapshot, targetMaxSnapshot) VALUES (1, 1, 1, 0, 3, 6, 12)")
            execSQL("INSERT INTO set_log (id, sessionExerciseId, setIndex, value, completedAt) VALUES (1, 1, 1, 8, 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 2, true, KalistDatabase.MIGRATION_1_2)
        db.query("SELECT skipped FROM session_exercise WHERE id = 1").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getInt(0)).isEqualTo(0)
        }
        db.query("SELECT value FROM set_log WHERE id = 1").use {
            it.moveToFirst()
            assertThat(it.getInt(0)).isEqualTo(8)
        }
    }

    private companion object { const val DB = "migration-test" }
}
