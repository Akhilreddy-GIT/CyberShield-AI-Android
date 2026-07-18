package com.cybershield.ai.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybershield.ai.data.local.datastore.PrivacyPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrivacyUiState(
    val shareTelemetry: Boolean = false,
    val crashReports: Boolean = true,
)

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val privacyPreferences: PrivacyPreferences,
) : ViewModel() {

    val uiState: StateFlow<PrivacyUiState> = combine(
        privacyPreferences.shareTelemetry,
        privacyPreferences.crashReports,
    ) { telemetry, crash ->
        PrivacyUiState(
            shareTelemetry = telemetry,
            crashReports = crash,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PrivacyUiState()
    )

    fun toggleTelemetry(enabled: Boolean) {
        viewModelScope.launch {
            privacyPreferences.setShareTelemetry(enabled)
        }
    }

    fun toggleCrashReports(enabled: Boolean) {
        viewModelScope.launch {
            privacyPreferences.setCrashReports(enabled)
        }
    }
}
