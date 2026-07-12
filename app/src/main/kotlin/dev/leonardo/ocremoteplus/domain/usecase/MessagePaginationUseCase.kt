package dev.leonardo.ocremoteplus.domain.usecase

import dev.leonardo.ocremoteplus.domain.model.Message
import dev.leonardo.ocremoteplus.domain.model.MessageWithParts
import dev.leonardo.ocremoteplus.domain.repository.ChatRepository
import dev.leonardo.ocremoteplus.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MessagePaginationUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val sessionRepository: SessionRepository
) {
    fun observeMessages(sessionId: String): Flow<List<Message>> =
        chatRepository.getMessagesFlow(sessionId)

    suspend fun loadOlderMessages(serverId: String, sessionId: String, limit: Int): Result<List<MessageWithParts>> =
        sessionRepository.listMessages(serverId, sessionId, limit)
}
