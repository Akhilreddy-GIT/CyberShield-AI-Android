package com.cybershield.ai.presentation.cases

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cybershield.ai.presentation.components.AccentCard
import com.cybershield.ai.presentation.components.CyberShieldTopBar
import com.cybershield.ai.presentation.components.EmptyState
import com.cybershield.ai.presentation.components.ErrorBanner
import com.cybershield.ai.presentation.components.GradientBackground
import com.cybershield.ai.presentation.components.InfoRow
import com.cybershield.ai.presentation.components.LoadingBox
import com.cybershield.ai.presentation.components.PremiumCard
import com.cybershield.ai.presentation.components.PrimaryActionButton
import com.cybershield.ai.presentation.components.RiskBadge
import com.cybershield.ai.presentation.components.SecondaryActionButton
import com.cybershield.ai.presentation.components.SkeletonCard
import com.cybershield.ai.presentation.components.StatusChip
import com.cybershield.ai.presentation.components.StatusPill
import com.cybershield.ai.presentation.components.StatusTone
import com.cybershield.ai.presentation.theme.CriticalRed
import com.cybershield.ai.presentation.theme.Emerald
import com.cybershield.ai.presentation.theme.JetBrainsMono
import com.cybershield.ai.presentation.theme.Mist
import com.cybershield.ai.presentation.theme.SoftGray
import com.cybershield.ai.presentation.theme.SuccessGreen
import com.cybershield.ai.presentation.theme.TextMuted
import com.cybershield.ai.presentation.theme.TextPrimary

