package com.cybershield.ai.data.repository

import com.cybershield.ai.data.local.datastore.SessionPreferences
import com.cybershield.ai.data.remote.CyberShieldApi
import com.cybershield.ai.data.remote.dto.AuthOutDto
import com.cybershield.ai.data.remote.dto.LoginInDto
import com.cybershield.ai.data.remote.dto.RegisterInDto
import com.cybershield.ai.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: CyberShieldApi,
    private val session: SessionPreferences,
) : AuthRepository {

    override suspend fun ensureAnonUserId(): String = session.ensureAnonUserId()

    override fun observeAnonUserId(): Flow<String> = session.anonUserId

    override fun observeUsername(): Flow<String?> = session.username

    override fun observeAuthToken(): Flow<String?> = session.authToken

    override suspend fun register(username: String, password: String): AuthOutDto {
        val result = api.register(RegisterInDto(username = username, password = password))
        session.setAuth(result.token, result.anon_user_id, result.username)
        return result
    }

    override suspend fun login(username: String, password: String): AuthOutDto {
        val result = api.login(LoginInDto(username = username, password = password))
        session.setAuth(result.token, result.anon_user_id, result.username)
        return result
    }

    override suspend fun logout() {
        session.clearAuth()
        session.ensureAnonUserId()
    }
}
