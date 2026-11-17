package com.kalist.training.data.backup

import androidx.room.withTransaction
import com.kalist.training.data.db.KalistDatabase
import com.kalist.training.data.db.SessionStatus
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class ImportMode { REPLACE, MERGE }

data class ImportResult(val exercises: Int, val templates: Int, val sessions: Int)

class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Singleton
class BackupRepository @Inject constructor(
    private val db: KalistDatabase,
    private val clock: Clock,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun snapshot(): BackupFile = db.withTransaction {
        BackupFile(
            exportedAt = Instant.now(clock).toEpochMilli(),
            exercises = db.exerciseDao().getAll().map { it.toDto() },
            templates = db.templateDao().getAll().map { it.toDto() },
            templateExercises = db.templateDao().getAllItems().map { it.toDto() },
            sessions = db.sessionDao().getAll().map { it.toDto() },
            sessionExercises = db.sessionDao().getAllExercises().map { it.toDto() },
            setLogs = db.sessionDao().getAllSets().map { it.toDto() },
        )
    }

    suspend fun exportJson(): String = json.encodeToString(BackupFile.serializer(), snapshot())

    fun parse(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw InvalidBackupException("Not a Kalist backup", e)
        }
        if (file.format != BackupFile.FORMAT) throw InvalidBackupException("Unknown format ${file.format}")
        if (file.version > BackupFile.VERSION) throw InvalidBackupException("Backup version ${file.version} is newer than this app")
        return file
    }

    suspend fun import(text: String, mode: ImportMode): ImportResult {
        val file = parse(text)
        return when (mode) {
            ImportMode.REPLACE -> replace(file)
            ImportMode.MERGE -> merge(file)
        }
    }

    /** Wipes everything and restores the backup with its original ids. */
    private suspend fun replace(file: BackupFile): ImportResult = db.withTransaction {
        db.clearAllTables()
        val exerciseDao = db.exerciseDao()
        val templateDao = db.templateDao()
        val sessionDao = db.sessionDao()
        exerciseDao.insertAll(file.exercises.map { it.toEntity() })
        file.templates.forEach { templateDao.insert(it.toEntity()) }
        templateDao.insertItems(file.templateExercises.map { it.toEntity() })
        file.sessions.forEach { sessionDao.insert(it.toEntity()) }
        sessionDao.insertExercises(file.sessionExercises.map { it.toEntity() })
        sessionDao.insertSets(file.setLogs.map { it.toEntity() })
        ImportResult(file.exercises.size, file.templates.size, file.sessions.size)
    }

    /**
     * Adds what is missing: exercises are matched by name, templates by name, sessions by
     * date + template name + start time. Ids are remapped.
     */
    private suspend fun merge(file: BackupFile): ImportResult = db.withTransaction {
        val exerciseDao = db.exerciseDao()
        val templateDao = db.templateDao()
        val sessionDao = db.sessionDao()

        val existingExercises = exerciseDao.getAll().associateBy { it.name.trim().lowercase() }
        val exerciseIds = mutableMapOf<Long, Long>()
        var newExercises = 0
        file.exercises.forEach { dto ->
            val match = existingExercises[dto.name.trim().lowercase()]
            exerciseIds[dto.id] = match?.id ?: exerciseDao.insert(dto.toEntity().copy(id = 0)).also { newExercises++ }
        }

        val existingTemplates = templateDao.getAll().associateBy { it.name.trim().lowercase() }
        val templateIds = mutableMapOf<Long, Long>()
        var newTemplates = 0
        val itemsByTemplate = file.templateExercises.groupBy { it.templateId }
        file.templates.forEach { dto ->
            val match = existingTemplates[dto.name.trim().lowercase()]
            if (match != null) {
                templateIds[dto.id] = match.id
            } else {
                val items = itemsByTemplate[dto.id].orEmpty().sortedBy { it.position }.mapNotNull { item ->
                    exerciseIds[item.exerciseId]?.let { item.toEntity().copy(id = 0, exerciseId = it) }
                }
                templateIds[dto.id] = templateDao.save(dto.toEntity().copy(id = 0), items)
                newTemplates++
            }
        }

        val existingSessions = sessionDao.getAll().map { Triple(it.date, it.templateNameSnapshot, it.startedAt) }.toSet()
        val exercisesBySession = file.sessionExercises.groupBy { it.sessionId }
        val logsByExercise = file.setLogs.groupBy { it.sessionExerciseId }
        var newSessions = 0
        file.sessions.forEach { dto ->
            val entity = dto.toEntity()
            if (Triple(entity.date, entity.templateNameSnapshot, entity.startedAt) in existingSessions) return@forEach
            // Never import a second in-progress session.
            val status = if (entity.status == SessionStatus.IN_PROGRESS) SessionStatus.COMPLETED else entity.status
            val sessionId = sessionDao.insert(entity.copy(id = 0, templateId = entity.templateId?.let { templateIds[it] }, status = status))
            exercisesBySession[dto.id].orEmpty().forEach { se ->
                val exerciseId = exerciseIds[se.exerciseId] ?: return@forEach
                val newId = sessionDao.insertExercises(listOf(se.toEntity().copy(id = 0, sessionId = sessionId, exerciseId = exerciseId))).single()
                sessionDao.insertSets(logsByExercise[se.id].orEmpty().map { it.toEntity().copy(id = 0, sessionExerciseId = newId) })
            }
            newSessions++
        }
        ImportResult(newExercises, newTemplates, newSessions)
    }

    /** Deletes all training data. */
    suspend fun clearAll() = db.withTransaction { db.clearAllTables() }
}