@Composable
fun CasesScreen(
    onBack: () -> Unit,
    onOpenCase: (String) -> Unit,
    showTopBack: Boolean = false,
    viewModel: CasesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        GradientBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CyberShieldTopBar(
                    showBack = showTopBack,
                    onBack = onBack,
                    trailing = {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Emerald)
                        }
                    },
                )
                Text(
                    "Cases",
                    style = MaterialTheme.typography.displayMedium,
                    textAlign = TextAlign.Center,
                    color = TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                )
                Text(
                    "Your active and past recovery investigations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                )

                when {
                    state.isLoading && state.cases.isEmpty() -> {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            repeat(3) { SkeletonCard() }
                        }
                    }
                    state.error != null && state.cases.isEmpty() -> {
                        Column(modifier = Modifier.padding(20.dp)) {
                            ErrorBanner(state.error!!, onRetry = viewModel::refresh)
                        }
                    }
                    state.cases.isEmpty() -> {
                        EmptyState(
                            message = "No cases yet. Start a chat with Guardian to create one.",
                            actionLabel = null,
                        )
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(bottom = 80.dp),
                        ) {
                            items(state.cases, key = { it.id }) { c ->
                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn() + slideInVertically { it / 10 },
                                ) {
                                    CaseListCard(case = c, onClick = { onOpenCase(c.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaseListCard(case: com.cybershield.ai.data.remote.dto.CaseListItemDto, onClick: () -> Unit) {
    com.cybershield.ai.presentation.components.AccentCard(
        onClick = onClick,
        accentColor = Emerald,
        ambientGlow = false,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    (case.category ?: "Uncategorized").replace('_', ' ').replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    case.id,
                    fontFamily = JetBrainsMono,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
            RiskBadge(case.risk_level)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(case.status)
        }
        // Real backend-derived progress only — never fabricated from
        // risk_score. If the backend hasn't populated it yet, we simply
        // omit the row rather than showing a guessed number.
        if (case.recovery_progress_percent != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    case.recovery_stage_label ?: "Recovery progress",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextMuted,
                )
                Text(
                    "${case.recovery_progress_percent}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = Emerald,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            val animated by animateFloatAsState(
                targetValue = case.recovery_progress_percent!!.coerceIn(0, 100) / 100f,
                animationSpec = tween(700, easing = FastOutSlowInEasing),
                label = "caseProgress",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(Mist, RoundedCornerShape(50)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animated)
                        .height(5.dp)
                        .background(Emerald, RoundedCornerShape(50)),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Updated ${case.created_at.take(10)}",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )
    }
}

@Composable
fun CaseDetailScreen(
    onBack: () -> Unit,
    onChat: (String) -> Unit,
    onEvidence: (String) -> Unit,
    onReport: (String) -> Unit,
    onRisk: (String) -> Unit,
    onTimeline: (String) -> Unit,
    viewModel: CaseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.load()
    }
    val case = state.case

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
                    .padding(bottom = 24.dp),
            ) {
                CyberShieldTopBar(showBack = true, onBack = onBack)
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Case details",
                        style = MaterialTheme.typography.displayMedium,
                        color = TextPrimary,
                    )
                    if (state.isLoading) LoadingBox()
                    if (state.error != null) ErrorBanner(state.error!!)
                    if (state.liveNote != null) {
                        StatusPill(state.liveNote!!, tone = StatusTone.Info)
                    }
                    case?.let { c ->
                        AccentCard(accentColor = Emerald) {
                            Text(
                                "CASE ID: #${c.id}",
                                fontFamily = JetBrainsMono,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                (c.category ?: "Uncategorized").replace('_', ' ').replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RiskBadge(c.risk_level)
                                StatusChip(c.status)
                            }
                        }

                        // Real recovery progress card
                        PremiumCard {
                            Text(
                                "RECOVERY PROGRESS",
                                fontFamily = JetBrainsMono,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                c.recovery_stage_label ?: "Awaiting backend sync",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            if (c.recovery_progress_percent != null) {
                                val pct = c.recovery_progress_percent.coerceIn(0, 100)
                                val animated by animateFloatAsState(
                                    targetValue = pct / 100f,
                                    animationSpec = tween(900, easing = FastOutSlowInEasing),
                                    label = "detailProgress",
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .background(Mist, RoundedCornerShape(50)),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(animated)
                                            .height(6.dp)
                                            .background(Emerald, RoundedCornerShape(50)),
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "$pct% Complete",
                                        color = SoftGray,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        if (c.risk_score < 40) "Protected" else "Monitoring",
                                        color = if (c.risk_score < 40) SuccessGreen else SoftGray,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            } else {
                                Text(
                                    "Progress will appear once the backend responds.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                )
                            }
                        }

                        // Risk factors + score, matching the reference "RISK FACTORS" card
                        PremiumCard {
                            Text(
                                "RISK FACTORS",
                                fontFamily = JetBrainsMono,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusPill(
                                    (c.category ?: "General").replace('_', ' '),
                                    tone = StatusTone.Danger,
                                )
                                StatusPill(c.status.replace('_', ' '), tone = StatusTone.Neutral)
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                RiskLevelRing(score = c.risk_score)
                            }
                        }

                        InfoRow("Risk score", "${c.risk_score} / 100")
                        InfoRow("Created", c.created_at.take(19).replace('T', ' '))

                        PrimaryActionButton(
                            text = "Open chat",
                            icon = Icons.AutoMirrored.Filled.Chat,
                            onClick = { onChat(c.id) },
                        )
                        SecondaryActionButton(
                            text = "Evidence vault",
                            icon = Icons.Default.FolderSpecial,
                            onClick = { onEvidence(c.id) },
                        )
                        SecondaryActionButton(
                            text = "Timeline",
                            icon = Icons.Default.Timeline,
                            onClick = { onTimeline(c.id) },
                        )
                        SecondaryActionButton(
                            text = "Assess risk",
                            icon = Icons.Default.Assessment,
                            onClick = { onRisk(c.id) },
                        )
                        PrimaryActionButton(
                            text = "Case report",
                            icon = Icons.Default.Description,
                            onClick = { onReport(c.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskLevelRing(score: Int, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = score.coerceIn(0, 100) / 100f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "riskRing",
    )
    val color = when {
        score >= 80 -> CriticalRed
        score >= 50 -> com.cybershield.ai.presentation.theme.Amber
        else -> Emerald
    }
    Box(modifier = modifier.size(120.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(120.dp)) {
            val stroke = 9.dp.toPx()
            drawArc(
                color = Mist,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score.toString(), style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Text(
                "RISK LVL",
                fontFamily = JetBrainsMono,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
        }
    }
}
