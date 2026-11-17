package com.kalist.training.domain

import com.kalist.training.data.db.Template
import com.kalist.training.data.db.TemplateLastUse

object NextTemplate {
    /**
     * Least recently used rotation template. Templates never completed come first (in creation order),
     * so with A and B the result alternates A-B-A, B-A-B, ... naturally.
     */
    fun pick(templates: List<Template>, lastUses: List<TemplateLastUse>): Template? {
        val candidates = templates.filter { it.inRotation }.ifEmpty { templates }
        val last = lastUses.associate { it.templateId to it.lastDate }
        return candidates.minWithOrNull(
            compareBy<Template>(
                { last[it.id] != null },
                { last[it.id] },
                { it.createdAt },
                { it.id },
            )
        )
    }
}
