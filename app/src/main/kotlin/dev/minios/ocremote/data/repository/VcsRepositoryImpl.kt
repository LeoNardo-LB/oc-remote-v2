package dev.minios.ocremote.data.repository

import dev.minios.ocremote.data.api.OpenCodeApi
import dev.minios.ocremote.data.mapper.VcsMapper
import dev.minios.ocremote.domain.model.VcsDiffMode
import dev.minios.ocremote.domain.repository.ServerRepository
import dev.minios.ocremote.domain.repository.VcsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VcsRepositoryImpl @Inject constructor(
    private val api: OpenCodeApi,
    private val serverRepository: ServerRepository
) : VcsRepository {

    override suspend fun getBranch(serverId: String, directory: String) = runCatching {
        val conn = serverRepository.resolveConnection(serverId)
        VcsMapper.toDomain(api.getVcs(conn, directory))
    }

    override suspend fun getStatus(serverId: String, directory: String) = runCatching {
        val conn = serverRepository.resolveConnection(serverId)
        api.getVcsStatus(conn, directory).map { VcsMapper.toDomain(it) }
    }

    override suspend fun getDiff(serverId: String, directory: String, mode: VcsDiffMode, context: Int) = runCatching {
        val conn = serverRepository.resolveConnection(serverId)
        api.getVcsDiff(conn, mode.apiValue, context, directory).map { VcsMapper.toDomain(it) }
    }
}
