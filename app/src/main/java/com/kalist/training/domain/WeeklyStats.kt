package com.kalist.training.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object WeeklyStats {
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun sessionsInWeek(completedDates: List<LocalDate>, today: LocalDate): Int {
        val start = weekStart(today)
        val end = start.plusDays(6)
        return completedDates.count { !it.isBefore(start) && !it.isAfter(end) }
    }

    /**
     * Consecutive weeks (Monday to Sunday) in which the weekly goal was reached, counting back from
     * the current week. The current week counts once its goal is reached, but an unfinished current
     * week never breaks the streak.
     */
    fun streakWeeks(completedDates: List<LocalDate>, today: LocalDate, weeklyGoal: Int): Int {
        val perWeek = completedDates.groupingBy { weekStart(it) }.eachCount()
        val goal = weeklyGoal.coerceAtLeast(1)
        var week = weekStart(today)
        var streak = 0
        if ((perWeek[week] ?: 0) >= goal) streak++
        week = week.minusWeeks(1)
        while ((perWeek[week] ?: 0) >= goal) {
            streak++
            week = week.minusWeeks(1)
        }
        return streak
    }
}
