package com.kalist.training.data.repo

import com.kalist.training.data.db.Template
import com.kalist.training.data.db.TemplateDao
import com.kalist.training.data.db.TemplateExercise
import com.kalist.training.data.db.TemplateSummary
import com.kalist.training.data.db.TemplateWithExercises
import com.kalist.training.domain.NextTemplate
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateRepository @Inject constructor(private val dao: TemplateDao) {
    fun observeSummaries(): Flow<List<TemplateSummary>> = dao.observeSummaries()
    fun observeAll(): Flow<List<Template>> = dao.observeAll()
    suspend fun getAll(): List<Template> = dao.getAll()
    suspend fun getWithExercises(id: Long): TemplateWithExercises? = dao.getWithExercises(id)

    suspend fun save(template: Template, items: List<TemplateExercise>): Long = dao.save(template, items)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun duplicate(id: Long, newName: String): Long? {
        val source = dao.getWithExercises(id) ?: return null
        val copy = source.template.copy(id = 0, name = newName, createdAt = Instant.now())
        return dao.save(copy, source.sortedItems.map { it.item.copy(id = 0) })
    }

    suspend fun nextTemplate(): Template? = NextTemplate.pick(dao.getAll(), dao.lastUses())
}
