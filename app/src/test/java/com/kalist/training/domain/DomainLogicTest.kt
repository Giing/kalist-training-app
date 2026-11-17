package com.kalist.training.domain

import com.google.common.truth.Truth.assertThat
import com.kalist.training.data.db.Template
import com.kalist.training.data.db.TemplateLastUse
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CircuitTest {
    @Test fun `steps go round by round and drop exercises with fewer sets`() {
        val steps = Circuit.steps(listOf(2, 3, 1))
        assertThat(steps).containsExactly(
            CircuitStep(0, 1), CircuitStep(1, 1), CircuitStep(2, 1),
            CircuitStep(0, 2), CircuitStep(1, 2),
            CircuitStep(1, 3),
        ).inOrder()
        assertThat(Circuit.totalRounds(listOf(2, 3, 1))).isEqualTo(3)
    }

    @Test fun `round boundary only between rounds`() {
        assertThat(Circuit.isRoundBoundary(CircuitStep(2, 1), CircuitStep(0, 2))).isTrue()
        assertThat(Circuit.isRoundBoundary(CircuitStep(0, 1), CircuitStep(1, 1))).isFalse()
        assertThat(Circuit.isRoundBoundary(CircuitStep(0, 3), null)).isFalse()
    }

    @Test fun `empty template has no steps`() {
        assertThat(Circuit.steps(emptyList())).isEmpty()
    }
}

class ProgressionTest {
    @Test fun `suggests harder variant when every set hit the top of the range`() {
        assertThat(Progression.shouldSuggestHarder(listOf(12, 12, 13), targetSets = 3, targetMax = 12)).isTrue()
    }

    @Test fun `no hint when one set is below the top`() {
        assertThat(Progression.shouldSuggestHarder(listOf(12, 11, 12), targetSets = 3, targetMax = 12)).isFalse()
    }

    @Test fun `no hint when sets are missing`() {
        assertThat(Progression.shouldSuggestHarder(listOf(12, 12), targetSets = 3, targetMax = 12)).isFalse()
        assertThat(Progression.shouldSuggestHarder(emptyList(), targetSets = 3, targetMax = 12)).isFalse()
    }
}

class NextTemplateTest {
    private val a = Template(id = 1, name = "A", colorArgb = 0, createdAt = Instant.parse("2026-01-01T00:00:00Z"))
    private val b = Template(id = 2, name = "B", colorArgb = 0, createdAt = Instant.parse("2026-01-01T00:00:01Z"))
    private val extra = Template(id = 3, name = "Hotel", colorArgb = 0, inRotation = false)

    @Test fun `first template when nothing was done`() {
        assertThat(NextTemplate.pick(listOf(b, a), emptyList())).isEqualTo(a)
    }

    @Test fun `never used template comes before used ones`() {
        val uses = listOf(TemplateLastUse(1, LocalDate.of(2026, 1, 5)))
        assertThat(NextTemplate.pick(listOf(a, b), uses)).isEqualTo(b)
    }

    @Test fun `alternates to the least recently used`() {
        val uses = listOf(TemplateLastUse(1, LocalDate.of(2026, 1, 7)), TemplateLastUse(2, LocalDate.of(2026, 1, 5)))
        assertThat(NextTemplate.pick(listOf(a, b), uses)).isEqualTo(b)
        val uses2 = listOf(TemplateLastUse(1, LocalDate.of(2026, 1, 7)), TemplateLastUse(2, LocalDate.of(2026, 1, 9)))
        assertThat(NextTemplate.pick(listOf(a, b), uses2)).isEqualTo(a)
    }

    @Test fun `templates outside the rotation are ignored`() {
        val uses = listOf(TemplateLastUse(1, LocalDate.of(2026, 1, 7)), TemplateLastUse(2, LocalDate.of(2026, 1, 5)))
        assertThat(NextTemplate.pick(listOf(a, b, extra), uses)).isEqualTo(b)
    }

    @Test fun `falls back to all templates when none is in the rotation`() {
        assertThat(NextTemplate.pick(listOf(extra), emptyList())).isEqualTo(extra)
        assertThat(NextTemplate.pick(emptyList(), emptyList())).isNull()
    }
}

class WeeklyStatsTest {
    private val wednesday = LocalDate.of(2026, 1, 14) // week starts Monday 12th

    @Test fun `counts sessions of the current week only`() {
        val dates = listOf(LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 12), LocalDate.of(2026, 1, 14), LocalDate.of(2026, 1, 18))
        assertThat(WeeklyStats.sessionsInWeek(dates, wednesday)).isEqualTo(3)
    }

    @Test fun `streak counts consecutive weeks meeting the goal and ignores an unfinished current week`() {
        val dates = listOf(
            // week of Jan 5: 3 sessions
            LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 9),
            // week of Dec 29: 3 sessions
            LocalDate.of(2025, 12, 29), LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 2),
            // week of Dec 22: only 1 -> breaks the streak
            LocalDate.of(2025, 12, 22),
            // current week: 1 so far
            LocalDate.of(2026, 1, 12),
        )
        assertThat(WeeklyStats.streakWeeks(dates, wednesday, weeklyGoal = 3)).isEqualTo(2)
        assertThat(WeeklyStats.streakWeeks(dates, wednesday, weeklyGoal = 1)).isEqualTo(4)
    }
}
