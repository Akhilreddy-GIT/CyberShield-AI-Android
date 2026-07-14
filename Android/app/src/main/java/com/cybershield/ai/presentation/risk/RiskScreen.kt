package com.cybershield.ai.presentation.risk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cybershield.ai.presentation.components.ErrorBanner
import com.cybershield.ai.presentation.components.RiskBadge
import com.cybershield.ai.presentation.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiskScreen(
    onBack: () -> Unit,
    viewModel: RiskViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val factors = listOf(
        "is_repeated_incident" to "Repeated incident" to state.is_repeated_incident,
        "involves_threat_of_harm" to "Threat of harm" to state.involves_threat_of_harm,
        "involves_minor" to "Involves a minor" to state.involves_minor,
        "involves_financial_loss" to "Financial loss" to state.involves_financial_loss,
        "involves_explicit_content" to "Explicit content" to state.involves_explicit_content,
        "accused_knows_victim_location" to "Accused knows location" to state.accused_knows_victim_location,
        "ongoing_blackmail" to "Ongoing blackmail" to state.ongoing_blackmail,
        "victim_reports_feeling_unsafe" to "Feeling immediately unsafe" to state.victim_reports_feeling_unsafe,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Risk assessment") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader(
                "Explainable risk scoring",
                "Matches backend RiskFactorsIn — evidence presence is detected server-side.",
            )
            if (state.error != null) ErrorBanner(state.error!!)
            factors.forEach { (pair, checked) ->
                val (key, label) = pair
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, modifier = Modifier.weight(1f))
                    Switch(checked = checked, onCheckedChange = { viewModel.toggle(key, it) })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = viewModel::submit,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSubmitting) "Assessing…" else "Run assessment")
            }
            state.result?.let { r ->
                Spacer(modifier = Modifier.height(8.dp))
                RiskBadge(r.level)
                Text("Score: ${r.score}", fontWeight = FontWeight.SemiBold)
                Text(r.explanation)
                if (r.triggered_factors.isNotEmpty()) {
                    Text("Triggered factors", fontWeight = FontWeight.Medium)
                    r.triggered_factors.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
