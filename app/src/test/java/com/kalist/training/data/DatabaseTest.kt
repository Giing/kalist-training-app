package com.kalist.training.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kalist.training.TestClock
import com.kalist.training.data.db.Exercise
import com.kalist.training.data.db.KalistDatabase
import com.kalist.training.data.db.SessionStatus
import com.kalist.training.data.db.TargetType
import com.kalist.training.data.db.Template
import com.kalist.training.data.db.TemplateExercise
import com.kalist.training.data.repo.ExerciseRepository
import com.kalist.training.data.repo.RemovalResult
import com.kalist.training.data.repo.SessionRepository
import com.kalist.training.data.repo.TemplateRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: KalistDatabase
    private lateinit var clock: TestClock
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: TemplateRepository
    private lateinit var sessions: SessionRepository

    private var pushups = 0L
    private var plank = 0L
    private var templateA = 0L
    private var templateB = 0L

    @Before fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KalistDatabase::class.java).allowMainThreadQueries().build()
        clock = TestClock()
        exercises = ExerciseRepository(db.exerciseDao())
        templates = TemplateRepository(db.templateDao())
        sessions = SessionRepository(db, db.sessionDao(), db.templateDao(), clock)

        pushups = exercises.save(Exercise(name = "Push-ups", harderVariant = "Archer"))
        plank = exercises.save(Exercise(name = "Plank", targetType = TargetType.TIME))
        templateA = templates.save(
            Template(name = "A", colorArgb = 1, createdAt = clock.instant()),
            listOf(item(pushups, 6, 12), item(plank, 30, 45)),
        )
        templateB = templates.save(
            Template(name = "B", colorArgb = 2, createdAt = clock.instant().plusSeconds(1)),
            listOf(item(plank, 20, 30)),
        )
    }

    @After fun tearDown() = db.close()

    private fun item(exerciseId: Long, min: Int, max: Int) =
        TemplateExercise(templateId = 0, exerciseId = exerciseId, position = 0, targetSets = 3, targetMin = min, targetMax = max)

    /** Runs and completes a session of [templateId] on [date], logging [values] per exercise position. */
    private suspend fun completeSession(templateId: Long, date: LocalDate, vararg values: List<Int>, variant: String? = null): Long {
        clock.setDate(date)
        val id = sessions.startFromTemplate(templateId, date)
        val withExercises = sessions.getWithExercises(id)!!
        withExercises.sortedExercises.forEachIndexed { index, se ->
            values.getOrNull(index)?.forEachIndexed { set, value -> sessions.logSet(se.sessionExercise.id, set + 1, value) }
            if (variant != null && index == 0) sessions.setVariant(se.sessionExercise.id, variant)
        }
        clock.advance(Duration.ofMinutes(30))
        sessions.finish(id, rpe = 7, notes = null)
        return id
    }

    @Test fun templateIsSavedWithOrderedExercises() = runTest {
        val loaded = templates.getWithExercises(templateA)!!
        assertThat(loaded.sortedItems.map { it.exercise.name }).containsExactly("Push-ups", "Plank").inOrder()
        assertThat(loaded.sortedItems.map { it.item.position }).containsExactly(0, 1).inOrder()
    }

    @Test fun previousReturnsLastCompletedSessionValuesAndVariant() = runTest {
        completeSession(templateA, LocalDate.of(2026, 1, 5), listOf(8, 7, 6), variant = "Knees")
        completeSession(templateA, LocalDate.of(2026, 1, 7), listOf(9, 8, 8), variant = "Full")

        val previous = sessions.previous(pushups)!!
        assertThat(previous.sortedLogs.map { it.value }).containsExactly(9, 8, 8).inOrder()
        assertThat(previous.sessionExercise.variant).isEqualTo("Full")
    }

    @Test fun previousIgnoresInProgressAndPlannedSessions() = runTest {
        completeSession(templateA, LocalDate.of(2026, 1, 5), listOf(8, 7, 6))
        clock.setDate(LocalDate.of(2026, 1, 7))
        val running = sessions.startFromTemplate(templateA)
        val se = sessions.getWithExercises(running)!!.sortedExercises.first()
        sessions.logSet(se.sessionExercise.id, 1, 20)

        assertThat(sessions.previous(pushups, excludeSessionId = running)!!.sortedLogs.map { it.value }).containsExactly(8, 7, 6).inOrder()
        assertThat(sessions.best(pushups)!!.value).isEqualTo(8)
    }

    @Test fun bestReturnsMaxSetAndFirstDateReachingIt() = runTest {
        completeSession(templateA, LocalDate.of(2026, 1, 5), listOf(8, 10, 6), listOf(40))
        completeSession(templateA, LocalDate.of(2026, 1, 7), listOf(10, 9, 8), listOf(45))
        completeSession(templateA, LocalDate.of(2026, 1, 9), listOf(9, 9, 9))

        val best = sessions.best(pushups)!!
        assertThat(best.value).isEqualTo(10)
        assertThat(best.date).isEqualTo(LocalDate.of(2026, 1, 5))
        assertThat(sessions.best(plank)!!.value).isEqualTo(45)
        assertThat(sessions.best(plank)!!.date).isEqualTo(LocalDate.of(2026, 1, 7))
    }

    @Test fun nextTemplateAlternates() = runTest {
        assertThat(templates.nextTemplate()!!.id).isEqualTo(templateA)
        completeSession(templateA, LocalDate.of(2026, 1, 5), listOf(8))
        assertThat(templates.nextTemplate()!!.id).isEqualTo(templateB)
        completeSession(templateB, LocalDate.of(2026, 1, 7), listOf(30))
        assertThat(templates.nextTemplate()!!.id).isEqualTo(templateA)
    }

    @Test fun startingPrefillsVariantFromLastTime() = runTest {
        completeSession(templateA, LocalDate.of(2026, 1, 5), listOf(8), variant = "Knees")
        clock.setDate(LocalDate.of(2026, 1, 7))
        val id = sessions.startFromTemplate(templateA)
        assertThat(sessions.getWithExercises(id)!!.sortedExercises.first().sessionExercise.variant).isEqualTo("Knees")
    }

    @Test fun editingTemplateNeverChangesHistory() = runTest {
        val id = completeSession(templateA, LocalDate.of(2026, 1, 5), listOf(8))
        val template = templates.getWithExercises(templateA)!!
        templates.save(
            template.template.copy(name = "A renamed"),
            listOf(item(pushups, 10, 20).copy(targetSets = 4)),
        )
        val history = sessions.getWithExercises(id)!!
        assertThat(history.session.templateNameSnapshot).isEqualTo("A")
        assertThat(history.sortedExercises).hasSize(2)
        assertThat(history.sortedExercises.first().sessionExercise.targetMaxSnapshot).isEqualTo(12)
        assertThat(history.sortedExercises.first().sessionExercise.targetSetsSnapshot).isEqualTo(3)
    }

    @Test fun deletingTemplateKeepsSessionsWithSnapshot() = runTest {
        val id = completeSession(templateA, LocalDate.of(2026, 1, 5), listOf(8))
        templates.delete(templateA)
        val history = sessions.getWithExercises(id)!!
        assertThat(history.session.templateId).isNull()
        assertThat(history.session.templateNameSnapshot).isEqualTo("A")
        assertThat(history.sortedExercises.first().sortedLogs.map { it.value }).containsExactly(8)
    }

    @Test fun exerciseWithHistoryIsArchivedNotDeleted() = runTest {
        completeSession(templateA, LocalDate.of(2026, 1, 5), listOf(8))
        assertThat(exercises.deleteOrArchive(pushups)).isEqualTo(RemovalResult.ARCHIVED)
        assertThat(exercises.get(pushups)!!.archived).isTrue()
        assertThat(exercises.observeActive().first().map { it.id }).doesNotContain(pushups)

        val unused = exercises.save(Exercise(name = "Burpees"))
        assertThat(exercises.deleteOrArchive(unused)).isEqualTo(RemovalResult.DELETED)
        assertThat(exercises.get(unused)).isNull()
    }

    @Test fun loggingTheSameSetTwiceReplacesIt() = runTest {
        val id = sessions.startFromTemplate(templateA)
        val se = sessions.getWithExercises(id)!!.sortedExercises.first().sessionExercise
        sessions.logSet(se.id, 1, 5)
        sessions.logSet(se.id, 1, 7)
        assertThat(sessions.getWithExercises(id)!!.sortedExercises.first().sortedLogs.map { it.value }).containsExactly(7)
    }

    @Test fun plannedSessionCopiesTargetsOnlyWhenStarted() = runTest {
        val id = sessions.plan(templateA, LocalDate.of(2026, 1, 5))
        assertThat(sessions.getWithExercises(id)!!.exercises).isEmpty()
        val template = templates.getWithExercises(templateA)!!
        templates.save(template.template, listOf(item(pushups, 5, 15)))
        sessions.start(id)
        val started = sessions.getWithExercises(id)!!
        assertThat(started.session.status).isEqualTo(SessionStatus.IN_PROGRESS)
        assertThat(started.sortedExercises.single().sessionExercise.targetMaxSnapshot).isEqualTo(15)
    }

    @Test fun cursorIsPersistedForResume() = runTest {
        val id = sessions.startFromTemplate(templateA)
        sessions.updateCursor(id, 3, null)
        assertThat(sessions.getWithExercises(id)!!.session.cursorStep).isEqualTo(3)
        assertThat(sessions.observeInProgress().first()!!.id).isEqualTo(id)
    }
}
