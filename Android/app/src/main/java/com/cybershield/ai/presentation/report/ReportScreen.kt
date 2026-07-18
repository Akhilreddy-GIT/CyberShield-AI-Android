package com.cybershield.ai.presentation.report

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cybershield.ai.presentation.components.CyberShieldTopBar
import com.cybershield.ai.presentation.components.ErrorBanner
import com.cybershield.ai.presentation.components.GradientBackground
import com.cybershield.ai.presentation.components.InfoRow
import com.cybershield.ai.presentation.components.LoadingBox
import com.cybershield.ai.presentation.components.PremiumCard
import com.cybershield.ai.presentation.components.PrimaryActionButton
import com.cybershield.ai.presentation.components.RiskBadge
import com.cybershield.ai.presentation.components.SecondaryActionButton
import com.cybershield.ai.presentation.theme.Emerald
import com.cybershield.ai.presentation.theme.JetBrainsMono
import com.cybershield.ai.presentation.theme.SoftGray
import com.cybershield.ai.presentation.theme.TextMuted
import com.cybershield.ai.presentation.theme.TextPrimary
import java.io.File

@Composable
fun ReportScreen(
    onBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val report = state.report

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
                        "Case report",
                        style = MaterialTheme.typography.displayMedium,
                    )
                    if (state.isLoading) LoadingBox()
                    if (state.error != null) ErrorBanner(state.error!!)
                    report?.let { r ->
                        PremiumCard {
                            Text(
                                r.case_id,
                                fontFamily = JetBrainsMono,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                            )
                            Text(
                                (r.category ?: "Uncategorized").replace('_', ' '),
                                color = SoftGray,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            RiskBadge(r.risk_level)
                        }
                        InfoRow("Status", r.status)
                        InfoRow("Risk score", r.risk_score.toString())
                        InfoRow("Created", r.created_at)
                        ReportSectionLabel("01", "EXECUTIVE SUMMARY")
                        PremiumCard {
                            Text(r.incident_summary, color = TextPrimary)
                        }
                        ReportSectionLabel("02", "TIMELINE")
                        PremiumCard {
                            if (r.timeline.isEmpty()) {
                                Text("No timeline events.", color = SoftGray)
                            } else {
                                r.timeline.forEach {
                                    Text(
                                        "• ${it.event_time?.let { t -> "$t — " } ?: ""}${it.description}",
                                        color = TextPrimary,
                                    )
                                }
                            }
                        }
                        ReportSectionLabel("03", "EVIDENCE")
                        PremiumCard {
                            if (r.evidence.isEmpty()) {
                                Text("No evidence uploaded.", color = SoftGray)
                            } else {
                                r.evidence.forEach {
                                    Text(
                                        "• ${it.filename} (${it.file_type ?: "-"}) ${it.description.orEmpty()}",
                                        color = TextPrimary,
                                    )
                                }
                            }
                        }
                        ReportSectionLabel("04", "LEGAL REFERENCES")
                        PremiumCard {
                            if (r.legal_references.isEmpty()) {
                                Text("No specific legal references matched yet.", color = SoftGray)
                            } else {
                                r.legal_references.forEach {
                                    Text(it.title, fontWeight = FontWeight.Medium, color = TextPrimary)
                                    Text(it.summary, style = MaterialTheme.typography.bodyMedium)
                                    Text("Source: ${it.source}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                        }
                        ReportSectionLabel("05", "RECOMMENDED ACTIONS")
                        PremiumCard {
                            r.recommended_actions.forEach { Text("• $it", color = TextPrimary) }
                        }
                        PrimaryActionButton(
                            text = if (state.isDownloading) "Downloading…" else "Download PDF",
                            onClick = viewModel::downloadPdf,
                            enabled = !state.isDownloading,
                        )
                        state.pdfPath?.let { path ->
                            SecondaryActionButton(
                                text = "Open PDF",
                                onClick = {
                                    val file = File(path)
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file,
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/pdf")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Open report PDF"))
                                },
                            )
                        }
                        Text(
                            "Disclaimer: academic demo — not a substitute for legal advice or an official police report.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportSectionLabel(number: String, title: String) {
    Text(
        "$number  $title",
        fontFamily = JetBrainsMono,
        style = MaterialTheme.typography.labelMedium,
        color = Emerald,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

