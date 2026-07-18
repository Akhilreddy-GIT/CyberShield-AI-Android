package com.cybershield.ai.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cybershield.ai.presentation.theme.Amber
import com.cybershield.ai.presentation.theme.CriticalRed
import com.cybershield.ai.presentation.theme.Mist
import com.cybershield.ai.presentation.theme.Plum
import com.cybershield.ai.presentation.theme.PlumContainer
import com.cybershield.ai.presentation.theme.SoftGray
import com.cybershield.ai.presentation.theme.SuccessGreen
import com.cybershield.ai.presentation.theme.Teal

@Composable
fun RiskBadge(level: String, modifier: Modifier = Modifier) {
    val tone = when {
        level.contains("Critical", ignoreCase = true) -> StatusTone.Danger
        level.contains("High", ignoreCase = true) -> StatusTone.Warning
        level.contains("Medium", ignoreCase = true) -> StatusTone.Warning
        else -> StatusTone.Success
    }
    StatusPill(text = level.replace('_', ' '), tone = tone, modifier = modifier)
}

@Composable
fun StatusChip(status: String) {
    val tone = when {
        status.contains("resolved", true) || status.contains("closed", true) -> StatusTone.Success
        status.contains("critical", true) || status.contains("escalat", true) -> StatusTone.Danger
        status.contains("progress", true) || status.contains("active", true) -> StatusTone.Info
        else -> StatusTone.Neutral
    }
    StatusPill(text = status.replace('_', ' '), tone = tone)
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SoftGray)
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(color = Plum, strokeWidth = 3.dp)
        Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = SoftGray)
        SkeletonCard()
    }
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = CriticalRed)
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .background(PlumContainer, RoundedCornerShape(24.dp))
                .padding(20.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Plum)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = SoftGray,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryActionButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun OfflineState(modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    EmptyState(
        message = "You're offline. Check your connection and try again.",
        icon = Icons.Outlined.CloudOff,
        actionLabel = if (onRetry != null) "Retry" else null,
        onAction = onRetry,
        modifier = modifier,
    )
}

@Composable
fun AnalyzingState(modifier: Modifier = Modifier, message: String = "Analyzing…") {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BreathingGuardianOrb()
        Spacer(modifier = Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.titleMedium, color = Plum)
        Spacer(modifier = Modifier.height(8.dp))
        TypingIndicator()
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Mist, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontWeight = FontWeight.Medium, color = SoftGray)
        Text(value, color = MaterialTheme.colorScheme.onSurface)
    }
}
