package com.kalist.training.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kalist.training.MainActivity
import com.kalist.training.data.db.SessionStatus
import com.kalist.training.data.repo.Seeder
import com.kalist.training.data.repo.SessionRepository
import com.kalist.training.data.repo.TemplateRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import javax.inject.Inject

private const val TIMEOUT = 10_000L

/** Runs what coroutines posted to the main looper (Robolectric's looper is paused between checks). */
private fun idleMain() {
    val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
    repeat(50) { if (!looper.isIdle) looper.runOneTask() }
}

private fun ComposeTestRule.waitForDb(condition: () -> Boolean) =
    waitUntil(TIMEOUT) { idleMain(); condition() }

private fun ComposeTestRule.waitForText(text: String, substring: Boolean = false) =
    waitUntil(TIMEOUT) { idleMain(); onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

private fun ComposeTestRule.waitForDescription(text: String) =
    waitUntil(TIMEOUT) { idleMain(); onAllNodes(hasContentDescription(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = "w400dp-h860dp-xhdpi")
class EndToEndTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Inject lateinit var seeder: Seeder
    @Inject lateinit var sessions: SessionRepository
    @Inject lateinit var exercises: com.kalist.training.data.repo.ExerciseRepository

    @Before fun setUp() {
        hilt.inject()
        // Seed before any activity exists (blocking the main thread while the UI queries the DB can deadlock).
        runBlocking { seeder.seedIfNeeded() }
    }

    @Test fun createTimeExerciseAndTemplateThenRunFullSession() = ActivityScenario.launch(MainActivity::class.java).use {
        compose.waitForText("Next template:")
        compose.screenshot("01-calendar-empty")

        // 1. Create a TIME exercise
        compose.onNodeWithText("Exercises").performClick()
        compose.waitForText("Push-ups")
        compose.screenshot("02-exercises")
        compose.onNodeWithText("New exercise", useUnmergedTree = true).performClick()
        compose.onNode(hasText("Name") and hasSetTextAction()).performTextInput("Wall sit")
        compose.onNodeWithText("Time").performClick()
        compose.screenshot("03-exercise-editor")
        compose.onAllNodesWithText("Save").onLast().performScrollTo().performClick()
        compose.waitForDb { runBlocking { exercises.observeAll().first().any { it.name == "Wall sit" } } }
        compose.waitForText("Push-ups")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Wall sit"))
        compose.waitForText("Wall sit")

        // 2. Create a template using it
        compose.onNodeWithText("Templates").performClick()
        compose.waitForText("A: Push & core")
        compose.screenshot("04-templates")
        compose.onNodeWithText("New template", useUnmergedTree = true).performClick()
        compose.onNode(hasText("Name") and hasSetTextAction()).performTextInput("Hotel")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Add exercise"))
        compose.onNodeWithText("Add exercise").performClick()
        compose.waitForText("Push-ups")
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Wall sit"))
        compose.onNodeWithText("Wall sit").performClick()
        compose.waitForText("3 × 20–30 s")
        compose.screenshot("05-template-editor")
        compose.onAllNodesWithText("Save").onFirst().performClick()
        compose.waitForText("Calendar")
        compose.waitForText("Hotel")

        // 3. Run a full session of it
        compose.onNodeWithText("Calendar").performClick()
        compose.onNodeWithText("Start session", useUnmergedTree = true).performClick()
        compose.waitForText("Hotel")
        compose.screenshot("06-start-dialog")
        compose.onAllNodesWithText("Hotel").onLast().performClick()
        compose.onNodeWithText("Let’s go").performClick()
        compose.waitForText("Round 1 / 3")
        compose.screenshot("07-active-session-time")
        repeat(3) { round ->
            compose.waitForText("Set ${round + 1} / 3")
            compose.onNodeWithText("Log set").performScrollTo().performClick()
            if (round < 2) {
                compose.waitForText("Rest")
                if (round == 0) compose.screenshot("08-rest-timer")
                compose.onNodeWithText("Skip").performClick()
            }
        }
        compose.waitForText("Finish the session")
        compose.onNodeWithText("7").performClick()
        compose.screenshot("09-finish-dialog")
        compose.onAllNodesWithText("Finish").onLast().performClick()

        // 4. Summary, then the calendar dot
        compose.waitForText("Session summary")
        compose.waitForText("20 · 20 · 20 s")
        compose.screenshot("10-summary")
        compose.onNodeWithContentDescriptionBack()
        compose.waitForDescription("1 session completed")
        compose.screenshot("11-calendar-with-dot")

        val completed = runBlocking { sessions.observeCompleted().first() }
        assertThat(completed).hasSize(1)
        assertThat(completed.single().templateNameSnapshot).isEqualTo("Hotel")
        assertThat(completed.single().rpe).isEqualTo(7)
    }

    private fun ComposeTestRule.onNodeWithContentDescriptionBack() =
        onNode(hasContentDescription("Back")).performClick()
}

/** The app is killed mid-session: a fresh activity (new ViewModels) must resume on the same set. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = "w400dp-h860dp-xhdpi")
class ResumeAfterKillTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Inject lateinit var seeder: Seeder
    @Inject lateinit var sessions: SessionRepository
    @Inject lateinit var templates: TemplateRepository

    @Before fun setUp() {
        hilt.inject()
        runBlocking { seeder.seedIfNeeded() }
    }

    @Test fun resumesWhereItStopped() {
        val first = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForText("Start session")
        compose.onNodeWithText("Start session", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Let’s go").performClick() // template A is suggested
        compose.waitForText("Round 1 / 3")
        fun cursor() = runBlocking { sessions.observeInProgress().first()?.cursorStep }
        compose.waitForText("Set 1 / 3")
        compose.onNodeWithText("Log set").performScrollTo().performClick()
        compose.waitForDb { cursor() == 1 }
        compose.onNodeWithText("Log set").performScrollTo().performClick()
        compose.waitForDb { cursor() == 2 }
        compose.waitForText("Chair dips")

        // Kill: destroy the activity and its ViewModels; only the database survives.
        first.close()
        val running = runBlocking { sessions.observeInProgress().first() }
        assertThat(running).isNotNull()
        assertThat(running!!.cursorStep).isEqualTo(2)

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForText("Session in progress")
            compose.screenshot("12-resume-banner")
            compose.onAllNodesWithText("Resume").onFirst().performClick()
            compose.waitForText("Chair dips")
            compose.waitForText("Set 1 / 3")
            val logged = runBlocking { sessions.getWithExercises(running.id)!!.sortedExercises.take(2).map { e -> e.logs.map { it.value } } }
            assertThat(logged).containsExactly(listOf(6), listOf(6)).inOrder()
            assertThat(runBlocking { sessions.getWithExercises(running.id)!!.session.status }).isEqualTo(SessionStatus.IN_PROGRESS)
        }
    }
}
