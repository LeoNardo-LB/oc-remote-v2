package dev.minios.ocremote.domain.usecase

import dev.minios.ocremote.data.api.OpenCodeApi
import dev.minios.ocremote.data.api.ServerConnection
import java.io.OutputStream
import javax.inject.Inject

/**
 * Use case: share, export, and compact sessions.
 * Temporary shell — delegates to OpenCodeApi. Full impl with tests in Phase 4.
 */
class ShareExportUseCase @Inject constructor(
    private val api: OpenCodeApi
) {
    // TODO: Phase 4 — replace api calls with SessionRepository methods

    suspend fun shareSession(conn: ServerConnection, sessionId: String): dev.minios.ocremote.domain.model.Session =
        api.shareSession(conn, sessionId)

    suspend fun unshareSession(conn: ServerConnection, sessionId: String) {
        api.unshareSession(conn, sessionId)
    }

    suspend fun compactSession(conn: ServerConnection, sessionId: String, providerId: String, modelId: String) {
        api.summarizeSession(conn, sessionId, providerId, modelId)
    }

    suspend fun exportSessionToStream(
        conn: ServerConnection,
        sessionId: String,
        outputStream: OutputStream,
        onProgress: (Long) -> Unit
    ) {
        api.exportSessionToStream(conn, sessionId, outputStream, onProgress)
    }
}
