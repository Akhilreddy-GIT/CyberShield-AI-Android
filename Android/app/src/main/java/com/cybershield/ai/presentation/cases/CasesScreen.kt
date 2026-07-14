package com.cybershield.ai.presentation.cases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cybershield.ai.presentation.components.EmptyState
import com.cybershield.ai.presentation.components.ErrorBanner
import com.cybershield.ai.presentation.components.InfoRow
import com.cybershield.ai.presentation.components.LoadingBox
import com.cybershield.ai.presentation.components.RiskBadge
import com.cybershield.ai.presentation.components.SectionHeader
import com.cybershield.ai.presentation.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CasesScreen(
    onBack: () -> Unit,
    onOpenCase: (String) -> Unit,
    viewModel: CasesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Cases") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingBox(Modifier.padding(padding))
            state.error != null && state.cases.isEmpty() -> {
                Column(Modifier.padding(padding).padding(16.dp)) {
                    ErrorBanner(state.error!!)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = viewModel::refresh) { Text("Retry") }
                }
            }
            state.cases.isEmpty() -> EmptyState("No cases yet. Start a chat to create one.")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.cases, key = { it.id }) { c ->
                    Card(onClick = { onOpenCase(c.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(c.id, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                (c.category ?: "Uncategorized").replace('_', ' '),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RiskBadge(c.risk_level)
                                StatusChip(c.status)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseDetailScreen(
    onBack: () -> Unit,
    onChat: (String) -> Unit,
    onEvidence: (String) -> Unit,
    onReport: (String) -> Unit,
    onRisk: (String) -> Unit,
    onTimeline: (String) -> Unit,
    viewModel: CaseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val case = state.case

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Case details") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.isLoading) LoadingBox()
            if (state.error != null) ErrorBanner(state.error!!)
            if (state.liveNote != null) {
                Text(state.liveNote!!, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge)
            }
            case?.let { c ->
                SectionHeader(c.id, (c.category ?: "Uncategorized").replace('_', ' '))
                RiskBadge(c.risk_level)
                StatusChip(c.status)
                InfoRow("Risk score", c.risk_score.toString())
                InfoRow("Created", c.created_at)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { onChat(c.id) }, modifier = Modifier.fillMaxWidth()) { Text("Open chat") }
                OutlinedButton(onClick = { onEvidence(c.id) }, modifier = Modifier.fillMaxWidth()) { Text("Evidence vault") }
                OutlinedButton(onClick = { onTimeline(c.id) }, modifier = Modifier.fillMaxWidth()) { Text("Timeline") }
                OutlinedButton(onClick = { onRisk(c.id) }, modifier = Modifier.fillMaxWidth()) { Text("Assess risk") }
                Button(onClick = { onReport(c.id) }, modifier = Modifier.fillMaxWidth()) { Text("Case report") }
            }
        }
    }
}
