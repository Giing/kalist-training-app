package com.kalist.training.ui.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kalist.training.data.db.Equipment
import com.kalist.training.data.db.Exercise
import com.kalist.training.data.db.TargetType
import com.kalist.training.data.repo.ExerciseRepository
import com.kalist.training.data.repo.RemovalResult
import com.kalist.training.ui.navigation.ExerciseEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExercisesViewModel @Inject constructor(repository: ExerciseRepository) : ViewModel() {
    private val showArchived = MutableStateFlow(false)

    val state: StateFlow<ExercisesState> = combine(repository.observeAll(), showArchived) { all, archived ->
        ExercisesState(
            active = all.filterNot { it.archived },
            archived = all.filter { it.archived },
            showArchived = archived,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExercisesState())

    fun toggleArchived() = showArchived.update { !it }
}

data class ExercisesState(
    val active: List<Exercise> = emptyList(),
    val archived: List<Exercise> = emptyList(),
    val showArchived: Boolean = false,
    val loading: Boolean = true,
)

data class ExerciseForm(
    val name: String = "",
    val description: String = "",
    val targetType: TargetType = TargetType.REPS,
    val perSide: Boolean = false,
    val easierVariant: String = "",
    val harderVariant: String = "",
    val equipment: Equipment = Equipment.NONE,
    val archived: Boolean = false,
    val showNameError: Boolean = false,
) {
    val isValid get() = name.isNotBlank()
}

sealed interface ExerciseEditEvent {
    data object Saved : ExerciseEditEvent
    data class Removed(val result: RemovalResult) : ExerciseEditEvent
}

@HiltViewModel
class ExerciseEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExerciseRepository,
) : ViewModel() {
    val exerciseId: Long = savedStateHandle.toRoute<ExerciseEditRoute>().id
    val isNew get() = exerciseId == 0L

    private var original: Exercise? = null
    private val _form = MutableStateFlow(ExerciseForm())
    val form: StateFlow<ExerciseForm> = _form.asStateFlow()

    private val _usedInSessions = MutableStateFlow(false)
    val usedInSessions: StateFlow<Boolean> = _usedInSessions.asStateFlow()

    private val _events = Channel<ExerciseEditEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        if (!isNew) viewModelScope.launch {
            repository.get(exerciseId)?.let { e ->
                original = e
                _form.value = ExerciseForm(
                    name = e.name,
                    description = e.description.orEmpty(),
                    targetType = e.targetType,
                    perSide = e.perSide,
                    easierVariant = e.easierVariant.orEmpty(),
                    harderVariant = e.harderVariant.orEmpty(),
                    equipment = e.equipment,
                    archived = e.archived,
                )
            }
            _usedInSessions.value = repository.isUsedInSessions(exerciseId)
        }
    }

    fun update(transform: (ExerciseForm) -> ExerciseForm) = _form.update(transform)

    fun save() {
        val f = _form.value
        if (!f.isValid) {
            _form.update { it.copy(showNameError = true) }
            return
        }
        viewModelScope.launch {
            val base = original ?: Exercise(name = f.name)
            repository.save(
                base.copy(
                    name = f.name.trim(),
                    description = f.description.trim().ifBlank { null },
                    targetType = f.targetType,
                    perSide = f.perSide,
                    easierVariant = f.easierVariant.trim().ifBlank { null },
                    harderVariant = f.harderVariant.trim().ifBlank { null },
                    equipment = f.equipment,
                    archived = f.archived,
                )
            )
            _events.send(ExerciseEditEvent.Saved)
        }
    }

    fun setArchived(archived: Boolean) = viewModelScope.launch {
        repository.setArchived(exerciseId, archived)
        _form.update { it.copy(archived = archived) }
        original = original?.copy(archived = archived)
    }

    fun delete() = viewModelScope.launch {
        _events.send(ExerciseEditEvent.Removed(repository.deleteOrArchive(exerciseId)))
    }
}
