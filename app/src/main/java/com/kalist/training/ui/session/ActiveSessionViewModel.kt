package com.kalist.training.ui.session

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kalist.training.data.db.BestSet
import com.kalist.training.data.db.SessionExerciseWithLogs
import com.kalist.training.data.db.SessionStatus
import com.kalist.training.data.db.SessionWithExercises
import com.kalist.training.data.db.TargetType
import com.kalist.training.data.repo.SessionRepository
import com.kalist.training.domain.Circuit
import com.kalist.training.domain.CircuitStep
import com.kalist.training.domain.Prefill
import com.kalist.training.domain.Progression
import com.kalist.training.ui.navigation.ActiveSessionRoute
import com.kalist.training.util.Haptics
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** Previous / Goal / Best for one exercise, computed once when the screen opens. */
data class ExerciseReference(
    val previousValues: List<Int>,
    val previousVariant: String?,
    val best: BestSet?,
)

/** The timer used to log TIME exercises: a 3 s lead-in, then a stopwatch. */
data class Stopwatch(val startedAt: Instant, val leadInSeconds: Int = 3)

data class ActiveSessionUi(
    val data: SessionWithExercises,
    val exercises: List<SessionExerciseWithLogs>,
    val steps: List<CircuitStep>,
    val cursor: Int,
    val references: Map<Long, ExerciseReference>,
    val input: Int,
    val restEndsAt: Instant?,
    val stopwatch: Stopwatch?,
    val now: Instant,
) {
    val step: CircuitStep? get() = steps.getOrNull(cursor)
    val current: SessionExerciseWithLogs? get() = step?.let { exercises.getOrNull(it.exerciseIndex) }
    val totalRounds: Int get() = Circuit.totalRounds(exercises.map { it.sessionExercise.targetSetsSnapshot })
    val reference: ExerciseReference? get() = current?.let { references[it.exercise.id] }

    val restRemaining: Long?
        get() = restEndsAt?.let { Duration.between(now, it).seconds + if (Duration.between(now, it).nano > 0) 1 else 0 }
            ?.takeIf { it > 0 }

    /** Stopwatch: negative while in the lead-in. */
    val stopwatchSeconds: Long?
        get() = stopwatch?.let { Duration.between(it.startedAt, now).seconds - it.leadInSeconds }

    val suggestHarder: Boolean
        get() {
            val c = current ?: return false
            val ref = reference ?: return false
            return !c.exercise.harderVariant.isNullOrBlank() &&
                Progression.shouldSuggestHarder(ref.previousValues, c.sessionExercise.targetSetsSnapshot, c.sessionExercise.targetMaxSnapshot)
        }

    fun loggedValue(exerciseIndex: Int, setIndex: Int): Int? =
        exercises.getOrNull(exerciseIndex)?.logs?.firstOrNull { it.setIndex == setIndex }?.value

    /** Steps still to do (not logged, exercise not skipped). */
    val remainingSteps: Int
        get() = steps.count { s ->
            val e = exercises[s.exerciseIndex]
            !e.sessionExercise.skipped && loggedValue(s.exerciseIndex, s.round) == null
        }
}

sealed interface ActiveSessionEvent {
    data object Finished : ActiveSessionEvent
    data object Abandoned : ActiveSessionEvent
    data object AskFinish : ActiveSessionEvent
}

