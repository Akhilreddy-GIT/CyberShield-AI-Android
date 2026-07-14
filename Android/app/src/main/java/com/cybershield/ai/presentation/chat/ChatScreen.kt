package com.cybershield.ai.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cybershield.ai.presentation.components.ErrorBanner
import com.cybershield.ai.presentation.components.LoadingBox
import com.cybershield.ai.presentation.theme.Accent
import com.cybershield.ai.presentation.theme.CriticalRed
import com.cybershield.ai.presentation.theme.Navy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenCase: (String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Expert Chat")
                        state.caseId?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.caseId.isNullOrBlank()) {
                        TextButton(onClick = { onOpenCase(state.caseId!!) }) {
                            Text("Case")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.isLoadingHistory) {
                LoadingBox()
            }
            if (state.error != null) {
                ErrorBanner(state.error!!, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
            if (state.helplines.isNotEmpty()) {
                Surface(
                    color = CriticalRed.copy(alpha = 0.12f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Critical escalation — contact help now", color = CriticalRed)
                        state.helplines.forEach { h ->
                            Text("${h.name}: ${h.number}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.messages.isEmpty() && !state.isLoadingHistory) {
                    item {
                        Text(
                            "Describe what happened. CyberShield only discusses cybercrime and digital safety topics.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
                items(state.messages) { msg ->
                    val isUser = msg.role == "user"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 320.dp)
                                .background(
                                    if (isUser) Navy else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(16.dp),
                                )
                                .padding(12.dp),
                        ) {
                            Column {
                                Text(
                                    msg.content,
                                    color = if (isUser) androidx.compose.ui.graphics.Color.White
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                                if (msg.citedSources.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Sources: ${msg.citedSources.joinToString()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isUser) Accent else MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = viewModel::onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Describe the incident…") },
                    enabled = !state.isSending,
                    maxLines = 4,
                )
                IconButton(
                    onClick = viewModel::send,
                    enabled = !state.isSending && state.input.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}
