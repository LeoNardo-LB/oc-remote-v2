package dev.leonardo.ocremoteplus.domain.usecase

import dev.leonardo.ocremoteplus.domain.model.ProvidersResponse
import dev.leonardo.ocremoteplus.domain.repository.ProviderRepository
import javax.inject.Inject

/**
 * Use case: load provider catalog for model selection.
 * Routes through ProviderRepository instead of direct API access.
 */
class SelectModelUseCase @Inject constructor(
    private val providerRepository: ProviderRepository
) {
    suspend fun loadProviders(serverId: String): ProvidersResponse {
        return providerRepository.loadProviderCatalog(serverId).getOrThrow()
    }
}
