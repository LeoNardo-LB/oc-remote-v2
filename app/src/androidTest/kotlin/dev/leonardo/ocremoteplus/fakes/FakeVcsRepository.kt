package dev.leonardo.ocremoteplus.fakes

import javax.inject.Inject
import dev.leonardo.ocremoteplus.domain.model.VcsBranchInfo
import dev.leonardo.ocremoteplus.domain.model.VcsChange
import dev.leonardo.ocremoteplus.domain.model.VcsDiffMode
import dev.leonardo.ocremoteplus.domain.model.VcsFileDiff
import dev.leonardo.ocremoteplus.domain.repository.VcsRepository
import javax.inject.Singleton

@Singleton
class FakeVcsRepository @Inject constructor() : VcsRepository {

    var getBranchResult: Result<VcsBranchInfo> = Result.success(VcsBranchInfo(branch = "main", defaultBranch = "main"))
    var getStatusResult: Result<List<VcsChange>> = Result.success(emptyList())
    var getDiffResult: Result<List<VcsFileDiff>> = Result.success(emptyList())

    override suspend fun getBranch(serverId: String, directory: String): Result<VcsBranchInfo> = getBranchResult

    override suspend fun getStatus(serverId: String, directory: String): Result<List<VcsChange>> = getStatusResult

    override suspend fun getDiff(
        serverId: String,
        directory: String,
        mode: VcsDiffMode,
        context: Int
    ): Result<List<VcsFileDiff>> = getDiffResult
}
