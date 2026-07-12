package dev.leonardo.ocremoteplus.domain.repository

import dev.leonardo.ocremoteplus.domain.model.McpServerStatus
import dev.leonardo.ocremoteplus.domain.model.ServerConnection

interface McpRepository {
    suspend fun getMcpServers(): Result<List<McpServerStatus>>
    suspend fun toggleMcpServer(name: String, connect: Boolean): Result<Boolean>
    fun setConnection(conn: ServerConnection)
}
