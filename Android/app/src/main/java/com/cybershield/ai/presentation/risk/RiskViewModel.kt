package com.cybershield.ai.presentation.risk

import com.cybershield.ai.core.toUserMessage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybershield.ai.data.remote.dto.RiskAssessmentOutDto
import com.cybershield.ai.data.remote.dto.RiskFactorsInDto
import com.cybershield.ai.domain.repository.CaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RiskUiState(
    val is_repeated_incident: Boolean = false,
    val involves_threat_of_harm: Boolean = false,
    val involves_minor: Boolean = false,
    val involves_financial_loss: Boolean = false,
    val involves_explicit_content: Boolean = false,
    val accused_knows_victim_location: Boolean = false,
    val ongoing_blackmail: Boolean = false,
    val victim_reports_feeling_unsafe: Boolean = false,
    val isSubmitting: Boolean = false,
    val result: RiskAssessmentOutDto? = null,
    val error: String? = null,
)

@HiltViewModel
class RiskViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val caseRepository: CaseRepository,
) : ViewModel() {

    private val caseId: String = checkNotNull(savedStateHandle["caseId"])

    private val _uiState = MutableStateFlow(RiskUiState())
    val uiState: StateFlow<RiskUiState> = _uiState.asStateFlow()

    fun toggle(field: String, value: Boolean) {
        _uiState.update {
            when (field) {
                "is_repeated_incident" -> it.copy(is_repeated_incident = value)
                "involves_threat_of_harm" -> it.copy(involves_threat_of_harm = value)
                "involves_minor" -> it.copy(involves_minor = value)
                "involves_financial_loss" -> it.copy(involves_financial_loss = value)
                "involves_explicit_content" -> it.copy(involves_explicit_content = value)
                "accused_knows_victim_location" -> it.copy(accused_knows_victim_location = value)
                "ongoing_blackmail" -> it.copy(ongoing_blackmail = value)
                "victim_reports_feeling_unsafe" -> it.copy(victim_reports_feeling_unsafe = value)
                else -> it
            }
        }
    }

    fun submit() {
        viewModelScope.launch {
            val s = _uiState.value
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            try {
                val result = caseRepository.assessRisk(
                    RiskFactorsInDto(
                        case_id = caseId,
                        is_repeated_incident = s.is_repeated_incident,
                        involves_threat_of_harm = s.involves_threat_of_harm,
                        involves_minor = s.involves_minor,
                        involves_financial_loss = s.involves_financial_loss,
                        involves_explicit_content = s.involves_explicit_content,
                        accused_knows_victim_location = s.accused_knows_victim_location,
                        ongoing_blackmail = s.ongoing_blackmail,
                        victim_reports_feeling_unsafe = s.victim_reports_feeling_unsafe,
                    )
                )
                _uiState.update { it.copy(isSubmitting = false, result = result) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, error = e.toUserMessage()) }
            }
        }
    }
}
