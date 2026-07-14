package com.cybershield.ai.presentation.timeline

import com.cybershield.ai.core.toUserMessage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybershield.ai.data.remote.dto.TimelineEventDto
import com.cybershield.ai.domain.repository.CaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimelineUiState(
    val events: List<TimelineEventDto> = emptyList(),
    val description: String = "",
    val eventTime: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val caseRepository: CaseRepository,
) : ViewModel() {

    private val caseId: String = checkNotNull(savedStateHandle["caseId"])

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun onDescriptionChange(v: String) = _uiState.update { it.copy(description = v) }
    fun onEventTimeChange(v: String) = _uiState.update { it.copy(eventTime = v) }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val events = caseRepository.getTimeline(caseId)
                _uiState.update { it.copy(isLoading = false, events = events) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun add() {
        val desc = _uiState.value.description.trim()
        if (desc.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val time = _uiState.value.eventTime.trim().ifBlank { null }
                caseRepository.addTimelineEvent(caseId, desc, time)
                _uiState.update { it.copy(isSaving = false, description = "", eventTime = "") }
                refresh()
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.toUserMessage()) }
            }
        }
    }
}
