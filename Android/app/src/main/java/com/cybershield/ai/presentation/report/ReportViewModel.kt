package com.cybershield.ai.presentation.report

import com.cybershield.ai.core.toUserMessage

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybershield.ai.data.remote.dto.ReportDto
import com.cybershield.ai.domain.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ReportUiState(
    val report: ReportDto? = null,
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val error: String? = null,
    val pdfPath: String? = null,
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reportRepository: ReportRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val caseId: String = checkNotNull(savedStateHandle["caseId"])

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val report = reportRepository.getReport(caseId)
                _uiState.update { it.copy(isLoading = false, report = report) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun downloadPdf() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, error = null) }
            try {
                val dest = File(context.cacheDir, "${caseId}_report.pdf")
                reportRepository.downloadPdf(caseId, dest)
                _uiState.update { it.copy(isDownloading = false, pdfPath = dest.absolutePath) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isDownloading = false, error = e.toUserMessage()) }
            }
        }
    }
}
