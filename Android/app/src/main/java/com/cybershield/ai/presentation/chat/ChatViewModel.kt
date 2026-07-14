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

data class ChatBubble(
    val role: String,
    val content: String,
    val citedSources: List<String> = emptyList(),
    val isCritical: Boolean = false,
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
                caseRepository.setActiveCaseId(response.case_id)
                _uiState.update {
                    it.copy(
                        caseId = response.case_id,
                        category = response.category,
                        isSending = false,
                        helplines = response.helplines.orEmpty(),
                        messages = it.messages + ChatBubble(
                            role = "assistant",
                            content = response.reply,
                            citedSources = response.cited_sources,
                            isCritical = response.is_critical,
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
