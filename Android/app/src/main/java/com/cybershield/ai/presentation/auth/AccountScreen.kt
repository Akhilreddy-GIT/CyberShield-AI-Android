package com.cybershield.ai.presentation.auth


import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cybershield.ai.presentation.components.CyberShieldTopBar
import com.cybershield.ai.presentation.components.ErrorBanner
import com.cybershield.ai.presentation.components.GradientBackground
import com.cybershield.ai.presentation.components.PremiumCard
import com.cybershield.ai.presentation.components.PrimaryActionButton
import com.cybershield.ai.presentation.components.SecondaryActionButton
import com.cybershield.ai.presentation.theme.CardWhite
import com.cybershield.ai.presentation.theme.Emerald
import com.cybershield.ai.presentation.theme.JetBrainsMono
import com.cybershield.ai.presentation.theme.Mist
import com.cybershield.ai.presentation.theme.OnEmerald
import com.cybershield.ai.presentation.theme.SoftGray
import com.cybershield.ai.presentation.theme.TextMuted
import com.cybershield.ai.presentation.theme.TextPrimary

@Composable
fun AccountScreen(
    onBack: () -> Unit,
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
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Account",
                        style = MaterialTheme.typography.displayMedium,
                        color = TextPrimary,
                    )
                    Text(
                        "Anonymous reporting always works. Register only if you want cases to follow you across devices.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SoftGray,
                    )
                    PremiumCard {
                        Text(
                            "SESSION ID",
                            fontFamily = JetBrainsMono,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            state.anonUserId,
                            fontFamily = JetBrainsMono,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                        )
                    }
                    if (state.isLoggedIn) {
                        Text(
                            "Signed in as ${state.username}",
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                        )
                        SecondaryActionButton(text = "Sign out", onClick = viewModel::logout)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.modeLogin,
                                onClick = { viewModel.setModeLogin(true) },
                                label = { Text("Login") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color.Transparent,
                                    labelColor = TextMuted,
                                    selectedContainerColor = Emerald,
                                    selectedLabelColor = OnEmerald,
                                ),
                            )
                            FilterChip(
                                selected = !state.modeLogin,
                                onClick = { viewModel.setModeLogin(false) },
                                label = { Text("Register") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color.Transparent,
                                    labelColor = TextMuted,
                                    selectedContainerColor = Emerald,
                                    selectedLabelColor = OnEmerald,
                                ),
                            )
                        }
                        val fieldColors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedContainerColor = com.cybershield.ai.presentation.theme.AuthFieldBackground,
                            unfocusedContainerColor = com.cybershield.ai.presentation.theme.AuthFieldBackground,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                        )
                        androidx.compose.material3.TextField(
                            value = state.formUsername,
                            onValueChange = viewModel::onUsernameChange,
                            label = { Text("Username (min 3)", color = TextMuted) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp)),

                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = fieldColors,
                        )
                        androidx.compose.material3.TextField(
                            value = state.formPassword,
                            onValueChange = viewModel::onPasswordChange,
                            label = { Text("Password (min 6)", color = TextMuted) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp)),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = fieldColors,
                        )
                        PrimaryActionButton(
                            text = when {
                                state.isLoading -> "Please wait…"
                                state.modeLogin -> "Login"
                                else -> "Register"
                            },
                            onClick = viewModel::submit,
                            enabled = !state.isLoading,
                        )
                    }
                    if (state.error != null) ErrorBanner(state.error!!)
                    if (state.info != null) {
                        Text(state.info!!, color = MaterialTheme.colorScheme.tertiary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No email or phone required. Backend stores cases against anon_user_id.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
