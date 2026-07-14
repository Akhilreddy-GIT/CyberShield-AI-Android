package com.cybershield.ai.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybershield.ai.domain.repository.AuthRepository
import com.cybershield.ai.domain.repository.CaseRepository
import com.cybershield.ai.domain.repository.HealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val anonUserId: String = "",
    val username: String? = null,
    val activeCaseId: String? = null,
    val backendOnline: Boolean? = null,
    val llmConfigured: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val caseRepository: CaseRepository,
    private val healthRepository: HealthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val anon = authRepository.ensureAnonUserId()
            _uiState.update { it.copy(anonUserId = anon) }
        }
        viewModelScope.launch {
            authRepository.observeUsername().collect { name ->
                _uiState.update { it.copy(username = name) }
            }
        }
        viewModelScope.launch {
            caseRepository.observeActiveCaseId().collect { id ->
                _uiState.update { it.copy(activeCaseId = id) }
            }
        }
        refreshHealth()
    }

    fun refreshHealth() {
        viewModelScope.launch {
            runCatching { healthRepository.health() }
                .onSuccess { h ->
                    _uiState.update {
                        it.copy(backendOnline = h.status == "ok", llmConfigured = h.llm_configured)
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(backendOnline = false) }
                }
        }
    }
}
