package com.cybershield.ai.presentation.report

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cybershield.ai.presentation.components.ErrorBanner
import com.cybershield.ai.presentation.components.InfoRow
import com.cybershield.ai.presentation.components.LoadingBox
import com.cybershield.ai.presentation.components.RiskBadge
import com.cybershield.ai.presentation.components.SectionHeader
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    onBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val report = state.report

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Case report") },
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.isLoading) LoadingBox()
            if (state.error != null) ErrorBanner(state.error!!)
            report?.let { r ->
                SectionHeader(r.case_id, (r.category ?: "Uncategorized").replace('_', ' '))
                RiskBadge(r.risk_level)
                InfoRow("Status", r.status)
                InfoRow("Risk score", r.risk_score.toString())
                InfoRow("Created", r.created_at)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Incident summary", fontWeight = FontWeight.SemiBold)
                Text(r.incident_summary)
                Text("Timeline", fontWeight = FontWeight.SemiBold)
                if (r.timeline.isEmpty()) Text("No timeline events.")
                else r.timeline.forEach {
                    Text("• ${it.event_time?.let { t -> "$t — " } ?: ""}${it.description}")
                }
                Text("Evidence", fontWeight = FontWeight.SemiBold)
                if (r.evidence.isEmpty()) Text("No evidence uploaded.")
                else r.evidence.forEach {
                    Text("• ${it.filename} (${it.file_type ?: "-"}) ${it.description.orEmpty()}")
                }
                Text("Legal references", fontWeight = FontWeight.SemiBold)
                if (r.legal_references.isEmpty()) {
                    Text("No specific legal references matched yet.")
                } else {
                    r.legal_references.forEach {
                        Text(it.title, fontWeight = FontWeight.Medium)
                        Text(it.summary, style = MaterialTheme.typography.bodyMedium)
                        Text("Source: ${it.source}", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
                Text("Recommended actions", fontWeight = FontWeight.SemiBold)
                r.recommended_actions.forEach { Text("• $it") }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = viewModel::downloadPdf,
                    enabled = !state.isDownloading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.isDownloading) "Downloading…" else "Download PDF")
                }
                state.pdfPath?.let { path ->
                    Button(
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
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open PDF")
                    }
                }
                Text(
                    "Disclaimer: academic demo — not a substitute for legal advice or an official police report.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}
