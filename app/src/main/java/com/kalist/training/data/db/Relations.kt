package com.kalist.training.data.db

import androidx.room.Embedded
import androidx.room.Relation
import java.time.LocalDate

data class TemplateExerciseWithExercise(
    @Embedded val item: TemplateExercise,
    @Relation(parentColumn = "exerciseId", entityColumn = "id") val exercise: Exercise,
)

data class TemplateWithExercises(
    @Embedded val template: Template,
    @Relation(entity = TemplateExercise::class, parentColumn = "id", entityColumn = "templateId")
    val items: List<TemplateExerciseWithExercise>,
) {
    val sortedItems: List<TemplateExerciseWithExercise> get() = items.sortedBy { it.item.position }
}

data class TemplateSummary(
    @Embedded val template: Template,
    val exerciseCount: Int,
)

data class SessionExerciseWithLogs(
    @Embedded val sessionExercise: SessionExercise,
    @Relation(parentColumn = "exerciseId", entityColumn = "id") val exercise: Exercise,
    @Relation(parentColumn = "id", entityColumn = "sessionExerciseId") val logs: List<SetLog>,
) {
    val sortedLogs: List<SetLog> get() = logs.sortedBy { it.setIndex }
}

data class SessionWithExercises(
    @Embedded val session: Session,
    @Relation(entity = SessionExercise::class, parentColumn = "id", entityColumn = "sessionId")
    val exercises: List<SessionExerciseWithLogs>,
) {
    val sortedExercises: List<SessionExerciseWithLogs> get() = exercises.sortedBy { it.sessionExercise.position }
}

/** Best single set for an exercise. */
data class BestSet(val value: Int, val date: LocalDate)

/** Last completed date per template, for the next-template rule. */
data class TemplateLastUse(val templateId: Long, val lastDate: LocalDate)

/** One row per set, flattened for the history chart and journal export. */
data class ExerciseSetRow(
    val sessionId: Long,
    val date: LocalDate,
    val variant: String?,
    val setIndex: Int,
    val value: Int,
)
