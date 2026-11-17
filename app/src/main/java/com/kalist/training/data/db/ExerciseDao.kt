package com.kalist.training.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise ORDER BY archived ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercise WHERE archived = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun get(id: Long): Exercise?

    @Query("SELECT * FROM exercise WHERE id = :id")
    fun observe(id: Long): Flow<Exercise?>

    @Query("SELECT * FROM exercise")
    suspend fun getAll(): List<Exercise>

    @Query("SELECT COUNT(*) FROM exercise")
    suspend fun count(): Int

    @Insert
    suspend fun insert(exercise: Exercise): Long

    @Insert
    suspend fun insertAll(exercises: List<Exercise>): List<Long>

    @Update
    suspend fun update(exercise: Exercise)

    @Query("DELETE FROM exercise WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE exercise SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    /** True when the exercise appears in any session (planned sessions have no exercises yet). */
    @Query("SELECT EXISTS(SELECT 1 FROM session_exercise WHERE exerciseId = :id)")
    suspend fun isUsedInSessions(id: Long): Boolean
}
