package dev.leonardo.ocremoteplus.domain.usecase

import dev.leonardo.ocremoteplus.domain.model.ServerConfig
import dev.leonardo.ocremoteplus.domain.repository.ServerRepository
import javax.inject.Inject

/**
 * Use case: connect to a server.
 * Used by Phase 4 HomeViewModel.
 */
class ConnectServerUseCase @Inject constructor(
    private val serverRepository: ServerRepository
) {
    suspend operator fun invoke(server: ServerConfig): Result<Unit> =
        serverRepository.connect(server)
}
