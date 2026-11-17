package com.kalist.training.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

enum class TargetType { REPS, TIME }

enum class Equipment { NONE, BAR, TABLE, CHAIR_BENCH, BAND }

enum class SessionStatus { PLANNED, IN_PROGRESS, COMPLETED, SKIPPED }

@Entity(tableName = "exercise")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val targetType: TargetType = TargetType.REPS,
    val perSide: Boolean = false,
    val easierVariant: String? = null,
    val harderVariant: String? = null,
    val equipment: Equipment = Equipment.NONE,
    val archived: Boolean = false,
    val createdAt: Instant = Instant.now(),
)

@Entity(tableName = "template")
data class Template(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val colorArgb: Int,
    val rounds: Int = 3,
    val restSeconds: Int = 90,
    /** Part of the A/B alternation used to suggest the next template. */
    @ColumnInfo(defaultValue = "1") val inRotation: Boolean = true,
    val createdAt: Instant = Instant.now(),
)

@Entity(
    tableName = "template_exercise",
    foreignKeys = [
        ForeignKey(entity = Template::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("templateId"), Index("exerciseId")],
)
data class TemplateExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: Long,
    val position: Int,
    val targetSets: Int,
    /** Reps or seconds depending on [Exercise.targetType]. */
    val targetMin: Int,
    val targetMax: Int,
    val note: String? = null,
)

@Entity(
    tableName = "session",
    foreignKeys = [
        ForeignKey(entity = Template::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("templateId"), Index("date"), Index("status")],
)
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long?,
    /** History survives template edits and deletes. */
    val templateNameSnapshot: String,
    val templateColorSnapshot: Int,
    val date: LocalDate,
    val status: SessionStatus,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
    val rpe: Int? = null,
    val notes: String? = null,
    val roundsSnapshot: Int = 3,
    val restSecondsSnapshot: Int = 90,
    /** Index of the current step in the circuit, persisted so a killed session resumes in place. */
    val cursorStep: Int = 0,
    /** When the rest timer running at the time of the last save ends, if any. */
    val restEndsAt: Instant? = null,
)

@Entity(
    tableName = "session_exercise",
    foreignKeys = [
        ForeignKey(entity = Session::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val position: Int,
    val variant: String? = null,
    val targetSetsSnapshot: Int,
    val targetMinSnapshot: Int,
    val targetMaxSnapshot: Int,
    val noteSnapshot: String? = null,
    /** Skipped for the rest of the session (schema v2). */
    @ColumnInfo(defaultValue = "0") val skipped: Boolean = false,
)

@Entity(
    tableName = "set_log",
    foreignKeys = [
        ForeignKey(entity = SessionExercise::class, parentColumns = ["id"], childColumns = ["sessionExerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["sessionExerciseId", "setIndex"], unique = true)],
)
data class SetLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionExerciseId: Long,
    /** 1-based. */
    val setIndex: Int,
    /** Reps or seconds. */
    val value: Int,
    val completedAt: Instant = Instant.now(),
)
