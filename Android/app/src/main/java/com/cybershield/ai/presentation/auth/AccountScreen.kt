package com.cybershield.ai.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cybershield.ai.presentation.components.ErrorBanner
import com.cybershield.ai.presentation.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account") },
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
            SectionHeader(
                "Optional account",
                "Anonymous reporting always works. Register only if you want cases to follow you across devices.",
            )
            Text("Session ID: ${state.anonUserId}", style = MaterialTheme.typography.labelMedium)
            if (state.isLoggedIn) {
                Text("Signed in as ${state.username}", fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign out")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.modeLogin,
                        onClick = { viewModel.setModeLogin(true) },
                        label = { Text("Login") },
                    )
                    FilterChip(
                        selected = !state.modeLogin,
                        onClick = { viewModel.setModeLogin(false) },
                        label = { Text("Register") },
                    )
                }
                OutlinedTextField(
                    value = state.formUsername,
                    onValueChange = viewModel::onUsernameChange,
                    label = { Text("Username (min 3)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.formPassword,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text("Password (min 6)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Button(
                    onClick = viewModel::submit,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            state.isLoading -> "Please wait…"
                            state.modeLogin -> "Login"
                            else -> "Register"
                        }
                    )
                }
            }
            if (state.error != null) ErrorBanner(state.error!!)
            if (state.info != null) Text(state.info!!, color = MaterialTheme.colorScheme.tertiary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "No email or phone required. Backend stores cases against anon_user_id.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
        }
    }
}
