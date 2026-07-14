package com.cybershield.ai.presentation.evidence

import com.cybershield.ai.core.toUserMessage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybershield.ai.data.remote.dto.EvidenceItemDto
import com.cybershield.ai.domain.repository.EvidenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class EvidenceUiState(
    val caseId: String,
    val items: List<EvidenceItemDto> = emptyList(),
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

@HiltViewModel
class EvidenceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val evidenceRepository: EvidenceRepository,
) : ViewModel() {

    private val caseId: String = checkNotNull(savedStateHandle["caseId"])

    private val _uiState = MutableStateFlow(EvidenceUiState(caseId = caseId))
    val uiState: StateFlow<EvidenceUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val items = evidenceRepository.list(caseId)
                _uiState.update { it.copy(isLoading = false, items = items) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun upload(file: File, mimeType: String, description: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, error = null, info = null) }
            try {
                val result = evidenceRepository.upload(caseId, description, file, mimeType)
                val ocrNote = if (!result.extracted_text.isNullOrBlank()) {
                    " OCR extracted ${result.extracted_text.length} characters."
                } else ""
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        info = "Uploaded ${result.filename}.$ocrNote",
                    )
                }
                refresh()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isUploading = false, error = e.toUserMessage())
                }
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                evidenceRepository.delete(id)
                refresh()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.toUserMessage()) }
            }
        }
    }
}
