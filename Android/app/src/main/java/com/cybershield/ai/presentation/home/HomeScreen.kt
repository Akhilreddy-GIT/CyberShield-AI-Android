package com.cybershield.ai.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cybershield.ai.presentation.components.CyberShieldTopBar
import com.cybershield.ai.presentation.components.GradientBackground
import com.cybershield.ai.presentation.components.PremiumCard
import com.cybershield.ai.presentation.theme.CriticalRed
import com.cybershield.ai.presentation.theme.Emerald
import com.cybershield.ai.presentation.theme.JetBrainsMono
import com.cybershield.ai.presentation.theme.Mist
import com.cybershield.ai.presentation.theme.ObsidianSurfaceContainer
import com.cybershield.ai.presentation.theme.SoftGray
import com.cybershield.ai.presentation.theme.SuccessGreen
import com.cybershield.ai.presentation.theme.TextMuted
import com.cybershield.ai.presentation.theme.TextPrimary
import kotlinx.coroutines.delay

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
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(40)
        visible = true
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshHealth()
    }

    val protected = state.backendOnline == true
    // Security score: derived from real backend state only. With no active
    // case and a healthy backend, this reads 100 (nothing to recover from).
    // With an active case, it's the real recovery_progress_percent from the
    // backend — never a client-side guess.
    val securityScore = when {
        state.backendOnline == null -> null
        state.activeCaseId.isNullOrBlank() -> if (protected) 100 else 0
        else -> state.recoveryProgressPercent
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
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 100.dp),
            ) {
                CyberShieldTopBar(onAlertClick = onEmergency)
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn() + slideInVertically { it / 8 },
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {

                        // ---- GREETING HEADER ----
                        val displayName = state.username?.replaceFirstChar { it.uppercase() } ?: "Agent"
                        Text(
                            "Good morning, $displayName",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary,
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // ---- SYSTEM CORE: security score ring ----
                        PremiumCard(contentPadding = 24.dp, ambientGlow = true) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "SYSTEM CORE",
                                    fontFamily = JetBrainsMono,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Emerald,
                                )
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = when (state.backendOnline) {
                                        true -> SuccessGreen
                                        false -> CriticalRed
                                        null -> TextMuted
                                    },
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                SecurityScoreRing(score = securityScore)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Safety Status: ${safetyStatusLabel(securityScore)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                color = TextPrimary,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                when {
                                    state.backendOnline == null -> "Verifying backend connection…"
                                    state.backendOnline == false -> "Backend offline — start FastAPI on :8000 to resume monitoring."
                                    state.activeCaseId.isNullOrBlank() -> "Active monitoring engaged. No open cases."
                                    else -> "Active monitoring engaged. Recovery in progress on 1 open case."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // ---- PRIORITY OVERRIDE: emergency ----
                        PremiumCard(
                            onClick = onEmergency,
                            background = androidx.compose.ui.graphics.Color(0xFF1A0E0F),
                            contentPadding = 16.dp,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.WarningAmber,
                                        contentDescription = null,
                                        tint = CriticalRed,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "PRIORITY OVERRIDE",
                                        fontFamily = JetBrainsMono,
                                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                        letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing,
                                        color = CriticalRed,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "I'M IN DANGER RIGHT NOW",
                                style = MaterialTheme.typography.headlineSmall,
                                color = CriticalRed,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(CriticalRed.copy(alpha = 0.25f)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.3f)
                                        .height(3.dp)
                                        .background(CriticalRed),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // ---- TALK TO CYBERSHIELD AI ----
                        PremiumCard(onClick = { onChat(state.activeCaseId) }, contentPadding = 18.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(ObsidianSurfaceContainer)
                                            .border(1.dp, Emerald.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Default.Shield,
                                            contentDescription = null,
                                            tint = Emerald,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        "Talk to CyberShield AI",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Text(
                                    "ASSISTANT",
                                    fontFamily = JetBrainsMono,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                if (state.activeCaseId.isNullOrBlank()) {
                                    "Describe what happened and Guardian will assess risk, open a case if needed, and walk you through recovery."
                                } else {
                                    "Continue your conversation — Guardian has context on your active case and can pick up where you left off."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (state.activeCaseId.isNullOrBlank()) "Start a conversation" else "Continue conversation",
                                    color = Emerald,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Emerald,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // ---- RECOVERY PROGRESS (real backend value) ----
                        if (!state.activeCaseId.isNullOrBlank()) {
                            PremiumCard(onClick = onCases) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "RECOVERY PROGRESS",
                                        fontFamily = JetBrainsMono,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextMuted,
                                    )
                                    Icon(
                                        Icons.Outlined.FolderOpen,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    (state.activeCaseCategory ?: "Case").replace('_', ' ').replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    state.recoveryStageLabel ?: "Syncing with backend…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                if (state.recoveryProgressPercent != null) {
                                    val pct = state.recoveryProgressPercent!!.coerceIn(0, 100)
                                    val animated by animateFloatAsState(
                                        targetValue = pct / 100f,
                                        animationSpec = tween(900, easing = FastOutSlowInEasing),
                                        label = "recoveryProgress",
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(Mist),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(animated)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(Emerald),
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "$pct% Complete",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = SoftGray,
                                    )
                                } else {
                                    Text(
                                        "Progress will appear once the backend responds.",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TextMuted,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // ---- STATS ROW ----
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard(
                                icon = Icons.Outlined.Bolt,
                                value = if (protected) "Active" else "—",
                                label = "GUARDIAN",
                                tint = Emerald,
                                modifier = Modifier.weight(1f),
                            )
                            StatCard(
                                icon = Icons.Outlined.WarningAmber,
                                value = if (state.activeCaseId.isNullOrBlank()) "0" else "1",
                                label = "ACTIVE CASES",
                                tint = com.cybershield.ai.presentation.theme.Amber,
                                modifier = Modifier.weight(1f),
                                onClick = onCases,
                            )
                            StatCard(
                                icon = Icons.Outlined.CheckCircleOutline,
                                value = if (protected) "Online" else "Off",
                                label = "BACKEND",
                                tint = if (protected) SuccessGreen else CriticalRed,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            "MORE TOOLS",
                            fontFamily = JetBrainsMono,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MiniToolCard("Legal", Icons.Outlined.Balance, Modifier.weight(1f), onLegal)
                            MiniToolCard("Awareness", Icons.Outlined.Lightbulb, Modifier.weight(1f), onAwareness)
                            MiniToolCard("Account", Icons.Outlined.AccountCircle, Modifier.weight(1f), onAccount)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = { onChat(null) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Start new conversation", color = Emerald)
                        }
                    }
                }
            }
        }
    }
}

private fun safetyStatusLabel(score: Int?): String = when {
    score == null -> "Checking…"
    score >= 80 -> "Excellent"
    score >= 50 -> "Moderate"
    score >= 20 -> "At Risk"
    else -> "Critical"
}

/** Ring progress indicator matching the "SYSTEM CORE" reference screen —
 * large centered score number with a partial emerald ring, drawn with
 * Canvas rather than a stock ProgressIndicator to get the exact stroke
 * cap and track styling from the design system. */
@Composable
private fun SecurityScoreRing(score: Int?, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = (score ?: 0) / 100f,
        animationSpec = tween(1100, easing = FastOutSlowInEasing),
        label = "scoreRing",
    )
    val ringColor = when {
        score == null -> TextMuted
        score >= 80 -> SuccessGreen
        score >= 50 -> Emerald
        score >= 20 -> com.cybershield.ai.presentation.theme.Amber
        else -> CriticalRed
    }
    Box(modifier = modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val stroke = 12.dp.toPx()
            drawArc(
                color = Mist,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                size = Size(size.width - stroke, size.height - stroke),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                size = Size(size.width - stroke, size.height - stroke),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                score?.toString() ?: "—",
                style = MaterialTheme.typography.displayLarge,
                color = TextPrimary,
            )
            Text(
                "SECURITY",
                fontFamily = JetBrainsMono,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    PremiumCard(modifier = modifier, onClick = onClick, contentPadding = 14.dp) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            fontFamily = JetBrainsMono,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )
    }
}

@Composable
private fun MiniToolCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    PremiumCard(modifier = modifier, onClick = onClick, contentPadding = 14.dp) {
        Icon(icon, contentDescription = null, tint = Emerald, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}
