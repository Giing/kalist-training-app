package com.kalist.training.data.backup

import com.kalist.training.data.db.Equipment
import com.kalist.training.data.db.Exercise
import com.kalist.training.data.db.Session
import com.kalist.training.data.db.SessionExercise
import com.kalist.training.data.db.SessionStatus
import com.kalist.training.data.db.SetLog
import com.kalist.training.data.db.TargetType
import com.kalist.training.data.db.Template
import com.kalist.training.data.db.TemplateExercise
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/**
 * JSON backup format. Instants are epoch milliseconds, dates are ISO-8601 strings. Bump [VERSION]
 * and keep reading older versions when the format changes.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: Long,
    val exercises: List<ExerciseDto>,
    val templates: List<TemplateDto>,
    val templateExercises: List<TemplateExerciseDto>,
    val sessions: List<SessionDto>,
    val sessionExercises: List<SessionExerciseDto>,
    val setLogs: List<SetLogDto>,
) {
    companion object {
        const val FORMAT = "kalist-backup"
        const val VERSION = 1
    }
}

@Serializable
data class ExerciseDto(
    val id: Long, val name: String, val description: String? = null, val targetType: String,
    val perSide: Boolean, val easierVariant: String? = null, val harderVariant: String? = null,
    val equipment: String, val archived: Boolean, val createdAt: Long,
)

@Serializable
data class TemplateDto(
    val id: Long, val name: String, val description: String? = null, val colorArgb: Int,
    val rounds: Int, val restSeconds: Int, val inRotation: Boolean = true, val createdAt: Long,
)

@Serializable
data class TemplateExerciseDto(
    val id: Long, val templateId: Long, val exerciseId: Long, val position: Int,
    val targetSets: Int, val targetMin: Int, val targetMax: Int, val note: String? = null,
)

@Serializable
data class SessionDto(
    val id: Long, val templateId: Long? = null, val templateNameSnapshot: String, val templateColorSnapshot: Int,
    val date: String, val status: String, val startedAt: Long? = null, val finishedAt: Long? = null,
    val rpe: Int? = null, val notes: String? = null, val roundsSnapshot: Int = 3, val restSecondsSnapshot: Int = 90,
    val cursorStep: Int = 0,
)

@Serializable
data class SessionExerciseDto(
    val id: Long, val sessionId: Long, val exerciseId: Long, val position: Int, val variant: String? = null,
    val targetSetsSnapshot: Int, val targetMinSnapshot: Int, val targetMaxSnapshot: Int,
    val noteSnapshot: String? = null, val skipped: Boolean = false,
)

@Serializable
data class SetLogDto(val id: Long, val sessionExerciseId: Long, val setIndex: Int, val value: Int, val completedAt: Long)

private inline fun <reified E : Enum<E>> String.toEnumOr(default: E): E = enumValues<E>().firstOrNull { it.name == this } ?: default

fun Exercise.toDto() = ExerciseDto(id, name, description, targetType.name, perSide, easierVariant, harderVariant, equipment.name, archived, createdAt.toEpochMilli())
fun ExerciseDto.toEntity() = Exercise(id, name, description, targetType.toEnumOr(TargetType.REPS), perSide, easierVariant, harderVariant,
    equipment.toEnumOr(Equipment.NONE), archived, Instant.ofEpochMilli(createdAt))

fun Template.toDto() = TemplateDto(id, name, description, colorArgb, rounds, restSeconds, inRotation, createdAt.toEpochMilli())
fun TemplateDto.toEntity() = Template(id, name, description, colorArgb, rounds, restSeconds, inRotation, Instant.ofEpochMilli(createdAt))

fun TemplateExercise.toDto() = TemplateExerciseDto(id, templateId, exerciseId, position, targetSets, targetMin, targetMax, note)
fun TemplateExerciseDto.toEntity() = TemplateExercise(id, templateId, exerciseId, position, targetSets, targetMin, targetMax, note)

fun Session.toDto() = SessionDto(id, templateId, templateNameSnapshot, templateColorSnapshot, date.toString(), status.name,
    startedAt?.toEpochMilli(), finishedAt?.toEpochMilli(), rpe, notes, roundsSnapshot, restSecondsSnapshot, cursorStep)
fun SessionDto.toEntity() = Session(id, templateId, templateNameSnapshot, templateColorSnapshot, LocalDate.parse(date),
    status.toEnumOr(SessionStatus.COMPLETED), startedAt?.let(Instant::ofEpochMilli), finishedAt?.let(Instant::ofEpochMilli),
    rpe, notes, roundsSnapshot, restSecondsSnapshot, cursorStep, restEndsAt = null)

fun SessionExercise.toDto() = SessionExerciseDto(id, sessionId, exerciseId, position, variant, targetSetsSnapshot, targetMinSnapshot, targetMaxSnapshot, noteSnapshot, skipped)
fun SessionExerciseDto.toEntity() = SessionExercise(id, sessionId, exerciseId, position, variant, targetSetsSnapshot, targetMinSnapshot, targetMaxSnapshot, noteSnapshot, skipped)

fun SetLog.toDto() = SetLogDto(id, sessionExerciseId, setIndex, value, completedAt.toEpochMilli())
fun SetLogDto.toEntity() = SetLog(id, sessionExerciseId, setIndex, value, Instant.ofEpochMilli(completedAt))
