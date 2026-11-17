package com.kalist.training.ui

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kalist.training.MainActivity
import com.kalist.training.TestClock
import com.kalist.training.data.repo.Seeder
import com.kalist.training.data.repo.SessionRepository
import com.kalist.training.data.repo.TemplateRepository
import com.kalist.training.data.settings.SettingsRepository
import com.kalist.training.data.settings.ThemeMode
import com.kalist.training.ui.history.History
import com.kalist.training.ui.history.niceMax
import com.kalist.training.data.db.ExerciseSetRow
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = "w400dp-h860dp-xhdpi")
class HistoryScreenTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Inject lateinit var seeder: Seeder
    @Inject lateinit var sessions: SessionRepository
    @Inject lateinit var templates: TemplateRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var clock: TestClock

    @Before fun setUp() {
        hilt.inject()
        runBlocking {
            seeder.seedIfNeeded()
            val a = templates.getAll().first { it.name.startsWith("A") }
            // Six sessions of template A over three weeks with progressing push-ups.
            listOf(5 to listOf(6, 6, 5), 7 to listOf(7, 6, 6), 9 to listOf(8, 7, 6), 13 to listOf(8, 8, 7), 16 to listOf(10, 9, 8), 20 to listOf(9, 9, 9))
                .forEach { (day, values) ->
                    val date = LocalDate.of(2026, 1, day)
                    clock.setDate(date)
                    val id = sessions.startFromTemplate(a.id, date)
                    val pushups = sessions.getWithExercises(id)!!.sortedExercises.first()
                    values.forEachIndexed { i, v -> sessions.logSet(pushups.sessionExercise.id, i + 1, v) }
                    sessions.finish(id, rpe = 7, notes = null)
                }
        }
    }

    private fun waitFor(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun historyShowsChartAndSessions() = ActivityScenario.launch(MainActivity::class.java).use {
        waitFor("Exercises")
        compose.onNodeWithText("Exercises").performClick()
        waitFor("Push-ups")
        compose.onNodeWithText("Push-ups").performClick()
        compose.onNode(hasContentDescription("History")).performClick()
        waitFor("Best set per session")
        waitFor("10 · 9 · 8")
        compose.screenshot("13-history")
        compose.onAllNodes(hasContentDescription("Chart of the best set over 6 sessions", substring = true)).fetchSemanticsNodes().let {
            assertThat(it).hasSize(1)
        }
    }

    @Test fun activeSessionInDarkMode() {
        runBlocking { settings.setThemeMode(ThemeMode.DARK) }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Start session")
            compose.onNodeWithText("Start session", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Let’s go").performClick()
            waitFor("Set 1 / 3")
            compose.screenshot("14-active-session-dark")
        }
    }
}

class HistoryLogicTest {
    @Test fun niceAxisMax() {
        assertThat(niceMax(10)).isEqualTo(12) // 11.5 / 4 = 2.9 -> step 3
        assertThat(niceMax(8)).isEqualTo(10)  // 2.3 -> step 2.5
        assertThat(niceMax(45)).isEqualTo(60) // 12.9 -> step 15
        assertThat(niceMax(3)).isEqualTo(4)
    }

    @Test fun groupsRowsBySession() {
        val rows = listOf(
            ExerciseSetRow(2, LocalDate.of(2026, 1, 7), "Knees", 2, 6),
            ExerciseSetRow(1, LocalDate.of(2026, 1, 5), null, 1, 5),
            ExerciseSetRow(2, LocalDate.of(2026, 1, 7), "Knees", 1, 7),
        )
        val entries = History.group(rows)
        assertThat(entries.map { it.sessionId }).containsExactly(1L, 2L).inOrder()
        assertThat(entries[1].values).containsExactly(7, 6).inOrder()
        assertThat(entries[1].best).isEqualTo(7)
    }
}
