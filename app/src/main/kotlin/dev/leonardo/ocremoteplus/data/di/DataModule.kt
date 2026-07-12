package dev.leonardo.ocremoteplus.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.leonardo.ocremoteplus.data.repository.ChatRepositoryImpl
import dev.leonardo.ocremoteplus.data.repository.SessionRepositoryImpl
import dev.leonardo.ocremoteplus.domain.repository.ChatRepository
import dev.leonardo.ocremoteplus.domain.repository.SessionRepository

/**
 * Hilt module that binds Chat and Session domain interfaces to their Data-layer implementations.
 * Server and Settings bindings live in di/DomainModule.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository
}
