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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
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
    val newTimelineEventCount: Int = 0,
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
            var backoffMs = 2_000L
            while (isActive) {
                try {
                    caseRepository.observeCaseUpdates(caseId).collect { update ->
                        backoffMs = 2_000L // connection is healthy again — reset backoff
                        _uiState.update { state ->
                            val current = state.case
                            val hasStatusUpdate = update.status != null && update.risk_level != null && update.risk_score != null
                            val hasTimelineUpdate = !update.timeline_events_added.isNullOrEmpty()
                            val hasRecoveryUpdate = update.recovery_stage != null || update.recovery_progress_percent != null
                            state.copy(
                                liveNote = when {
                                    hasStatusUpdate -> "Live update: ${update.risk_level} / ${update.status}"
                                    hasTimelineUpdate -> "New timeline event added"
                                    else -> state.liveNote
                                },
                                case = if ((hasStatusUpdate || hasRecoveryUpdate) && current != null) {
                                    current.copy(
                                        risk_level = update.risk_level ?: current.risk_level,
                                        risk_score = update.risk_score ?: current.risk_score,
                                        status = update.status ?: current.status,
                                        recovery_stage = update.recovery_stage ?: current.recovery_stage,
                                        recovery_progress_percent = update.recovery_progress_percent
                                            ?: current.recovery_progress_percent,
                                    )
                                } else {
                                    current
                                },
                                newTimelineEventCount = if (hasTimelineUpdate) {
                                    state.newTimelineEventCount + update.timeline_events_added!!.size
                                } else {
                                    state.newTimelineEventCount
                                },
                            )
                        }
                    }
                } catch (_: CancellationException) {
                    throw CancellationException("listenWs cancelled")
                } catch (_: Exception) {
                    // connection dropped — back off and retry rather than
                    // giving up on live updates for the rest of the screen's
                    // lifetime. Capped at 30s so a long-broken connection
                    // doesn't hammer the server, but the screen keeps trying
                    // to recover on its own.
                }
                if (!isActive) break
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    override fun onCleared() {
        wsJob?.cancel()
        super.onCleared()
    }
}
