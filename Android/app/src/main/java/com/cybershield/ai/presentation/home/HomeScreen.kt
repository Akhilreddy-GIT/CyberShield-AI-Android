package com.cybershield.ai.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cybershield.ai.presentation.theme.Accent
import com.cybershield.ai.presentation.theme.Deep
import com.cybershield.ai.presentation.theme.Teal

private data class HomeAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onChat: (String?) -> Unit,
    onCases: () -> Unit,
    onAccount: () -> Unit,
    onLegal: () -> Unit,
    onEmergency: () -> Unit,
    onAwareness: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val actions = listOf(
        HomeAction("AI Expert Chat", "Guided cybercrime interview", Icons.AutoMirrored.Filled.Chat) {
            onChat(state.activeCaseId)
        },
        HomeAction("My Cases", "Status, risk & reports", Icons.Default.Folder, onCases),
        HomeAction("Emergency", "Helplines & escalation", Icons.Default.Emergency, onEmergency),
        HomeAction("Legal Reference", "IT Act & BNS summaries", Icons.Default.Balance, onLegal),
        HomeAction("Awareness", "Stay safer online", Icons.Default.Lightbulb, onAwareness),
        HomeAction("Account", "Optional login", Icons.Default.AccountCircle, onAccount),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Accent)
                        Spacer(modifier = Modifier.padding(6.dp))
                        Text("CyberShield AI", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Deep,
                    titleContentColor = androidx.compose.ui.graphics.Color.White,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(
                "Anonymous-first cyber assistance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Domain-locked guidance, risk assessment, evidence vault, and case reports.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(12.dp))
            val statusText = when (state.backendOnline) {
                true -> "Backend online" + if (state.llmConfigured) " · LLM ready" else " · KB fallback"
                false -> "Backend offline — start FastAPI on :8000"
                null -> "Checking backend…"
            }
            Text(
                statusText,
                color = when (state.backendOnline) {
                    true -> Teal
                    false -> Accent
                    null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                },
                style = MaterialTheme.typography.labelLarge,
            )
            if (state.username != null) {
                Text("Signed in as ${state.username}", style = MaterialTheme.typography.labelMedium)
            } else {
                Text("Anonymous session active", style = MaterialTheme.typography.labelMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onChat(null) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start new conversation")
            }
            if (!state.activeCaseId.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onChat(state.activeCaseId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Continue active case")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(actions) { action ->
                    Card(
                        onClick = action.onClick,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(action.title, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                action.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            )
                        }
                    }
                }
            }
        }
    }
}
