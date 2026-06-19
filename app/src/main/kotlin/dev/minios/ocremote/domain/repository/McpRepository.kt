package dev.minios.ocremote.domain.repository

import dev.minios.ocremote.domain.model.McpServerStatus
import dev.minios.ocremote.domain.model.ServerConnection

interface McpRepository {
    suspend fun getMcpServers(): Result<List<McpServerStatus>>
    suspend fun toggleMcpServer(name: String, connect: Boolean): Result<Boolean>
    fun setConnection(conn: ServerConnection)
}
