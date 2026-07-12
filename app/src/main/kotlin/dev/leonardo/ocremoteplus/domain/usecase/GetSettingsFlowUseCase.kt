package dev.leonardo.ocremoteplus.domain.usecase

import dev.leonardo.ocremoteplus.domain.model.AppSettings
import dev.leonardo.ocremoteplus.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case: observe application settings.
 * Used by Phase 4 SettingsViewModel.
 */
class GetSettingsFlowUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(): Flow<AppSettings> =
        settingsRepository.getSettingsFlow()
}
