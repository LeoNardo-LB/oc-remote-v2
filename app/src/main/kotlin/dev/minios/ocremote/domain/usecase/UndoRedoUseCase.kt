package dev.minios.ocremote.domain.usecase

import dev.minios.ocremote.data.api.OpenCodeApi
import dev.minios.ocremote.data.api.ServerConnection
import javax.inject.Inject

/**
 * Use case: undo and redo messages (revert/unrevert sessions).
 * Temporary shell — delegates to OpenCodeApi. Full impl with tests in Phase 4.
 */
class UndoRedoUseCase @Inject constructor(
    private val api: OpenCodeApi
) {
    // TODO: Phase 4 — replace api calls with ChatRepository methods

    suspend fun revertSession(conn: ServerConnection, sessionId: String, messageId: String) {
        api.revertSession(conn, sessionId, messageId)
    }

    suspend fun unrevertSession(conn: ServerConnection, sessionId: String) {
        api.unrevertSession(conn, sessionId)
    }
}
