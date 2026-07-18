package com.cybershield.ai.presentation.chat
import androidx.compose.foundation.layout.width

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cybershield.ai.presentation.components.BreathingGuardianOrb
import com.cybershield.ai.presentation.components.CyberShieldTopBar
import com.cybershield.ai.presentation.components.ErrorBanner
import com.cybershield.ai.presentation.components.ExpandableReasoningCard
import com.cybershield.ai.presentation.components.GradientBackground
import com.cybershield.ai.presentation.components.LoadingBox
import com.cybershield.ai.presentation.components.MarkdownText
import com.cybershield.ai.presentation.components.RecoveryAction
import com.cybershield.ai.presentation.components.RecoveryActionPlanCard
import com.cybershield.ai.presentation.components.ThreatAnalysisCard
import com.cybershield.ai.presentation.theme.CardWhite
import com.cybershield.ai.presentation.theme.CriticalContainer
import com.cybershield.ai.presentation.theme.CriticalRed
import com.cybershield.ai.presentation.theme.Emerald
import com.cybershield.ai.presentation.theme.HairlineBorder
import com.cybershield.ai.presentation.theme.JetBrainsMono
import com.cybershield.ai.presentation.theme.Mist
import com.cybershield.ai.presentation.theme.OnEmerald
import com.cybershield.ai.presentation.theme.SoftGray
import com.cybershield.ai.presentation.theme.TextMuted
import com.cybershield.ai.presentation.theme.TextPrimary

@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenCase: (String) -> Unit,
    showTopBack: Boolean = false,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Per-message, per-action completion toggles for backend-supplied
    // recovery steps. Keyed by "messageIndex:actionIndex" since the action
    // list itself now comes entirely from the backend and can vary in
    // length/content per message.
    val recoveryDoneState = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(state.messages.size, state.isSending) {
        val last = state.messages.size + if (state.isSending) 2 else 1
        if (last > 0) {
            listState.animateScrollToItem(last.coerceAtLeast(0))
        }
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        GradientBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CyberShieldTopBar(
                    showBack = showTopBack,
                    onBack = onBack,
                    trailing = {
                        // Case button only appears once the backend has
                        // actually established a case. No fake/placeholder
                        // navigation is ever offered.
                        val caseId = state.caseId
                        if (!caseId.isNullOrBlank()) {
                            TextButton(onClick = { onOpenCase(caseId) }) {
                                Text("Case", color = Emerald)
                            }
                        }
                    },
                )

                if (state.isLoadingHistory) {
                    LoadingBox(Modifier.weight(1f))
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            GuardianHeroHeader(hasMessages = state.messages.isNotEmpty())
                        }

                        if (state.error != null) {
                            item {
                                ErrorBanner(state.error!!, onRetry = null)
                            }
                        }

                        // Helplines are backend-supplied and independent of
                        // any per-message analysis; render whenever the
                        // backend sends them. Styled as the reference
                        // screen's "ELEVATED RISK DETECTED" card.
                        if (state.helplines.isNotEmpty()) {
                            item {
                                Surface(
                                    color = CriticalContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CriticalRed.copy(alpha = 0.4f)),
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Outlined.WarningAmber,
                                                contentDescription = null,
                                                tint = CriticalRed,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "CRITICAL ESCALATION",
                                                fontFamily = JetBrainsMono,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = CriticalRed,
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "Contact help now — these lines are staffed for this kind of incident.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary,
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        state.helplines.forEach { h ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Text(
                                                    h.name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = SoftGray,
                                                )
                                                Text(
                                                    h.number,
                                                    fontFamily = JetBrainsMono,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = CriticalRed,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                    }
                                }
                            }
                        }

                        itemsIndexed(state.messages) { index, msg ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn() + slideInVertically { it / 6 },
                            ) {
                                ChatBubbleItem(msg)
                            }

                            // Rich cards render ONLY when the backend
                            // explicitly marked this message as a confirmed
                            // cyber incident AND supplied an analysis
                            // payload. No inference from role, position,
                            // category text, or message content. If the
                            // backend didn't send analysis, nothing is
                            // fabricated to fill the gap.
                            val analysis = msg.analysis
                            if (msg.isCyberIncident && analysis != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                ThreatAnalysisCard(
                                    title = analysis.title,
                                    riskScore = analysis.riskScore,
                                    riskLevel = analysis.riskLevel,
                                    certainty = analysis.certainty,
                                    target = analysis.target,
                                )
                                if (msg.citedSources.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ExpandableReasoningCard(msg.citedSources)
                                }
                                // Recovery card only appears if the backend
                                // actually supplied recovery steps. Never
                                // shown with hardcoded/fabricated actions.
                                if (analysis.recoveryActions.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    RecoveryActionPlanCard(
                                        actions = analysis.recoveryActions.mapIndexed { actionIndex, action ->
                                            val key = "$index:$actionIndex"
                                            RecoveryAction(
                                                title = action.title,
                                                done = recoveryDoneState[key] ?: false,
                                                recommended = action.recommended,
                                                optional = action.optional,
                                            )
                                        },
                                        onToggle = { actionIndex ->
                                            val key = "$index:$actionIndex"
                                            recoveryDoneState[key] = !(recoveryDoneState[key] ?: false)
                                        },
                                    )
                                }
                            }
                        }

                        if (state.isSending) {
                            item { AnalyzingThreatVectorsRow() }
                        }

                        if (state.messages.isEmpty() && !state.isLoadingHistory) {
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    com.cybershield.ai.presentation.components.TopicCard(
                                        title = "Suspicious Link",
                                        subtitle = "Analyze a URL or message",
                                        icon = Icons.Outlined.WarningAmber,
                                        modifier = Modifier.weight(1f),
                                        onClick = { viewModel.onInputChange("Can you check if this link is safe? ") }
                                    )
                                    com.cybershield.ai.presentation.components.TopicCard(
                                        title = "Account Access",
                                        subtitle = "Locked out or hacked",
                                        icon = Icons.Default.Shield,
                                        modifier = Modifier.weight(1f),
                                        onClick = { viewModel.onInputChange("I've been locked out of my account.") }
                                    )
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }

                GuardianInputBar(
                    value = state.input,
                    onValueChange = viewModel::onInputChange,
                    onSend = viewModel::send,
                    enabled = !state.isSending,
                )
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun GuardianHeroHeader(hasMessages: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!hasMessages) {
            BreathingGuardianOrb(size = 64.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Ask Guardian anything about digital safety.",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Share a link, message, or describe the incident.",
                style = MaterialTheme.typography.bodyMedium,
                color = SoftGray,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                "TODAY · SYSTEM TIME",
                fontFamily = JetBrainsMono,
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )
        }
    }
}

