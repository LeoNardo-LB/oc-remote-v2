package dev.minios.ocremote.domain.repository

import dev.minios.ocremote.domain.model.FileContent
import dev.minios.ocremote.domain.model.FileNode

interface FileRepository {
    suspend fun listDirectory(serverId: String, directory: String, path: String): Result<List<FileNode>>
    suspend fun getFileContent(serverId: String, directory: String, path: String): Result<FileContent>
}