@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SessionRepository,
    private val clock: Clock,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val sessionId: Long = savedStateHandle.toRoute<ActiveSessionRoute>().sessionId

    private val references = MutableStateFlow<Map<Long, ExerciseReference>?>(null)
    private val cursor = MutableStateFlow<Int?>(null)
    private val input = MutableStateFlow<Int?>(null)
    private val restEndsAt = MutableStateFlow<Instant?>(null)
    private val stopwatch = MutableStateFlow<Stopwatch?>(null)
    private val now = MutableStateFlow(Instant.now(clock))
    private var tickerJob: Job? = null
    private var restAlarmFired = false
    private var targetAlarmFired = false

    private val _events = Channel<ActiveSessionEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val sessionFlow = repository.observeWithExercises(sessionId)

    val ui: StateFlow<ActiveSessionUi?> = combine(
        combine(sessionFlow.filterNotNull(), references.filterNotNull(), cursor.filterNotNull()) { s, r, c -> Triple(s, r, c) },
        input, restEndsAt, stopwatch, now,
    ) { (data, refs, cur), inp, rest, sw, t ->
        val exercises = data.sortedExercises
        val steps = Circuit.steps(exercises.map { it.sessionExercise.targetSetsSnapshot })
        val base = ActiveSessionUi(data, exercises, steps, cur.coerceIn(0, maxOf(steps.lastIndex, 0)), refs, 0, rest, sw, t)
        base.copy(input = inp ?: defaultInput(base))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val data = sessionFlow.filterNotNull().first()
            if (data.session.status == SessionStatus.PLANNED) repository.start(sessionId)
            val started = sessionFlow.filterNotNull().first { it.session.status != SessionStatus.PLANNED }
            references.value = started.exercises.associate { e ->
                val previous = repository.previous(e.exercise.id, sessionId)
                e.exercise.id to ExerciseReference(
                    previousValues = previous?.sortedLogs?.map { it.value }.orEmpty(),
                    previousVariant = previous?.sessionExercise?.variant,
                    best = repository.best(e.exercise.id, sessionId),
                )
            }
            // Resume exactly where the session stopped, including a running rest timer.
            restEndsAt.value = started.session.restEndsAt?.takeIf { it.isAfter(Instant.now(clock)) }
            restAlarmFired = restEndsAt.value == null
            cursor.value = started.session.cursorStep
            ensureTicker()
        }
    }

    private fun defaultInput(ui: ActiveSessionUi): Int {
        val step = ui.step ?: return 0
        val current = ui.current ?: return 0
        val ref = ui.references[current.exercise.id]
        return Prefill.value(
            logged = ui.loggedValue(step.exerciseIndex, step.round),
            previousValues = ref?.previousValues.orEmpty(),
            setIndex = step.round,
            targetMin = current.sessionExercise.targetMinSnapshot,
        )
    }

    fun setInput(value: Int) { input.value = value.coerceIn(0, 9999) }

    fun setVariant(sessionExerciseId: Long, variant: String) = viewModelScope.launch {
        repository.setVariant(sessionExerciseId, variant)
    }

    /** Logs the current set (saved immediately) and moves on. */
    fun logCurrent() = viewModelScope.launch {
        val ui = ui.value ?: return@launch
        val step = ui.step ?: return@launch
        val current = ui.current ?: return@launch
        stopwatch.value = null
        repository.logSet(current.sessionExercise.id, step.round, ui.input)
        val updated = sessionFlow.filterNotNull().first { s ->
            s.exercises.any { e -> e.sessionExercise.id == current.sessionExercise.id && e.logs.any { it.setIndex == step.round && it.value == ui.input } }
        }
        advanceFrom(ui.copy(data = updated, exercises = updated.sortedExercises), startRestOnRoundChange = true)
    }

    /** Skips the current set without logging it. */
    fun skipSet() = viewModelScope.launch {
        val ui = ui.value ?: return@launch
        stopwatch.value = null
        advanceFrom(ui, startRestOnRoundChange = false)
    }

    /** Skips the current exercise for the rest of the session. */
    fun skipExercise() = viewModelScope.launch {
        val ui = ui.value ?: return@launch
        val current = ui.current ?: return@launch
        stopwatch.value = null
        repository.setSkipped(current.sessionExercise.id, true)
        val updated = sessionFlow.filterNotNull().first { s -> s.exercises.any { it.sessionExercise.id == current.sessionExercise.id && it.sessionExercise.skipped } }
        advanceFrom(ui.copy(data = updated, exercises = updated.sortedExercises), startRestOnRoundChange = false)
    }

    fun unskip(sessionExerciseId: Long) = viewModelScope.launch { repository.setSkipped(sessionExerciseId, false) }

    /** Jumps to the first not-yet-logged set of an exercise (from the exercise list). */
    fun jumpTo(exerciseIndex: Int) = viewModelScope.launch {
        val ui = ui.value ?: return@launch
        val target = ui.steps.indices.firstOrNull { i ->
            val s = ui.steps[i]
            s.exerciseIndex == exerciseIndex && ui.loggedValue(s.exerciseIndex, s.round) == null
        } ?: ui.steps.indices.lastOrNull { ui.steps[it].exerciseIndex == exerciseIndex } ?: return@launch
        val e = ui.exercises[exerciseIndex]
        if (e.sessionExercise.skipped) repository.setSkipped(e.sessionExercise.id, false)
        moveCursor(target, rest = null)
    }

    fun previousStep() = viewModelScope.launch {
        val ui = ui.value ?: return@launch
        moveCursor((ui.cursor - 1).coerceAtLeast(0), rest = null)
    }

    private suspend fun advanceFrom(ui: ActiveSessionUi, startRestOnRoundChange: Boolean) {
        val from = ui.cursor
        val nextIndex = (from + 1 until ui.steps.size).firstOrNull { i ->
            val s = ui.steps[i]
            !ui.exercises[s.exerciseIndex].sessionExercise.skipped && ui.loggedValue(s.exerciseIndex, s.round) == null
        } ?: (0 until ui.steps.size).firstOrNull { i ->
            // Wrap around to anything left unlogged earlier (e.g. skipped sets).
            val s = ui.steps[i]
            i != from && !ui.exercises[s.exerciseIndex].sessionExercise.skipped && ui.loggedValue(s.exerciseIndex, s.round) == null
        }
        if (nextIndex == null) {
            moveCursor(from, rest = null)
            _events.send(ActiveSessionEvent.AskFinish)
            return
        }
        val roundChanged = ui.steps[nextIndex].round != ui.steps[from].round
        val restSeconds = ui.data.session.restSecondsSnapshot
        val rest = if (startRestOnRoundChange && roundChanged && restSeconds > 0) Instant.now(clock).plusSeconds(restSeconds.toLong()) else null
        moveCursor(nextIndex, rest)
    }

    private suspend fun moveCursor(index: Int, rest: Instant?) {
        cursor.value = index
        input.value = null
        restEndsAt.value = rest
        restAlarmFired = rest == null
        repository.updateCursor(sessionId, index, rest)
        ensureTicker()
    }

    fun skipRest() = viewModelScope.launch {
        restEndsAt.value = null
        restAlarmFired = true
        repository.updateCursor(sessionId, cursor.value ?: 0, null)
    }

    fun addRest(seconds: Long) = viewModelScope.launch {
        val end = (restEndsAt.value ?: Instant.now(clock)).plusSeconds(seconds)
        restEndsAt.value = end
        restAlarmFired = false
        repository.updateCursor(sessionId, cursor.value ?: 0, end)
        ensureTicker()
    }

    fun startStopwatch() {
        stopwatch.value = Stopwatch(Instant.now(clock))
        targetAlarmFired = false
        ensureTicker()
    }

    /** Stops the stopwatch and puts the measured time in the input. */
    fun stopStopwatch() {
        val seconds = ui.value?.stopwatchSeconds ?: return
        stopwatch.value = null
        input.value = seconds.coerceAtLeast(0).toInt()
    }

    fun cancelStopwatch() { stopwatch.value = null }

    private fun ensureTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (true) {
                now.value = Instant.now(clock)
                checkAlarms()
                if (restEndsAt.value == null && stopwatch.value == null) break
                delay(200)
            }
        }
    }

    private fun checkAlarms() {
        val t = now.value
        restEndsAt.value?.let { end ->
            if (!restAlarmFired && !t.isBefore(end)) {
                restAlarmFired = true
                Haptics.alarm(context)
                viewModelScope.launch { skipRest() }
            }
        }
        val ui = ui.value ?: return
        val sw = ui.stopwatchSeconds ?: return
        val current = ui.current ?: return
        if (current.exercise.targetType == TargetType.TIME && !targetAlarmFired && sw >= ui.input && ui.input > 0) {
            targetAlarmFired = true
            Haptics.alarm(context)
        } else if (sw in -3..-1) {
            // Lead-in ticks are fine to repeat-guard by second.
            if (lastTick != sw) { lastTick = sw; Haptics.tick(context) }
        }
    }
    private var lastTick: Long? = null

    fun finish(rpe: Int?, notes: String?) = viewModelScope.launch {
        repository.finish(sessionId, rpe, notes)
        _events.send(ActiveSessionEvent.Finished)
    }

    fun abandon() = viewModelScope.launch {
        repository.delete(sessionId)
        _events.send(ActiveSessionEvent.Abandoned)
    }

    fun requestFinish() = viewModelScope.launch { _events.send(ActiveSessionEvent.AskFinish) }
}
