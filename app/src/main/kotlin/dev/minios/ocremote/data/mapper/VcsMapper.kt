package dev.minios.ocremote.data.mapper

import dev.minios.ocremote.data.dto.response.FileDiffDto
import dev.minios.ocremote.data.dto.response.VcsBranchDto
import dev.minios.ocremote.data.dto.response.VcsChangeDto
import dev.minios.ocremote.domain.model.VcsBranchInfo
import dev.minios.ocremote.domain.model.VcsChange
import dev.minios.ocremote.domain.model.VcsFileDiff
import dev.minios.ocremote.domain.model.VcsStatus

object VcsMapper {

    fun toDomain(dto: VcsChangeDto): VcsChange = VcsChange(
        file = dto.file,
        additions = dto.additions,
        deletions = dto.deletions,
        status = parseStatus(dto.status)
    )

    fun toDomain(dto: VcsBranchDto): VcsBranchInfo = VcsBranchInfo(
        branch = dto.branch,
        defaultBranch = dto.defaultBranch
    )

    fun toDomain(dto: FileDiffDto): VcsFileDiff = VcsFileDiff(
        file = dto.file ?: "",
        patch = dto.patch,
        additions = dto.additions,
        deletions = dto.deletions,
        status = dto.status?.let { parseStatus(it) }
    )

    internal fun parseStatus(s: String?): VcsStatus = when (s) {
        "added" -> VcsStatus.ADDED
        "deleted" -> VcsStatus.DELETED
        "modified" -> VcsStatus.MODIFIED
        null -> VcsStatus.MODIFIED
        else -> VcsStatus.MODIFIED
    }
}
