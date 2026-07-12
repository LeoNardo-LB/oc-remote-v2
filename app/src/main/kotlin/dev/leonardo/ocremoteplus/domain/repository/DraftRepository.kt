package dev.leonardo.ocremoteplus.domain.repository

import dev.leonardo.ocremoteplus.domain.model.Draft

interface DraftRepository {
    fun getDraft(sessionId: String): Draft?
    fun saveDraft(sessionId: String, draft: Draft)
    fun clearDraft(sessionId: String)
    fun getDraftSessionIds(): Set<String>
}
