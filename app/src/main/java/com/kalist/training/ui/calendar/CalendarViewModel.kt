package com.kalist.training.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kalist.training.data.db.Session
import com.kalist.training.data.db.SessionStatus
import com.kalist.training.data.db.Template
import com.kalist.training.data.db.TemplateLastUse
import com.kalist.training.data.repo.SessionRepository
import com.kalist.training.data.repo.TemplateRepository
import com.kalist.training.data.settings.SettingsRepository
import com.kalist.training.domain.NextTemplate
import com.kalist.training.domain.WeeklyStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class CalendarUi(
    val month: YearMonth,
    val today: LocalDate,
    val selected: LocalDate,
    val gridStart: LocalDate,
    val sessionsByDate: Map<LocalDate, List<Session>>,
    val sessionsThisWeek: Int,
    val weeklyGoal: Int,
    val streakWeeks: Int,
    val templates: List<Template>,
    val suggested: Template?,
    val inProgress: Session?,
) {
    val selectedSessions: List<Session> get() = sessionsByDate[selected].orEmpty()
}

sealed interface CalendarEvent {
    data class OpenActive(val sessionId: Long) : CalendarEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val sessions: SessionRepository,
    templateRepository: TemplateRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val today get() = LocalDate.now(clock)
    private val month = MutableStateFlow(YearMonth.from(today))
    private val selected = MutableStateFlow(today)

    private val _events = Channel<CalendarEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val monthSessions = month.flatMapLatest { m ->
        val (start, end) = gridRange(m)
        sessions.observeBetween(start, end)
    }

    private val stats = combine(sessions.observeCompleted(), settingsRepository.settings, templateRepository.observeAll()) { completed, settings, templates ->
        val dates = completed.map { it.date }
        val lastUses = completed.filter { it.templateId != null }
            .groupBy { it.templateId!! }
            .map { (id, list) -> TemplateLastUse(id, list.maxOf { it.date }) }
        Stats(
            thisWeek = WeeklyStats.sessionsInWeek(dates, today),
            goal = settings.weeklyGoal,
            streak = WeeklyStats.streakWeeks(dates, today, settings.weeklyGoal),
            templates = templates,
            suggested = NextTemplate.pick(templates, lastUses),
        )
    }

    val ui: StateFlow<CalendarUi?> = combine(month, selected, monthSessions, stats, sessions.observeInProgress()) { m, sel, list, st, running ->
        CalendarUi(
            month = m,
            today = today,
            selected = sel,
            gridStart = gridRange(m).first,
            sessionsByDate = list.groupBy { it.date },
            sessionsThisWeek = st.thisWeek,
            weeklyGoal = st.goal,
            streakWeeks = st.streak,
            templates = st.templates,
            suggested = st.suggested,
            inProgress = running,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { month.value = month.value.plusMonths(1) }
    fun goToToday() { month.value = YearMonth.from(today); selected.value = today }

    fun select(date: LocalDate) {
        selected.value = date
        val m = YearMonth.from(date)
        if (m != month.value) month.value = m
    }

    fun plan(templateId: Long, date: LocalDate) = viewModelScope.launch { sessions.plan(templateId, date) }

    fun startNew(templateId: Long) = viewModelScope.launch {
        _events.send(CalendarEvent.OpenActive(sessions.startFromTemplate(templateId)))
    }

    /** Planned sessions are started by the session screen itself. */
    fun open(session: Session) = viewModelScope.launch { _events.send(CalendarEvent.OpenActive(session.id)) }

    fun skip(session: Session) = viewModelScope.launch { sessions.setStatus(session.id, SessionStatus.SKIPPED) }
    fun unskip(session: Session) = viewModelScope.launch { sessions.setStatus(session.id, SessionStatus.PLANNED) }
    fun delete(session: Session) = viewModelScope.launch { sessions.delete(session.id) }

    private data class Stats(val thisWeek: Int, val goal: Int, val streak: Int, val templates: List<Template>, val suggested: Template?)

    companion object {
        /** Six full weeks (Monday first) covering the month. */
        fun gridRange(month: YearMonth): Pair<LocalDate, LocalDate> {
            val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return start to start.plusDays(41)
        }
    }
}
