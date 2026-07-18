package com.cybershield.ai.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cybershield.ai.presentation.auth.AccountViewModel
import com.cybershield.ai.presentation.components.CyberShieldTopBar
import com.cybershield.ai.presentation.components.DangerButton
import com.cybershield.ai.presentation.components.GradientBackground
import com.cybershield.ai.presentation.components.PremiumCard
import com.cybershield.ai.presentation.components.SettingsListItem
import com.cybershield.ai.presentation.theme.Emerald
import com.cybershield.ai.presentation.theme.HairlineBorder
import com.cybershield.ai.presentation.theme.JetBrainsMono
import com.cybershield.ai.presentation.theme.SoftGray
import com.cybershield.ai.presentation.theme.TextMuted
import com.cybershield.ai.presentation.theme.TextPrimary

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onNavigatePrivacy: () -> Unit,
    onNavigateLegal: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(containerColor = Color.Transparent) { padding ->
        GradientBackground(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                CyberShieldTopBar(showBack = true, onBack = onBack)

                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    // Profile Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(com.cybershield.ai.presentation.theme.ObsidianSurfaceContainer)
                                .border(1.dp, Emerald.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Emerald, modifier = Modifier.size(36.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                state.username ?: "Anonymous User",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (state.isLoggedIn) "Verified Account" else "Local Session",
                                fontFamily = JetBrainsMono,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.isLoggedIn) Emerald else TextMuted,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        "ACCOUNT DETAILS",
                        fontFamily = JetBrainsMono,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    PremiumCard(contentPadding = 0.dp) {
                        SettingsListItem(
                            title = "Session ID",
                            subtitle = state.anonUserId,
                            icon = Icons.Default.VerifiedUser,
                            iconTint = Emerald,
                            trailing = {
                                Text("Active", fontFamily = JetBrainsMono, color = Emerald, style = MaterialTheme.typography.labelSmall)
                            }
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HairlineBorder))
                        SettingsListItem(
                            title = "Status",
                            subtitle = if (state.isLoggedIn) "Authenticated" else "Guest",
                            icon = Icons.Default.Security,
                            iconTint = Emerald,
                            trailing = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        "PREFERENCES",
                        fontFamily = JetBrainsMono,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PremiumCard(contentPadding = 0.dp) {
                        SettingsListItem(
                            title = "Privacy & Permissions",
                            subtitle = "Manage data access",
                            icon = Icons.Default.Lock,
                            iconTint = Emerald,
                            onClick = onNavigatePrivacy,
                            trailing = {
                                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        "ABOUT",
                        fontFamily = JetBrainsMono,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PremiumCard(contentPadding = 0.dp) {
                        SettingsListItem(
                            title = "Legal & Compliance",
                            subtitle = "Terms of Service, Privacy Policy",
                            icon = Icons.Default.Info,
                            iconTint = SoftGray,
                            onClick = onNavigateLegal,
                            trailing = {
                                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    if (state.isLoggedIn) {
                        DangerButton(
                            text = "Sign Out",
                            onClick = viewModel::logout,
                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                        )
                    } else {
                        // For anonymous users, the "DangerButton" is somewhat equivalent. Let's not provide logout for anon users.
                        Text(
                            "You are currently operating in a local, anonymous session. Data remains on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}
