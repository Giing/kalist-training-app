package com.kalist.training.data.repo

import com.kalist.training.data.db.Exercise
import com.kalist.training.data.db.ExerciseDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

enum class RemovalResult { DELETED, ARCHIVED }

@Singleton
class ExerciseRepository @Inject constructor(private val dao: ExerciseDao) {
    fun observeAll(): Flow<List<Exercise>> = dao.observeAll()
    fun observeActive(): Flow<List<Exercise>> = dao.observeActive()
    fun observe(id: Long): Flow<Exercise?> = dao.observe(id)
    suspend fun get(id: Long): Exercise? = dao.get(id)

    suspend fun save(exercise: Exercise): Long =
        if (exercise.id == 0L) dao.insert(exercise) else exercise.id.also { dao.update(exercise) }

    suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived)

    suspend fun isUsedInSessions(id: Long): Boolean = dao.isUsedInSessions(id)

    /** Exercises with history are archived instead of deleted, so the history stays intact. */
    suspend fun deleteOrArchive(id: Long): RemovalResult =
        if (dao.isUsedInSessions(id)) {
            dao.setArchived(id, true)
            RemovalResult.ARCHIVED
        } else {
            dao.delete(id)
            RemovalResult.DELETED
        }
}
