package com.cybershield.ai.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cybershield.ai.presentation.theme.Amber
import com.cybershield.ai.presentation.theme.AmberContainer
import com.cybershield.ai.presentation.theme.CardWhite
import com.cybershield.ai.presentation.theme.CriticalContainer
import com.cybershield.ai.presentation.theme.CriticalRed
import com.cybershield.ai.presentation.theme.Emerald
import com.cybershield.ai.presentation.theme.EmeraldPressed
import com.cybershield.ai.presentation.theme.HairlineBorder
import com.cybershield.ai.presentation.theme.JetBrainsMono
import com.cybershield.ai.presentation.theme.Mist
import com.cybershield.ai.presentation.theme.ObsidianBackground
import com.cybershield.ai.presentation.theme.ObsidianSurfaceContainerLowest
import com.cybershield.ai.presentation.theme.OnEmerald
import com.cybershield.ai.presentation.theme.Plum
import com.cybershield.ai.presentation.theme.PlumContainer
import com.cybershield.ai.presentation.theme.PlumMuted
import com.cybershield.ai.presentation.theme.SoftGray
import com.cybershield.ai.presentation.theme.SuccessContainer
import com.cybershield.ai.presentation.theme.SuccessGreen
import com.cybershield.ai.presentation.theme.TextMuted
import com.cybershield.ai.presentation.theme.TextPrimary

/**
 * Obsidian Emerald card: hairline emerald border (8-10% opacity per
 * DESIGN.md "glass-edge" effect) instead of a drop shadow, dark card
 * surface, and an optional soft ambient glow in the top-left corner for
 * cards representing an active/live state (used sparingly — most cards
 * should NOT glow, only ones showing something actively happening).
 */
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    elevation: Dp = 0.dp, // retained for call-site compatibility; Obsidian Emerald uses hairlines, not elevation
    contentPadding: Dp = 20.dp,
    background: Color = CardWhite,
    accentBar: Color? = null,
    ambientGlow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val borderAlpha by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.22f else 0.09f,
        label = "cardBorder",
    )

    Surface(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else Modifier
            )
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (ambientGlow) {
                    Modifier.drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Emerald.copy(alpha = 0.15f), Color.Transparent),
                                radius = 140.dp.toPx(),
                                center = Offset(0f, 0f),
                            ),
                            radius = 140.dp.toPx(),
                            center = Offset(0f, 0f),
                        )
                    }
                } else Modifier
            )
            .border(1.dp, Emerald.copy(alpha = borderAlpha), RoundedCornerShape(12.dp))
            .then(
                if (accentBar != null) {
                    Modifier.drawBehind {
                        drawRect(
                            color = accentBar,
                            topLeft = Offset.Zero,
                            size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height),
                        )
                    }
                } else Modifier
            ),
        color = background,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
                .padding(start = if (accentBar != null) 6.dp else 0.dp),
            content = content,
        )
    }
}

@Composable
fun AccentCard(
    modifier: Modifier = Modifier,
    accentColor: Color = Plum,
    onClick: (() -> Unit)? = null,
    ambientGlow: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    PremiumCard(
        modifier = modifier,
        onClick = onClick,
        accentBar = accentColor,
        ambientGlow = ambientGlow,
        content = content,
    )
}

@Composable
fun CyberShieldTopBar(
    modifier: Modifier = Modifier,
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null,
    onAlertClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianSurfaceContainerLowest)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBack && onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Emerald,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = Emerald,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "CYBERSHIELD AI",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = TextPrimary,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            trailing?.invoke(this)
            IconButton(onClick = { onAlertClick?.invoke() }) {
                Icon(
                    Icons.Outlined.ReportProblem,
                    contentDescription = "Alerts",
                    tint = PlumMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * "Breathing Glow" per DESIGN.md — used for critical/active states. Slow
 * pulse of a low-opacity risk color behind the shield icon.
 */
@Composable
fun BreathingGuardianOrb(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    icon: ImageVector = Icons.Default.Shield,
    glowColor: Color = Emerald,
) {
    val transition = rememberInfiniteTransition(label = "breathe")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    Box(modifier = modifier.size(size * 1.4f), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size * 1.25f)
                .clip(CircleShape)
                .background(glowColor.copy(alpha = glowAlpha)),
        )
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(ObsidianSurfaceContainerLowest)
                .border(1.dp, glowColor.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = glowColor, modifier = Modifier.size(size * 0.42f))
        }
    }
}

/** JetBrains Mono chip — used for metadata, timestamps, filenames. */
@Composable
fun MetadataChip(
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.border(1.dp, HairlineBorder, RoundedCornerShape(50)),
        color = Color.Transparent,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
            }
            Text(
                text,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                letterSpacing = 0.4.sp,
                color = TextMuted,
            )
        }
    }
}

/** Pill status indicator with a solid dot, per the reference screens'
 * risk-factor chips (e.g. "Location Anomaly", "Device Unknown"). */
