package dev.minios.ocremote.domain.repository

import dev.minios.ocremote.domain.model.VcsBranchInfo
import dev.minios.ocremote.domain.model.VcsChange
import dev.minios.ocremote.domain.model.VcsDiffMode
import dev.minios.ocremote.domain.model.VcsFileDiff

interface VcsRepository {
    suspend fun getBranch(serverId: String, directory: String): Result<VcsBranchInfo>
    suspend fun getStatus(serverId: String, directory: String): Result<List<VcsChange>>
    suspend fun getDiff(serverId: String, directory: String, mode: VcsDiffMode, context: Int = 3): Result<List<VcsFileDiff>>
}
