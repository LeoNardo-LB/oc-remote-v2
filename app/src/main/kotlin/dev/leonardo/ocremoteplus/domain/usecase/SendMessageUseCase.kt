package dev.leonardo.ocremoteplus.domain.usecase

import dev.leonardo.ocremoteplus.domain.model.ModelSelection
import dev.leonardo.ocremoteplus.domain.model.PromptPart
import dev.leonardo.ocremoteplus.domain.repository.ChatRepository
import javax.inject.Inject

/**
 * Use case: send messages to a session.
 * Delegates to ChatRepository.
 */
class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend fun sendPrompt(
        serverId: String,
        sessionId: String,
        parts: List<PromptPart>,
        model: ModelSelection?,
        agent: String,
        variant: String?,
        directory: String?
    ) {
        chatRepository.promptAsync(
            serverId = serverId,
            sessionId = sessionId,
            parts = parts,
            model = model,
            agent = agent,
            variant = variant,
            directory = directory
        ).getOrThrow()
    }
}
