package com.cybershield.ai.di

import com.cybershield.ai.data.repository.AuthRepositoryImpl
import com.cybershield.ai.data.repository.CaseRepositoryImpl
import com.cybershield.ai.data.repository.ChatRepositoryImpl
import com.cybershield.ai.data.repository.EvidenceRepositoryImpl
import com.cybershield.ai.data.repository.HealthRepositoryImpl
import com.cybershield.ai.data.repository.ReportRepositoryImpl
import com.cybershield.ai.domain.repository.AuthRepository
import com.cybershield.ai.domain.repository.CaseRepository
import com.cybershield.ai.domain.repository.ChatRepository
import com.cybershield.ai.domain.repository.EvidenceRepository
import com.cybershield.ai.domain.repository.HealthRepository
import com.cybershield.ai.domain.repository.ReportRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds @Singleton
    abstract fun bindCaseRepository(impl: CaseRepositoryImpl): CaseRepository

    @Binds @Singleton
    abstract fun bindEvidenceRepository(impl: EvidenceRepositoryImpl): EvidenceRepository

    @Binds @Singleton
    abstract fun bindReportRepository(impl: ReportRepositoryImpl): ReportRepository

    @Binds @Singleton
    abstract fun bindHealthRepository(impl: HealthRepositoryImpl): HealthRepository
}
