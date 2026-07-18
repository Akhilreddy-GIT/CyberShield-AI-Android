package com.cybershield.ai.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cybershield.ai.presentation.components.GradientBackground
import com.cybershield.ai.presentation.components.PrimaryActionButton
import com.cybershield.ai.presentation.components.SecondaryActionButton
import com.cybershield.ai.presentation.theme.AuthFieldBackground
import com.cybershield.ai.presentation.theme.BebasNeue
import com.cybershield.ai.presentation.theme.CsRadius
import com.cybershield.ai.presentation.theme.CsSpacing
import com.cybershield.ai.presentation.theme.Emerald
import com.cybershield.ai.presentation.theme.HairlineBorder
import com.cybershield.ai.presentation.theme.JetBrainsMono
import com.cybershield.ai.presentation.theme.SoftGray
import com.cybershield.ai.presentation.theme.TextMuted
import com.cybershield.ai.presentation.theme.TextPrimary

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateRegister: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) {
            onLoginSuccess()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.setModeLogin(true)
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        GradientBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = CsSpacing.screenHorizontal),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = "Logo",
                    tint = Emerald,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "CYBERSHIELD",
                    fontFamily = BebasNeue,
                    fontSize = 32.sp,
                    letterSpacing = 4.sp,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "AUTHORIZED ACCESS ONLY",
                    fontFamily = JetBrainsMono,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    color = SoftGray,
                )
                Spacer(modifier = Modifier.height(48.dp))

                val fieldColors = TextFieldDefaults.colors(
                    focusedContainerColor = AuthFieldBackground,
                    unfocusedContainerColor = AuthFieldBackground,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                )

                // Initialize Key Pair (Username)
                TextField(
                    value = state.formUsername,
                    onValueChange = viewModel::onUsernameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CsRadius.md))
                        .border(1.dp, HairlineBorder, RoundedCornerShape(CsRadius.md)),
                    colors = fieldColors,
                    label = { Text("INITIALIZE KEY PAIR", fontFamily = JetBrainsMono, fontSize = 12.sp, color = TextMuted) },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(16.dp))

                // API Key (Password)
                TextField(
                    value = state.formPassword,
                    onValueChange = viewModel::onPasswordChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CsRadius.md))
                        .border(1.dp, HairlineBorder, RoundedCornerShape(CsRadius.md)),
                    colors = fieldColors,
                    label = { Text("API KEY", fontFamily = JetBrainsMono, fontSize = 12.sp, color = TextMuted) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                if (state.error != null) {
                    Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (state.isLoading) {
                    CircularProgressIndicator(color = Emerald)
                } else {
                    PrimaryActionButton(
                        text = "Authenticate",
                        onClick = viewModel::submit,
                        enabled = state.formUsername.isNotBlank() && state.formPassword.isNotBlank()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    SecondaryActionButton(
                        text = "Biometric Login",
                        onClick = {
                            // As per user feedback, keep button present but gracefully disable with "Coming Soon" if no architecture for it.
                            // The current architecture doesn't have a secure token store for biometrics without a password yet.
                        },
                        icon = Icons.Default.Fingerprint
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Coming Soon",
                        fontFamily = JetBrainsMono,
                        fontSize = 10.sp,
                        color = TextMuted,
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    "Request New Credentials",
                    color = Emerald,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { onNavigateRegister() }
                )
            }
        }
    }
}
