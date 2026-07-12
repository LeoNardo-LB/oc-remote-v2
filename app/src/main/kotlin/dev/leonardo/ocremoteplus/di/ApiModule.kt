package dev.leonardo.ocremoteplus.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.leonardo.ocremoteplus.data.api.file.FileApi
import dev.leonardo.ocremoteplus.data.api.file.FileApiImpl
import dev.leonardo.ocremoteplus.data.api.message.MessageApi
import dev.leonardo.ocremoteplus.data.api.message.MessageApiImpl
import dev.leonardo.ocremoteplus.data.api.provider.ProviderApi
import dev.leonardo.ocremoteplus.data.api.provider.ProviderApiImpl
import dev.leonardo.ocremoteplus.data.api.session.SessionApi
import dev.leonardo.ocremoteplus.data.api.session.SessionApiImpl
import dev.leonardo.ocremoteplus.data.api.system.SystemApi
import dev.leonardo.ocremoteplus.data.api.system.SystemApiImpl
import dev.leonardo.ocremoteplus.data.api.terminal.TerminalApi
import dev.leonardo.ocremoteplus.data.api.terminal.TerminalApiImpl

/**
 * Hilt bindings for the 6 domain API interfaces.
 *
 * Each `*ApiImpl` is `@Singleton` + `@Inject constructor`, so the bindings here are
 * unscoped aliases — the scope lives on the implementation, matching [DomainModule].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ApiModule {

    @Binds
    abstract fun bindSessionApi(impl: SessionApiImpl): SessionApi

    @Binds
    abstract fun bindMessageApi(impl: MessageApiImpl): MessageApi

    @Binds
    abstract fun bindTerminalApi(impl: TerminalApiImpl): TerminalApi

    @Binds
    abstract fun bindProviderApi(impl: ProviderApiImpl): ProviderApi

    @Binds
    abstract fun bindFileApi(impl: FileApiImpl): FileApi

    @Binds
    abstract fun bindSystemApi(impl: SystemApiImpl): SystemApi
}
