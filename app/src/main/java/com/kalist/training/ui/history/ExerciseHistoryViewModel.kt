package com.kalist.training.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kalist.training.data.db.Exercise
import com.kalist.training.data.db.ExerciseSetRow
import com.kalist.training.data.repo.ExerciseRepository
import com.kalist.training.data.repo.SessionRepository
import com.kalist.training.ui.navigation.ExerciseHistoryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** One past session of the exercise. */
data class HistoryEntry(val sessionId: Long, val date: LocalDate, val variant: String?, val values: List<Int>) {
    val best: Int get() = values.max()
    val total: Int get() = values.sum()
}

data class HistoryUi(val exercise: Exercise, val entries: List<HistoryEntry>) {
    val allTimeBest: HistoryEntry? get() = entries.maxWithOrNull(compareBy<HistoryEntry> { it.best }.thenByDescending { it.date })
}

object History {
    fun group(rows: List<ExerciseSetRow>): List<HistoryEntry> =
        rows.groupBy { it.sessionId }.map { (id, sets) ->
            HistoryEntry(id, sets.first().date, sets.first().variant, sets.sortedBy { it.setIndex }.map { it.value })
        }.sortedWith(compareBy({ it.date }, { it.sessionId }))
}

@HiltViewModel
class ExerciseHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    exercises: ExerciseRepository,
    sessions: SessionRepository,
) : ViewModel() {
    private val exerciseId = savedStateHandle.toRoute<ExerciseHistoryRoute>().exerciseId

    val ui: StateFlow<HistoryUi?> = combine(exercises.observe(exerciseId).filterNotNull(), sessions.observeHistory(exerciseId)) { e, rows ->
        HistoryUi(e, History.group(rows))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