@Composable
fun StatusPill(
    text: String,
    tone: StatusTone = StatusTone.Neutral,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = when (tone) {
        StatusTone.Success -> SuccessContainer to SuccessGreen
        StatusTone.Warning -> AmberContainer to Amber
        StatusTone.Danger -> CriticalContainer to CriticalRed
        StatusTone.Neutral -> Mist to SoftGray
        StatusTone.Info -> PlumContainer to Plum
    }
    Surface(
        modifier = modifier.border(1.dp, fg.copy(alpha = 0.35f), RoundedCornerShape(50)),
        color = bg.copy(alpha = 0.5f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(fg),
            )
            Text(
                text,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp,
                color = fg,
            )
        }
    }
}

enum class StatusTone { Success, Warning, Danger, Neutral, Info }

@Composable
fun AnimatedProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Mist,
    fillColor: Color = Emerald,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "progress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(fillColor),
        )
    }
}

/** Deep Forest background wash — replaces the old light cream gradient. */
@Composable
fun GradientBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(ObsidianSurfaceContainerLowest, ObsidianBackground, ObsidianBackground),
            ),
        ),
    ) {
        content()
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    centered: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.displayMedium,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            color = TextPrimary,
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = SoftGray,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            )
        }
    }
}

/** Primary button: solid Emerald fill, near-black text, presses to
 * EmeraldPressed — exactly per DESIGN.md's button spec. */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(
        when {
            !enabled -> Emerald.copy(alpha = 0.35f)
            pressed -> EmeraldPressed
            else -> Emerald
        },
        label = "btnBg",
    )
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        color = bg,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = OnEmerald, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(text, color = OnEmerald, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Secondary button: Emerald hairline border, no fill, per DESIGN.md. */
@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Emerald.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = Color.Transparent,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Emerald, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(text, color = Emerald, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CardWhite)
            .border(1.dp, HairlineBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = index * 140),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Emerald.copy(alpha = alpha)),
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text("Guardian is analyzing…", style = MaterialTheme.typography.bodySmall, color = SoftGray)
    }
}

/** Lightweight markdown: **bold**, *italic*, `code`, and bullets. */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
) {
    val annotated = remember(text) {
        buildAnnotatedString {
            val lines = text.lines()
            lines.forEachIndexed { lineIndex, line ->
                var i = 0
                val content = when {
                    line.startsWith("- ") || line.startsWith("• ") -> {
                        append("• ")
                        line.drop(2)
                    }
                    line.matches(Regex("^\\d+\\.\\s+.*")) -> {
                        val dot = line.indexOf('.')
                        append("${line.take(dot + 1)} ")
                        line.drop(dot + 1).trimStart()
                    }
                    else -> line
                }
                while (i < content.length) {
                    when {
                        content.startsWith("**", i) -> {
                            val end = content.indexOf("**", i + 2)
                            if (end != -1) {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary)) {
                                    append(content.substring(i + 2, end))
                                }
                                i = end + 2
                            } else {
                                append(content[i]); i++
                            }
                        }
                        content.startsWith("*", i) && !content.startsWith("**", i) -> {
                            val end = content.indexOf("*", i + 1)
                            if (end != -1) {
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append(content.substring(i + 1, end))
                                }
                                i = end + 1
                            } else {
                                append(content[i]); i++
                            }
                        }
                        content.startsWith("`", i) -> {
                            val end = content.indexOf("`", i + 1)
                            if (end != -1) {
                                withStyle(
                                    SpanStyle(
                                        background = Mist,
                                        color = Emerald,
                                        fontFamily = JetBrainsMono,
                                        fontSize = 13.sp,
                                    ),
                                ) {
                                    append(content.substring(i + 1, end))
                                }
                                i = end + 1
                            } else {
                                append(content[i]); i++
                            }
                        }
                        else -> {
                            append(content[i]); i++
                        }
                    }
                }
                if (lineIndex < lines.lastIndex) append("\n")
            }
        }
    }
    Text(annotated, modifier = modifier, color = color, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun StruckText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(
            textDecoration = TextDecoration.LineThrough,
            color = SoftGray,
        ),
    )
}

/**
 * Settings row: icon · title / subtitle · trailing chevron or content.
 * Used in Profile and Privacy screens matching the Stitch design list items.
 */
@Composable
fun SettingsListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Emerald,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = CardWhite,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, HairlineBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Mist),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
            }
            if (trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
            } else if (onClick != null) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent),
                )
            }
        }
    }
}

/**
 * Danger button: CriticalRed hairline border, red text. Used for destructive
 * actions like "Deactivate Account" in Stitch Profile design.
 */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, CriticalRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = CriticalContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = CriticalRed, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(text, color = CriticalRed, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Topic selection card for chat empty state, matching Stitch AI assistant
 * design: icon + title + subtitle in a tappable card.
 */
@Composable
fun TopicCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    PremiumCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = 16.dp,
    ) {
        Icon(icon, contentDescription = null, tint = Emerald, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

