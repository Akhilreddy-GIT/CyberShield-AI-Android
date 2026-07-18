package com.cybershield.ai.presentation.evidence

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cybershield.ai.presentation.components.CyberShieldTopBar
import com.cybershield.ai.presentation.components.EmptyState
import com.cybershield.ai.presentation.components.GradientBackground
import com.cybershield.ai.presentation.components.PrimaryActionButton
import com.cybershield.ai.presentation.components.SecondaryActionButton
import com.cybershield.ai.presentation.home.HomeViewModel
import com.cybershield.ai.presentation.theme.SoftGray

/**
 * Vault tab entry — opens evidence for the active case, or a guided empty state.
 */
@Composable
fun VaultHubScreen(
    onOpenEvidence: (String) -> Unit,
    onStartChat: () -> Unit,
    onOpenCases: () -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel(),
) {
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()
    val active = state.activeCaseId

    Scaffold(containerColor = Color.Transparent) { padding ->
        GradientBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CyberShieldTopBar()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Evidence Repository",
                        style = MaterialTheme.typography.displayMedium,
                        textAlign = TextAlign.Center,
                        color = com.cybershield.ai.presentation.theme.TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "A secure, chronological record of uploaded artifacts and AI findings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SoftGray,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    if (!active.isNullOrBlank()) {
                        Text(
                            "Active case ready",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = com.cybershield.ai.presentation.theme.TextPrimary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(active, style = MaterialTheme.typography.bodySmall, color = SoftGray)
                        Spacer(modifier = Modifier.height(20.dp))
                        PrimaryActionButton(
                            text = "Open Evidence Repository",
                            icon = Icons.Outlined.FolderSpecial,
                            onClick = { onOpenEvidence(active) },
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SecondaryActionButton(
                            text = "Choose another case",
                            onClick = onOpenCases,
                        )
                    } else {
                        EmptyState(
                            message = "Start a Guardian session or open a case to begin collecting evidence.",
                            icon = Icons.Outlined.FolderSpecial,
                            actionLabel = "Talk to Guardian",
                            onAction = onStartChat,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SecondaryActionButton(text = "Browse cases", onClick = onOpenCases)
                    }
                }
            }
        }
    }
}
