package dev.leonardo.ocremoteplus.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dev.leonardo.ocremoteplus.data.di.DataModule
import dev.leonardo.ocremoteplus.domain.repository.AgentRepository
import dev.leonardo.ocremoteplus.domain.repository.ChatRepository
import dev.leonardo.ocremoteplus.domain.repository.DraftRepository
import dev.leonardo.ocremoteplus.domain.repository.FileRepository
import dev.leonardo.ocremoteplus.domain.repository.LocalServerRepository
import dev.leonardo.ocremoteplus.domain.repository.McpRepository
import dev.leonardo.ocremoteplus.domain.repository.ProviderRepository
import dev.leonardo.ocremoteplus.domain.repository.ServerConfigRepository
import dev.leonardo.ocremoteplus.domain.repository.ServerConnectionRepository
import dev.leonardo.ocremoteplus.domain.repository.ServerRepository
import dev.leonardo.ocremoteplus.domain.repository.SessionRepository
import dev.leonardo.ocremoteplus.domain.repository.SettingsRepository
import dev.leonardo.ocremoteplus.domain.repository.TerminalRepository
import dev.leonardo.ocremoteplus.domain.repository.VcsRepository
import dev.leonardo.ocremoteplus.fakes.FakeAgentRepository
import dev.leonardo.ocremoteplus.fakes.FakeChatRepository
import dev.leonardo.ocremoteplus.fakes.FakeDraftRepository
import dev.leonardo.ocremoteplus.fakes.FakeFileRepository
import dev.leonardo.ocremoteplus.fakes.FakeMcpRepository
import dev.leonardo.ocremoteplus.fakes.FakeServerRepository
import dev.leonardo.ocremoteplus.fakes.FakeSessionRepository
import dev.leonardo.ocremoteplus.fakes.FakeSettingsRepository
import dev.leonardo.ocremoteplus.fakes.FakeTerminalRepository
import dev.leonardo.ocremoteplus.fakes.FakeVcsRepository
import javax.inject.Singleton

/**
 * Replaces BOTH DomainModule and DataModule with fake repository bindings.
 *
 * DataModule (data/di/) binds ChatRepository + SessionRepository.
 * DomainModule (di/) binds all other repository interfaces.
 *
 * ServerRepositoryImpl implements 5 interfaces; FakeServerRepository does the same,
 * so we bind the single fake instance as all 5 types.
 */
@TestInstallIn(components = [SingletonComponent::class], replaces = [DomainModule::class, DataModule::class])
@Module
@Suppress("unused")
abstract class FakeDomainModule {

    // DataModule replacements
    @Binds @Singleton abstract fun bindChatRepository(impl: FakeChatRepository): ChatRepository
    @Binds @Singleton abstract fun bindSessionRepository(impl: FakeSessionRepository): SessionRepository

    // DomainModule replacements
    @Binds @Singleton abstract fun bindSettingsRepository(impl: FakeSettingsRepository): SettingsRepository
    @Binds @Singleton abstract fun bindAgentRepository(impl: FakeAgentRepository): AgentRepository
    @Binds @Singleton abstract fun bindDraftRepository(impl: FakeDraftRepository): DraftRepository
    @Binds @Singleton abstract fun bindFileRepository(impl: FakeFileRepository): FileRepository
    @Binds @Singleton abstract fun bindVcsRepository(impl: FakeVcsRepository): VcsRepository
    @Binds @Singleton abstract fun bindTerminalRepository(impl: FakeTerminalRepository): TerminalRepository
    @Binds @Singleton abstract fun bindMcpRepository(impl: FakeMcpRepository): McpRepository

    // ServerRepository and its 4 sub-interfaces — all backed by single FakeServerRepository
    @Binds @Singleton abstract fun bindServerRepository(impl: FakeServerRepository): ServerRepository
    @Binds @Singleton abstract fun bindServerConfigRepository(impl: FakeServerRepository): ServerConfigRepository
    @Binds @Singleton abstract fun bindServerConnectionRepository(impl: FakeServerRepository): ServerConnectionRepository
    @Binds @Singleton abstract fun bindLocalServerRepository(impl: FakeServerRepository): LocalServerRepository
    @Binds @Singleton abstract fun bindProviderRepository(impl: FakeServerRepository): ProviderRepository
}
