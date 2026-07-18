package com.cybershield.ai.presentation.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cybershield.ai.presentation.components.CyberShieldTopBar
import com.cybershield.ai.presentation.components.GradientBackground
import com.cybershield.ai.presentation.components.PremiumCard
import com.cybershield.ai.presentation.components.SettingsListItem
import com.cybershield.ai.presentation.theme.Emerald
import com.cybershield.ai.presentation.theme.HairlineBorder
import com.cybershield.ai.presentation.theme.JetBrainsMono
import com.cybershield.ai.presentation.theme.Mist
import com.cybershield.ai.presentation.theme.TextMuted
import com.cybershield.ai.presentation.theme.TextPrimary

@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasCamera by remember { mutableStateOf(false) }
    var hasLocation by remember { mutableStateOf(false) }

    fun refreshPermissions() {
        hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refreshPermissions()
    }

    val openSettings = {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        GradientBackground(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                CyberShieldTopBar(showBack = true, onBack = onBack)

                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(
                        "Privacy & Permissions",
                        style = MaterialTheme.typography.displayMedium,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        "HARDWARE ACCESS",
                        fontFamily = JetBrainsMono,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PremiumCard(contentPadding = 0.dp) {
                        SettingsListItem(
                            title = "Camera",
                            subtitle = if (hasCamera) "Allowed" else "Denied",
                            icon = Icons.Default.CameraAlt,
                            iconTint = if (hasCamera) Emerald else TextMuted,
                            trailing = {
                                TextButton(onClick = openSettings) {
                                    Text("Manage", color = Emerald)
                                }
                            }
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HairlineBorder))
                        SettingsListItem(
                            title = "Location",
                            subtitle = if (hasLocation) "Allowed" else "Denied",
                            icon = Icons.Default.LocationOn,
                            iconTint = if (hasLocation) Emerald else TextMuted,
                            trailing = {
                                TextButton(onClick = openSettings) {
                                    Text("Manage", color = Emerald)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        "DATA & TELEMETRY",
                        fontFamily = JetBrainsMono,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PremiumCard(contentPadding = 0.dp) {
                        SettingsListItem(
                            title = "Threat Telemetry",
                            subtitle = "Share anonymized threat patterns",
                            icon = Icons.Default.Analytics,
                            iconTint = Emerald,
                            trailing = {
                                Switch(
                                    checked = state.shareTelemetry,
                                    onCheckedChange = { viewModel.toggleTelemetry(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Emerald,
                                        uncheckedThumbColor = TextMuted,
                                        uncheckedTrackColor = Mist,
                                    )
                                )
                            }
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HairlineBorder))
                        SettingsListItem(
                            title = "Crash Reports",
                            subtitle = "Help improve stability",
                            icon = Icons.Default.BugReport,
                            iconTint = Emerald,
                            trailing = {
                                Switch(
                                    checked = state.crashReports,
                                    onCheckedChange = { viewModel.toggleCrashReports(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Emerald,
                                        uncheckedThumbColor = TextMuted,
                                        uncheckedTrackColor = Mist,
                                    )
                                )
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}
