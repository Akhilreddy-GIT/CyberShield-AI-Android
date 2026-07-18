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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cybershield.ai.presentation.components.CyberShieldTopBar
import com.cybershield.ai.presentation.components.ErrorBanner
import com.cybershield.ai.presentation.components.GradientBackground
import com.cybershield.ai.presentation.components.PremiumCard
import com.cybershield.ai.presentation.components.PrimaryActionButton
import com.cybershield.ai.presentation.components.RiskBadge
import com.cybershield.ai.presentation.components.ThreatAnalysisCard
import com.cybershield.ai.presentation.theme.SoftGray

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

    Scaffold(containerColor = Color.Transparent) { padding ->
        GradientBackground(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                CyberShieldTopBar(showBack = true, onBack = onBack)
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Risk assessment",
                        style = MaterialTheme.typography.displayMedium,
                    )
                    Text(
                        "Explainable scoring that matches backend RiskFactorsIn — evidence presence is detected server-side.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SoftGray,
                    )
                    if (state.error != null) ErrorBanner(state.error!!)
                    PremiumCard {
                        factors.forEach { (pair, checked) ->
                            val (key, label) = pair
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(label, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = checked,
                                    onCheckedChange = { viewModel.toggle(key, it) },
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = com.cybershield.ai.presentation.theme.Emerald,
                                        checkedThumbColor = com.cybershield.ai.presentation.theme.OnEmerald,
                                        checkedBorderColor = com.cybershield.ai.presentation.theme.Emerald,
                                        uncheckedTrackColor = com.cybershield.ai.presentation.theme.Mist,
                                        uncheckedThumbColor = com.cybershield.ai.presentation.theme.TextMuted,
                                        uncheckedBorderColor = com.cybershield.ai.presentation.theme.Mist,
                                    ),
                                )
                            }
                        }
                    }
                    PrimaryActionButton(
                        text = if (state.isSubmitting) "Assessing…" else "Run assessment",
                        onClick = viewModel::submit,
                        enabled = !state.isSubmitting,
                    )
                    state.result?.let { r ->
                        ThreatAnalysisCard(
                            title = r.level,
                            riskScore = r.score,
                            riskLevel = r.level,
                            certainty = 0.95f,
                            target = "Triggered factors",
                        )
                        PremiumCard {
                            Text(r.explanation, style = MaterialTheme.typography.bodyMedium)
                            if (r.triggered_factors.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Triggered factors", fontWeight = FontWeight.Medium)
                                r.triggered_factors.forEach {
                                    Text("• $it", style = MaterialTheme.typography.bodyMedium, color = SoftGray)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            RiskBadge(r.level)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
