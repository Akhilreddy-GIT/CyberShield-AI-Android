package com.cybershield.ai.presentation.auth

import com.cybershield.ai.core.toUserMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybershield.ai.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val username: String? = null,
    val anonUserId: String = "",
    val isLoggedIn: Boolean = false,
    val formUsername: String = "",
    val formPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val modeLogin: Boolean = true,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.ensureAnonUserId()
        }
        viewModelScope.launch {
            authRepository.observeUsername().collect { name ->
                _uiState.update { it.copy(username = name, isLoggedIn = !name.isNullOrBlank()) }
            }
        }
        viewModelScope.launch {
            authRepository.observeAnonUserId().collect { id ->
                _uiState.update { it.copy(anonUserId = id) }
            }
        }
    }

    fun onUsernameChange(v: String) = _uiState.update { it.copy(formUsername = v, error = null) }
    fun onPasswordChange(v: String) = _uiState.update { it.copy(formPassword = v, error = null) }
    fun setModeLogin(login: Boolean) = _uiState.update { it.copy(modeLogin = login, error = null, info = null) }

    fun submit() {
        val u = _uiState.value.formUsername.trim()
        val p = _uiState.value.formPassword
        if (u.length < 3) {
            _uiState.update { it.copy(error = "Username must be at least 3 characters") }
            return
        }
        if (p.length < 6) {
            _uiState.update { it.copy(error = "Password must be at least 6 characters") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, info = null) }
            try {
                if (_uiState.value.modeLogin) {
                    authRepository.login(u, p)
                    _uiState.update { it.copy(isLoading = false, info = "Logged in", formPassword = "") }
                } else {
                    authRepository.register(u, p)
                    _uiState.update { it.copy(isLoading = false, info = "Account created", formPassword = "") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.update { it.copy(info = "Signed out. Anonymous session continues.") }
        }
    }
}
