package com.cybershield.ai.presentation.emergency

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cybershield.ai.domain.model.StaticContent
import com.cybershield.ai.presentation.components.BreathingGuardianOrb
import com.cybershield.ai.presentation.components.CyberShieldTopBar
import com.cybershield.ai.presentation.components.GradientBackground
import com.cybershield.ai.presentation.components.PremiumCard
import com.cybershield.ai.presentation.components.PrimaryActionButton
import com.cybershield.ai.presentation.components.SecondaryActionButton
import com.cybershield.ai.presentation.theme.Emerald
import com.cybershield.ai.presentation.theme.JetBrainsMono
import com.cybershield.ai.presentation.theme.ObsidianSurfaceContainerLowest
import com.cybershield.ai.presentation.theme.SoftGray
import com.cybershield.ai.presentation.theme.TextMuted
import com.cybershield.ai.presentation.theme.TextPrimary
import kotlinx.coroutines.delay

private data class ImmediateStep(
    val title: String,
    val description: String,
    val icon: ImageVector,
)

@Composable
fun EmergencyScreen(onBack: () -> Unit, showTopBack: Boolean = false) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50)
        visible = true
    }

    val steps = listOf(
        ImmediateStep(
            "Secure your accounts",
            "Change passwords on email and financial accounts. Enable 2FA where available.",
            Icons.Default.Shield,
        ),
        ImmediateStep(
            "Block the contact / domain",
            "Block the sender and do not click further links. Preserve original messages first.",
            Icons.Default.Block,
        ),
        ImmediateStep(
            "Preserve evidence",
            "Screenshot chats, emails, and transaction IDs. Upload them to your Evidence Vault.",
            Icons.Default.Save,
        ),
    )

    Scaffold(containerColor = Color.Transparent) { padding ->
        GradientBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
                item {
                    CyberShieldTopBar(showBack = showTopBack, onBack = onBack)
                }
                item {
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn() + slideInVertically { it / 10 },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BreathingGuardianOrb(size = 60.dp, icon = Icons.Default.Favorite, glowColor = Emerald)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "CRISIS SUPPORT ACTIVE",
                                style = MaterialTheme.typography.headlineLarge,
                                textAlign = TextAlign.Center,
                                color = Emerald,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "You are not alone. Follow these guided steps to regain control quietly and safely.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SoftGray,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "IMMEDIATE ACTIONS",
                        fontFamily = JetBrainsMono,
                        style = MaterialTheme.typography.labelMedium,
                        color = Emerald,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                itemsIndexed(steps) { index, step ->
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn() + slideInVertically { it / 8 },
                    ) {
                        PremiumCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            contentPadding = 16.dp,
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(ObsidianSurfaceContainerLowest)
                                        .border(1.dp, Emerald.copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${index + 1}",
                                        fontFamily = JetBrainsMono,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Emerald,
                                    )
                                }
                                Spacer(modifier = Modifier.size(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        step.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(step.description, style = MaterialTheme.typography.bodySmall, color = SoftGray)
                                }
                                Icon(step.icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "EMERGENCY SUPPORT",
                        fontFamily = JetBrainsMono,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PrimaryActionButton(
                        text = "Women's Safety Helpline",
                        icon = Icons.Default.PhoneInTalk,
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:181")))
                        },
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SecondaryActionButton(
                        text = "Call Local Authorities",
                        icon = Icons.Default.LocalHospital,
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
                        },
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "OFFICIAL CHANNELS",
                        fontFamily = JetBrainsMono,
                        style = MaterialTheme.typography.labelMedium,
                        color = Emerald,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                itemsIndexed(StaticContent.helplines) { _, h ->
                    PremiumCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        contentPadding = 16.dp,
                        onClick = {
                            val digits = h.number.filter { it.isDigit() }
                            if (digits.length >= 3 && !h.number.contains('.')) {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")))
                            } else if (h.number.contains('.')) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://${h.number}")))
                            }
                        },
                    ) {
                        Text(h.name, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(
                            h.number,
                            fontFamily = JetBrainsMono,
                            style = MaterialTheme.typography.titleMedium,
                            color = Emerald,
                        )
                        Text(h.useFor, style = MaterialTheme.typography.bodySmall, color = SoftGray)
                    }
                }
            }
        }
    }
}