/** Matches the reference screen's "ANALYZING THREAT VECTORS" labeled
 * progress row that appears between the user's message and Guardian's
 * reply — distinct from TypingIndicator's dot animation, this one reads
 * as active technical work rather than a generic "typing" state. */
@Composable
private fun AnalyzingThreatVectorsRow() {
    val transition = rememberInfiniteTransition(label = "analyzing")
    val sweep by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "analyzingSweep",
    )
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(end = 8.dp, top = 4.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Emerald.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = Emerald, modifier = Modifier.size(14.dp))
        }
        Column(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardWhite)
                .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                "ANALYZING THREAT VECTORS",
                fontFamily = JetBrainsMono,
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Mist),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(sweep)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Emerald),
                )
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(msg: ChatBubble) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp, top = 4.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Emerald.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Emerald, modifier = Modifier.size(14.dp))
            }
        }
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (isUser) Modifier.background(Emerald)
                    else Modifier.background(com.cybershield.ai.presentation.theme.AuthFieldBackground).border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
                )
                .padding(14.dp),
        ) {
            if (isUser) {
                // Per DESIGN.md: solid emerald fill pairs with near-black
                // text, not white — matches the reference chat bubble.
                Text(msg.content, color = OnEmerald)
            } else {
                MarkdownText(msg.content, color = TextPrimary)
            }
        }
    }
}

@Composable
private fun GuardianInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(com.cybershield.ai.presentation.theme.AuthFieldBackground)
            .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {}) {
            Icon(Icons.Default.Add, contentDescription = "Attach", tint = TextMuted)
        }
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Describe a security concern…", color = TextMuted) },
            enabled = enabled,
            maxLines = 4,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = Emerald,
            ),
        )
        IconButton(
            onClick = onSend,
            enabled = enabled && value.isNotBlank(),
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (enabled && value.isNotBlank()) Emerald else Mist),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (enabled && value.isNotBlank()) OnEmerald else TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
