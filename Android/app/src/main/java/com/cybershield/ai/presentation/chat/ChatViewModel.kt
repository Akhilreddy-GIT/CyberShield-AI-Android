package com.cybershield.ai.presentation.chat

import com.cybershield.ai.core.toUserMessage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybershield.ai.data.remote.dto.HelplineDto
import com.cybershield.ai.domain.repository.AuthRepository
import com.cybershield.ai.domain.repository.CaseRepository
import com.cybershield.ai.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backend-confirmed cyber-incident analysis for a single assistant message.
 *
 * This exists ONLY when the backend explicitly determined the message
 * represents a real cyber incident. Every field is sourced directly from
 * the backend response DTO — nothing here is computed, guessed, or
 * defaulted by the client.
 *
 * NOTE: This assumes the backend response payload is extended to include
 * an `analysis` object (nullable) with these fields. If the backend does
 * not yet send `analysis`, it must be added server-side — the client
 * cannot legitimately synthesize any of these values on its own.
 */
data class ThreatAnalysis(
    val title: String,
    val riskScore: Int,
    val riskLevel: String,
    val certainty: Float,
    val target: String,
    val recoveryActions: List<RecoveryActionItem>,
)

data class RecoveryActionItem(
    val title: String,
    val recommended: Boolean = false,
    val optional: Boolean = false,
)

data class ChatBubble(
    val role: String,
    val content: String,
    val citedSources: List<String> = emptyList(),
    val isCritical: Boolean = false,
    val isCyberIncident: Boolean = false,
    val analysis: ThreatAnalysis? = null,
)

data class ChatUiState(
    val caseId: String? = null,
    val messages: List<ChatBubble> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val isLoadingHistory: Boolean = false,
    val error: String? = null,
    val helplines: List<HelplineDto> = emptyList(),
    val category: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val caseRepository: CaseRepository,
) : ViewModel() {

    private val initialCaseId: String? = savedStateHandle.get<String>("caseId")?.takeIf { it.isNotBlank() && it != "{caseId}" }

    private val _uiState = MutableStateFlow(ChatUiState(caseId = initialCaseId))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        if (!initialCaseId.isNullOrBlank()) {
            loadHistory(initialCaseId)
        }
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value, error = null) }
    }

    fun send() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty() || _uiState.value.isSending) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSending = true,
                    error = null,
                    messages = it.messages + ChatBubble(role = "user", content = text),
                    input = "",
                )
            }
            try {
                val anon = authRepository.ensureAnonUserId()
                val response = chatRepository.sendMessage(
                    caseId = _uiState.value.caseId,
                    anonUserId = anon,
                    message = text,
                )

                // case_id is only meaningful once the backend has actually
                // established a case. Never fabricate or propagate a case
                // state the backend did not send.
                val backendCaseId = response.case_id?.takeIf { it.isNotBlank() }
                if (backendCaseId != null) {
                    caseRepository.setActiveCaseId(backendCaseId)
                }

                // The backend is the ONLY source of truth for whether this
                // is a real cyber incident and for every analysis value.
                // `analysis` must be null/absent unless the backend
                // explicitly returns it — the client performs no inference.
                val analysisDto = response.analysis
                val analysis = analysisDto?.let {
                    ThreatAnalysis(
                        title = it.title,
                        riskScore = it.risk_score,
                        riskLevel = it.risk_level,
                        certainty = it.certainty,
                        target = it.target,
                        recoveryActions = it.recovery_actions.orEmpty().map { action ->
                            RecoveryActionItem(
                                title = action.title,
                                recommended = action.recommended,
                                optional = action.optional,
                            )
                        },
                    )
                }

                _uiState.update {
                    it.copy(
                        caseId = backendCaseId ?: it.caseId,
                        category = response.category,
                        isSending = false,
                        helplines = response.helplines.orEmpty(),
                        messages = it.messages + ChatBubble(
                            role = "assistant",
                            content = response.reply,
                            citedSources = response.cited_sources,
                            isCritical = response.is_critical,
                            isCyberIncident = response.is_cyber_incident,
                            analysis = analysis,
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSending = false, error = e.toUserMessage())
                }
            }
        }
    }

    private fun loadHistory(caseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHistory = true, error = null) }
            try {
                val history = chatRepository.getHistory(caseId)
                _uiState.update {
                    it.copy(
                        isLoadingHistory = false,
                        messages = history.map { m ->
                            ChatBubble(
                                role = m.role,
                                content = m.content,
                                citedSources = m.cited_sources,
                                isCritical = m.is_critical,
                                isCyberIncident = m.is_cyber_incident,
                                analysis = m.analysis?.let { a ->
                                    ThreatAnalysis(
                                        title = a.title,
                                        riskScore = a.risk_score,
                                        riskLevel = a.risk_level,
                                        certainty = a.certainty,
                                        target = a.target,
                                        recoveryActions = a.recovery_actions.orEmpty().map { action ->
                                            RecoveryActionItem(
                                                title = action.title,
                                                recommended = action.recommended,
                                                optional = action.optional,
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoadingHistory = false, error = e.toUserMessage())
                }
            }
        }
    }
}
