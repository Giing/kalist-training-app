package com.kalist.training.ui.templates

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kalist.training.R
import com.kalist.training.data.db.Exercise
import com.kalist.training.data.db.Template
import com.kalist.training.data.db.TemplateExercise
import com.kalist.training.data.db.TemplateSummary
import com.kalist.training.data.repo.ExerciseRepository
import com.kalist.training.data.repo.TemplateRepository
import com.kalist.training.data.settings.SettingsRepository
import com.kalist.training.ui.navigation.TemplateEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/** Colors offered for templates (calendar dots and cards). */
val TemplateColors: List<Int> = listOf(
    0xFFE8572A, 0xFF1F7A8C, 0xFF4F5BD5, 0xFF2E8B57, 0xFFD6336C,
    0xFF8E44AD, 0xFFF2A007, 0xFF6D4C41, 0xFF00897B, 0xFF546E7A,
).map { it.toInt() }

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val repository: TemplateRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val templates: StateFlow<List<TemplateSummary>?> = repository.observeSummaries()
        .map { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun duplicate(template: Template) = viewModelScope.launch {
        repository.duplicate(template.id, context.getString(R.string.template_copy_name, template.name))
    }

    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }
}

/** One exercise row in the editor. [key] is a stable local id used for drag-and-drop. */
data class DraftItem(
    val key: Long,
    val exercise: Exercise,
    val targetSets: Int,
    val targetMin: Int,
    val targetMax: Int,
    val note: String,
)

data class TemplateForm(
    val name: String = "",
    val description: String = "",
    val colorArgb: Int = TemplateColors.first(),
    val rounds: Int = 3,
    val restSeconds: Int = 90,
    val inRotation: Boolean = true,
    val items: List<DraftItem> = emptyList(),
    val showNameError: Boolean = false,
    val loaded: Boolean = false,
)

@HiltViewModel
class TemplateEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TemplateRepository,
    exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val templateId: Long = savedStateHandle.toRoute<TemplateEditRoute>().id
    val isNew get() = templateId == 0L

    private var original: Template? = null
    private val keys = AtomicLong(1)
    private val _form = MutableStateFlow(TemplateForm())
    val form: StateFlow<TemplateForm> = _form.asStateFlow()

    val pickableExercises: StateFlow<List<Exercise>> =
        exerciseRepository.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    init {
        viewModelScope.launch {
            if (isNew) {
                val rest = settingsRepository.settings.first().defaultRestSeconds
                // New templates stay out of the A/B rotation unless the user opts in.
                _form.update { it.copy(restSeconds = rest, inRotation = false, loaded = true) }
            } else {
                val t = repository.getWithExercises(templateId) ?: return@launch
                original = t.template
                _form.value = TemplateForm(
                    name = t.template.name,
                    description = t.template.description.orEmpty(),
                    colorArgb = t.template.colorArgb,
                    rounds = t.template.rounds,
                    restSeconds = t.template.restSeconds,
                    inRotation = t.template.inRotation,
                    items = t.sortedItems.map {
                        DraftItem(keys.getAndIncrement(), it.exercise, it.item.targetSets, it.item.targetMin, it.item.targetMax, it.item.note.orEmpty())
                    },
                    loaded = true,
                )
            }
        }
    }

    fun update(transform: (TemplateForm) -> TemplateForm) = _form.update(transform)

    /** Changing the rounds also updates exercises that followed the previous round count. */
    fun setRounds(rounds: Int) = _form.update { f ->
        f.copy(rounds = rounds, items = f.items.map { if (it.targetSets == f.rounds) it.copy(targetSets = rounds) else it })
    }

    fun addExercise(exercise: Exercise) = _form.update { f ->
        val (min, max) = if (exercise.targetType == com.kalist.training.data.db.TargetType.TIME) 20 to 30 else 6 to 10
        f.copy(items = f.items + DraftItem(keys.getAndIncrement(), exercise, f.rounds, min, max, ""))
    }

    fun updateItem(key: Long, transform: (DraftItem) -> DraftItem) = _form.update { f ->
        f.copy(items = f.items.map { if (it.key == key) transform(it) else it })
    }

    fun removeItem(key: Long) = _form.update { f -> f.copy(items = f.items.filterNot { it.key == key }) }

    fun move(fromKey: Any, toKey: Any) = _form.update { f ->
        val from = f.items.indexOfFirst { it.key == fromKey }
        val to = f.items.indexOfFirst { it.key == toKey }
        if (from < 0 || to < 0) f else f.copy(items = f.items.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun moveBy(key: Long, delta: Int) = _form.update { f ->
        val from = f.items.indexOfFirst { it.key == key }
        val to = (from + delta).coerceIn(0, f.items.lastIndex)
        if (from < 0 || from == to) f else f.copy(items = f.items.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun save() {
        val f = _form.value
        if (f.name.isBlank()) {
            _form.update { it.copy(showNameError = true) }
            return
        }
        viewModelScope.launch {
            val base = original ?: Template(name = f.name, colorArgb = f.colorArgb)
            repository.save(
                base.copy(
                    name = f.name.trim(),
                    description = f.description.trim().ifBlank { null },
                    colorArgb = f.colorArgb,
                    rounds = f.rounds,
                    restSeconds = f.restSeconds,
                    inRotation = f.inRotation,
                ),
                f.items.map {
                    TemplateExercise(
                        templateId = templateId,
                        exerciseId = it.exercise.id,
                        position = 0,
                        targetSets = it.targetSets,
                        targetMin = minOf(it.targetMin, it.targetMax),
                        targetMax = maxOf(it.targetMin, it.targetMax),
                        note = it.note.trim().ifBlank { null },
                    )
                },
            )
            _saved.send(Unit)
        }
    }
}
