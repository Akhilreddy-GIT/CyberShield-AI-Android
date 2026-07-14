package com.cybershield.ai.presentation.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.cybershield.ai.presentation.components.LoadingBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onBack: () -> Unit,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Incident timeline") },
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
        ) {
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("What happened?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.eventTime,
                onValueChange = viewModel::onEventTimeChange,
                label = { Text("When? (optional, approximate OK)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = viewModel::add,
                enabled = !state.isSaving && state.description.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSaving) "Saving…" else "Add event")
            }
            if (state.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                ErrorBanner(state.error!!)
            }
            Spacer(modifier = Modifier.height(12.dp))
            when {
                state.isLoading -> LoadingBox()
                state.events.isEmpty() -> EmptyState("No timeline events yet.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.events, key = { it.id }) { e ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(e.description, fontWeight = FontWeight.Medium)
                                if (!e.event_time.isNullOrBlank()) {
                                    Text("Reported time: ${e.event_time}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(e.created_at, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
