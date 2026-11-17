package com.kalist.training.data.repo

import androidx.room.withTransaction
import com.kalist.training.data.db.BestSet
import com.kalist.training.data.db.ExerciseSetRow
import com.kalist.training.data.db.KalistDatabase
import com.kalist.training.data.db.Session
import com.kalist.training.data.db.SessionDao
import com.kalist.training.data.db.SessionExercise
import com.kalist.training.data.db.SessionExerciseWithLogs
import com.kalist.training.data.db.SessionStatus
import com.kalist.training.data.db.SessionWithExercises
import com.kalist.training.data.db.SetLog
import com.kalist.training.data.db.TemplateDao
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val db: KalistDatabase,
    private val sessionDao: SessionDao,
    private val templateDao: TemplateDao,
    private val clock: Clock,
) {
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Session>> = sessionDao.observeBetween(from, to)
    fun observeCompleted(): Flow<List<Session>> = sessionDao.observeCompleted()
    fun observeInProgress(): Flow<Session?> = sessionDao.observeInProgress()
    fun observeWithExercises(id: Long): Flow<SessionWithExercises?> = sessionDao.observeWithExercises(id)
    fun observeHistory(exerciseId: Long): Flow<List<ExerciseSetRow>> = sessionDao.observeHistory(exerciseId)
    suspend fun getWithExercises(id: Long): SessionWithExercises? = sessionDao.getWithExercises(id)

    suspend fun previous(exerciseId: Long, excludeSessionId: Long = -1): SessionExerciseWithLogs? =
        sessionDao.previous(exerciseId, excludeSessionId)

    suspend fun best(exerciseId: Long, excludeSessionId: Long = -1): BestSet? =
        sessionDao.best(exerciseId, excludeSessionId)

    /** Plans a session; its exercises are copied from the template only when it starts. */
    suspend fun plan(templateId: Long, date: LocalDate): Long {
        val template = requireNotNull(templateDao.get(templateId)) { "Template $templateId not found" }
        return sessionDao.insert(
            Session(
                templateId = template.id,
                templateNameSnapshot = template.name,
                templateColorSnapshot = template.colorArgb,
                date = date,
                status = SessionStatus.PLANNED,
                roundsSnapshot = template.rounds,
                restSecondsSnapshot = template.restSeconds,
            )
        )
    }

    /** Creates and starts a session for today from a template. */
    suspend fun startFromTemplate(templateId: Long, date: LocalDate = LocalDate.now(clock)): Long {
        val id = plan(templateId, date)
        start(id)
        return id
    }

    /**
     * Starts a planned session: snapshots the template's current targets (later template edits
     * never change the history) and prefills each variant with the one used last time.
     */
    suspend fun start(sessionId: Long) = db.withTransaction {
        val session = requireNotNull(sessionDao.get(sessionId))
        if (session.status == SessionStatus.IN_PROGRESS || session.status == SessionStatus.COMPLETED) return@withTransaction
        val templateId = requireNotNull(session.templateId) { "The template of this session was deleted" }
        val template = requireNotNull(templateDao.getWithExercises(templateId))
        sessionDao.deleteExercises(sessionId)
        sessionDao.insertExercises(
            template.sortedItems.mapIndexed { index, entry ->
                SessionExercise(
                    sessionId = sessionId,
                    exerciseId = entry.exercise.id,
                    position = index,
                    variant = sessionDao.previous(entry.exercise.id, sessionId)?.sessionExercise?.variant,
                    targetSetsSnapshot = entry.item.targetSets,
                    targetMinSnapshot = entry.item.targetMin,
                    targetMaxSnapshot = entry.item.targetMax,
                    noteSnapshot = entry.item.note,
                )
            }
        )
        sessionDao.update(
            session.copy(
                templateNameSnapshot = template.template.name,
                templateColorSnapshot = template.template.colorArgb,
                status = SessionStatus.IN_PROGRESS,
                startedAt = Instant.now(clock),
                date = if (session.date.isAfter(LocalDate.now(clock))) LocalDate.now(clock) else session.date,
                roundsSnapshot = template.template.rounds,
                restSecondsSnapshot = template.template.restSeconds,
                cursorStep = 0,
                restEndsAt = null,
            )
        )
    }

    /** Saved immediately so a killed app never loses a logged set. */
    suspend fun logSet(sessionExerciseId: Long, setIndex: Int, value: Int) {
        sessionDao.upsertSet(
            SetLog(sessionExerciseId = sessionExerciseId, setIndex = setIndex, value = value, completedAt = Instant.now(clock))
        )
    }

    suspend fun deleteSet(sessionExerciseId: Long, setIndex: Int) = sessionDao.deleteSet(sessionExerciseId, setIndex)

    suspend fun setVariant(sessionExerciseId: Long, variant: String?) =
        sessionDao.updateVariant(sessionExerciseId, variant?.trim()?.ifBlank { null })

    suspend fun updateCursor(sessionId: Long, step: Int, restEndsAt: Instant?) =
        sessionDao.updateCursor(sessionId, step, restEndsAt)

    suspend fun finish(sessionId: Long, rpe: Int?, notes: String?) {
        val session = requireNotNull(sessionDao.get(sessionId))
        sessionDao.update(
            session.copy(
                status = SessionStatus.COMPLETED,
                finishedAt = session.finishedAt ?: Instant.now(clock),
                rpe = rpe?.coerceIn(1, 10),
                notes = notes?.trim()?.ifBlank { null },
                restEndsAt = null,
            )
        )
    }

    suspend fun updateDetails(sessionId: Long, rpe: Int?, notes: String?) {
        val session = requireNotNull(sessionDao.get(sessionId))
        sessionDao.update(session.copy(rpe = rpe?.coerceIn(1, 10), notes = notes?.trim()?.ifBlank { null }))
    }

    suspend fun setStatus(sessionId: Long, status: SessionStatus) {
        val session = requireNotNull(sessionDao.get(sessionId))
        sessionDao.update(session.copy(status = status))
    }

    suspend fun moveTo(sessionId: Long, date: LocalDate) {
        val session = requireNotNull(sessionDao.get(sessionId))
        sessionDao.update(session.copy(date = date))
    }

    suspend fun delete(sessionId: Long) = sessionDao.delete(sessionId)
}
