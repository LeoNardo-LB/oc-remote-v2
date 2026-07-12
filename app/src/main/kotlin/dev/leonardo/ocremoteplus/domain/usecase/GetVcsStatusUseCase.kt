package dev.leonardo.ocremoteplus.domain.usecase

import dev.leonardo.ocremoteplus.domain.repository.VcsRepository
import javax.inject.Inject

/**
 * Use case: get VCS status for a directory on a server.
 */
class GetVcsStatusUseCase @Inject constructor(
    private val vcsRepository: VcsRepository
) {
    suspend operator fun invoke(serverId: String, directory: String) =
        vcsRepository.getStatus(serverId, directory)
}
