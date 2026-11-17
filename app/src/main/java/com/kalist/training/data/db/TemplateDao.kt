package com.kalist.training.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Query(
        """
        SELECT t.*, (SELECT COUNT(*) FROM template_exercise te WHERE te.templateId = t.id) AS exerciseCount
        FROM template t ORDER BY t.name COLLATE NOCASE ASC
        """
    )
    fun observeSummaries(): Flow<List<TemplateSummary>>

    @Query("SELECT * FROM template ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Template>>

    @Query("SELECT * FROM template ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<Template>

    @Transaction
    @Query("SELECT * FROM template WHERE id = :id")
    suspend fun getWithExercises(id: Long): TemplateWithExercises?

    @Query("SELECT * FROM template WHERE id = :id")
    suspend fun get(id: Long): Template?

    @Query("SELECT COUNT(*) FROM template")
    suspend fun count(): Int

    @Query("SELECT * FROM template_exercise")
    suspend fun getAllItems(): List<TemplateExercise>

    @Insert
    suspend fun insert(template: Template): Long

    @Update
    suspend fun update(template: Template)

    @Query("DELETE FROM template WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertItems(items: List<TemplateExercise>)

    @Query("DELETE FROM template_exercise WHERE templateId = :templateId")
    suspend fun deleteItems(templateId: Long)

    /** Saves a template and replaces its exercise list atomically. Returns the template id. */
    @Transaction
    suspend fun save(template: Template, items: List<TemplateExercise>): Long {
        val id = if (template.id == 0L) insert(template) else template.id.also { update(template) }
        deleteItems(id)
        insertItems(items.mapIndexed { index, item -> item.copy(id = 0, templateId = id, position = index) })
        return id
    }

    @Query(
        """
        SELECT templateId, MAX(date) AS lastDate FROM session
        WHERE status = 'COMPLETED' AND templateId IS NOT NULL
        GROUP BY templateId
        """
    )
    suspend fun lastUses(): List<TemplateLastUse>
}
