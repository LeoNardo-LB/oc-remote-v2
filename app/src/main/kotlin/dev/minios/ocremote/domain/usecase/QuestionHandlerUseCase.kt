package dev.minios.ocremote.domain.usecase

import dev.minios.ocremote.domain.model.QuestionState
import dev.minios.ocremote.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class QuestionHandlerUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    fun observeQuestions(sessionId: String): Flow<List<QuestionState>> =
        chatRepository.getQuestionsFlow(sessionId)
}
