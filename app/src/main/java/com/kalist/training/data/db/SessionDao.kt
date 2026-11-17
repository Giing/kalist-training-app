package com.kalist.training.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface SessionDao {
    // ---- Sessions ----

    @Insert
    suspend fun insert(session: Session): Long

    @Update
    suspend fun update(session: Session)

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun get(id: Long): Session?

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM session WHERE date BETWEEN :from AND :to ORDER BY date ASC, id ASC")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Session>>

    @Query("SELECT * FROM session WHERE status = 'COMPLETED' ORDER BY date ASC")
    fun observeCompleted(): Flow<List<Session>>

    @Query("SELECT * FROM session WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    fun observeInProgress(): Flow<Session?>

    @Transaction
    @Query("SELECT * FROM session WHERE id = :id")
    fun observeWithExercises(id: Long): Flow<SessionWithExercises?>

    @Transaction
    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun getWithExercises(id: Long): SessionWithExercises?

    @Query("UPDATE session SET cursorStep = :step, restEndsAt = :restEndsAt WHERE id = :id")
    suspend fun updateCursor(id: Long, step: Int, restEndsAt: Instant?)

    @Query("SELECT * FROM session")
    suspend fun getAll(): List<Session>

    // ---- Session exercises & sets ----

    @Insert
    suspend fun insertExercises(items: List<SessionExercise>): List<Long>

    @Query("DELETE FROM session_exercise WHERE sessionId = :sessionId")
    suspend fun deleteExercises(sessionId: Long)

    @Query("UPDATE session_exercise SET variant = :variant WHERE id = :id")
    suspend fun updateVariant(id: Long, variant: String?)

    @Query("UPDATE session_exercise SET skipped = :skipped WHERE id = :id")
    suspend fun updateSkipped(id: Long, skipped: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSet(log: SetLog): Long

    @Query("DELETE FROM set_log WHERE sessionExerciseId = :sessionExerciseId AND setIndex = :setIndex")
    suspend fun deleteSet(sessionExerciseId: Long, setIndex: Int)

    @Query("SELECT * FROM session_exercise")
    suspend fun getAllExercises(): List<SessionExercise>

    @Query("SELECT * FROM set_log")
    suspend fun getAllSets(): List<SetLog>

    @Insert
    suspend fun insertSets(items: List<SetLog>)

    // ---- Derived queries ----

    /**
     * The most recent completed session containing the exercise (with at least one logged set),
     * optionally excluding the session being run.
     */
    @Transaction
    @Query(
        """
        SELECT se.* FROM session_exercise se
        JOIN session s ON s.id = se.sessionId
        WHERE se.exerciseId = :exerciseId AND s.status = 'COMPLETED' AND s.id != :excludeSessionId
          AND EXISTS(SELECT 1 FROM set_log sl WHERE sl.sessionExerciseId = se.id)
        ORDER BY s.date DESC, s.finishedAt DESC, se.id DESC
        LIMIT 1
        """
    )
    suspend fun previous(exerciseId: Long, excludeSessionId: Long = -1): SessionExerciseWithLogs?

    /** Best single set ever (earliest date that reached it) across completed sessions. */
    @Query(
        """
        SELECT sl.value AS value, s.date AS date FROM set_log sl
        JOIN session_exercise se ON se.id = sl.sessionExerciseId
        JOIN session s ON s.id = se.sessionId
        WHERE se.exerciseId = :exerciseId AND s.status = 'COMPLETED' AND s.id != :excludeSessionId
        ORDER BY sl.value DESC, s.date ASC
        LIMIT 1
        """
    )
    suspend fun best(exerciseId: Long, excludeSessionId: Long = -1): BestSet?

    /** Every logged set of an exercise in completed sessions, oldest first. */
    @Query(
        """
        SELECT s.id AS sessionId, s.date AS date, se.variant AS variant, sl.setIndex AS setIndex, sl.value AS value
        FROM set_log sl
        JOIN session_exercise se ON se.id = sl.sessionExerciseId
        JOIN session s ON s.id = se.sessionId
        WHERE se.exerciseId = :exerciseId AND s.status = 'COMPLETED'
        ORDER BY s.date ASC, s.id ASC, sl.setIndex ASC
        """
    )
    fun observeHistory(exerciseId: Long): Flow<List<ExerciseSetRow>>
}
