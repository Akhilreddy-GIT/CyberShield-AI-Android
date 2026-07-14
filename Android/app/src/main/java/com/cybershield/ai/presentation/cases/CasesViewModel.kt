package com.cybershield.ai.presentation.cases

import com.cybershield.ai.core.toUserMessage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybershield.ai.data.remote.dto.CaseListItemDto
import com.cybershield.ai.data.remote.dto.CaseOutDto
import com.cybershield.ai.domain.repository.AuthRepository
import com.cybershield.ai.domain.repository.CaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CasesUiState(
    val cases: List<CaseListItemDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CasesViewModel @Inject constructor(
    private val caseRepository: CaseRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CasesUiState())
    val uiState: StateFlow<CasesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val anon = authRepository.ensureAnonUserId()
                val cases = caseRepository.listUserCases(anon)
                _uiState.update { it.copy(isLoading = false, cases = cases) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }
}

data class CaseDetailUiState(
    val case: CaseOutDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val liveNote: String? = null,
)

@HiltViewModel
class CaseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val caseRepository: CaseRepository,
) : ViewModel() {

    private val caseId: String = checkNotNull(savedStateHandle["caseId"])

    private val _uiState = MutableStateFlow(CaseDetailUiState())
    val uiState: StateFlow<CaseDetailUiState> = _uiState.asStateFlow()

    private var wsJob: Job? = null

    init {
        load()
        listenWs()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val c = caseRepository.getCase(caseId)
                caseRepository.setActiveCaseId(caseId)
                _uiState.update { it.copy(isLoading = false, case = c) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }

    private fun listenWs() {
        wsJob?.cancel()
        wsJob = viewModelScope.launch {
            try {
                caseRepository.observeCaseUpdates(caseId).collect { update ->
                    _uiState.update { state ->
                        val current = state.case
                        state.copy(
                            liveNote = "Live update: ${update.risk_level} / ${update.status}",
                            case = current?.copy(
                                risk_level = update.risk_level,
                                risk_score = update.risk_score,
                                status = update.status,
                            ),
                        )
                    }
                }
            } catch (_: Exception) {
                // connection dropped — non-fatal
            }
        }
    }

    override fun onCleared() {
        wsJob?.cancel()
        super.onCleared()
    }
}
