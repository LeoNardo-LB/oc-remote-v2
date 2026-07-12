package dev.leonardo.ocremoteplus.domain.usecase

import dev.leonardo.ocremoteplus.domain.repository.FileRepository
import javax.inject.Inject

/**
 * Use case: list directory contents on a server.
 */
class ListDirectoryUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(serverId: String, directory: String, path: String) =
        fileRepository.listDirectory(serverId, directory, path)
}
