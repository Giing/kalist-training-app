package com.kalist.training.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kalist.training.data.db.SessionExerciseWithLogs
import com.kalist.training.data.db.SessionWithExercises
import com.kalist.training.data.repo.SessionRepository
import com.kalist.training.ui.navigation.SessionDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseSummary(
    val entry: SessionExerciseWithLogs,
    val values: List<Int>,
    val total: Int,
    val bestInSession: Int?,
    /** Best before this session (excluding it), null when there was no history. */
    val previousBest: Int?,
) {
    val isPersonalBest: Boolean get() = bestInSession != null && previousBest != null && bestInSession > previousBest
}

data class SessionSummary(val data: SessionWithExercises, val exercises: List<ExerciseSummary>) {
    val personalBests: Int get() = exercises.count { it.isPersonalBest }
}

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SessionRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<SessionDetailRoute>()
    val sessionId = route.sessionId
    val justFinished = route.justFinished

    private val _deleted = Channel<Unit>(Channel.BUFFERED)
    val deleted = _deleted.receiveAsFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val summary: StateFlow<SessionSummary?> = repository.observeWithExercises(sessionId).mapLatest { data ->
        data ?: return@mapLatest null
        SessionSummary(
            data = data,
            exercises = data.sortedExercises.map { e ->
                val values = e.sortedLogs.map { it.value }
                ExerciseSummary(
                    entry = e,
                    values = values,
                    total = values.sum(),
                    bestInSession = values.maxOrNull(),
                    previousBest = repository.best(e.exercise.id, excludeSessionId = sessionId)?.value,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setValue(sessionExerciseId: Long, setIndex: Int, value: Int) = viewModelScope.launch {
        repository.logSet(sessionExerciseId, setIndex, value.coerceAtLeast(0))
    }

    fun removeSet(sessionExerciseId: Long, setIndex: Int) = viewModelScope.launch {
        repository.deleteSet(sessionExerciseId, setIndex)
    }

    fun saveDetails(rpe: Int?, notes: String?) = viewModelScope.launch { repository.updateDetails(sessionId, rpe, notes) }

    fun setVariant(sessionExerciseId: Long, variant: String) = viewModelScope.launch { repository.setVariant(sessionExerciseId, variant) }

    fun delete() = viewModelScope.launch {
        repository.delete(sessionId)
        _deleted.send(Unit)
    }
}
