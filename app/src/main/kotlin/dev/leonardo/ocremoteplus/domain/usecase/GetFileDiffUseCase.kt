package dev.leonardo.ocremoteplus.domain.usecase

import dev.leonardo.ocremoteplus.domain.model.VcsDiffMode
import dev.leonardo.ocremoteplus.domain.repository.VcsRepository
import javax.inject.Inject

/**
 * Use case: get VCS file diff for a directory on a server.
 */
class GetFileDiffUseCase @Inject constructor(
    private val vcsRepository: VcsRepository
) {
    suspend operator fun invoke(
        serverId: String,
        directory: String,
        mode: VcsDiffMode = VcsDiffMode.GIT,
        context: Int = 3
    ) = vcsRepository.getDiff(serverId, directory, mode, context)
}
