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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.cybershield.ai.presentation.theme.AmberContainer
import com.cybershield.ai.presentation.theme.CardWhite
import com.cybershield.ai.presentation.theme.Charcoal
import com.cybershield.ai.presentation.theme.CriticalRed
import com.cybershield.ai.presentation.theme.Mist
import com.cybershield.ai.presentation.theme.OnEmerald
import com.cybershield.ai.presentation.theme.Plum
import com.cybershield.ai.presentation.theme.PlumContainer
import com.cybershield.ai.presentation.theme.SoftGray
import com.cybershield.ai.presentation.theme.SuccessGreen

@Composable
fun ThreatAnalysisCard(
    title: String,
    riskScore: Int,
    riskLevel: String,
    certainty: Float = 0.9f,
    target: String? = null,
    modifier: Modifier = Modifier,
) {
    val tone = when {
        riskLevel.contains("Critical", true) || riskScore >= 80 -> StatusTone.Danger
        riskLevel.contains("High", true) || riskScore >= 60 -> StatusTone.Warning
        riskLevel.contains("Medium", true) || riskScore >= 40 -> StatusTone.Warning
        else -> StatusTone.Success
    }
    PremiumCard(modifier = modifier, accentBar = CriticalRed, contentPadding = 18.dp) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("THREAT ANALYSIS", style = MaterialTheme.typography.labelMedium, color = SoftGray)
                Spacer(modifier = Modifier.height(4.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Risk Score", style = MaterialTheme.typography.labelMedium, color = SoftGray)
                Text(
                    "$riskScore / 100",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Charcoal,
                )
                Spacer(modifier = Modifier.height(4.dp))
                StatusPill(riskLevel.replace('_', ' '), tone = tone)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        AnimatedProgressBar(progress = certainty)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "${(certainty * 100).toInt()}% Certainty",
            style = MaterialTheme.typography.labelMedium,
            color = SoftGray,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
        )
        if (!target.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text("Target", style = MaterialTheme.typography.labelMedium, color = SoftGray)
            Text(target, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun RecoveryActionPlanCard(
    actions: List<RecoveryAction>,
    onToggle: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    PremiumCard(modifier = modifier, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = Plum)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Recovery Action Plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        actions.forEachIndexed { index, action ->
            val bg = if (action.recommended) Mist else Color.Transparent
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .padding(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                IconButton(
                    onClick = { onToggle(index) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (action.done) Plum else Color.Transparent)
                            .then(
                                if (!action.done) Modifier.background(Color.Transparent)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            color = if (action.done) Plum else Color.Transparent,
                            shape = RoundedCornerShape(6.dp),
                            border = if (!action.done) {
                                androidx.compose.foundation.BorderStroke(1.5.dp, SoftGray.copy(alpha = 0.5f))
                            } else null,
                            modifier = Modifier.size(22.dp),
                        ) {
                            if (action.done) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("✓", color = OnEmerald, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (action.done) StruckText(action.title)
                    else Text(action.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        when {
                            action.done -> "Completed"
                            action.recommended -> "Recommended next step"
                            action.optional -> "Optional"
                            else -> action.subtitle.orEmpty()
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = SoftGray,
                    )
                }
            }
            if (index < actions.lastIndex) Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

data class RecoveryAction(
    val title: String,
    val done: Boolean = false,
    val recommended: Boolean = false,
    val optional: Boolean = false,
    val subtitle: String? = null,
)

@Composable
fun ExpandableReasoningCard(
    sources: List<String>,
    modifier: Modifier = Modifier,
) {
    if (sources.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    PremiumCard(modifier = modifier, contentPadding = 14.dp, elevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Sources & reasoning", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = Plum,
                )
            }
        }
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                sources.forEach { src ->
                    Text("• $src", style = MaterialTheme.typography.bodySmall, color = SoftGray)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun EvidenceTimelineCard(
    title: String,
    description: String,
    filename: String?,
    location: String? = null,
    extractedText: String? = null,
    isImage: Boolean = false,
    onDelete: (() -> Unit)? = null,
    imageContent: (@Composable () -> Unit)? = null,
    // New optional params — null by default so all existing call-sites compile unchanged.
    imageUrl: String? = null,
    onView: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    PremiumCard(modifier = modifier, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isImage) AmberContainer else PlumContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isImage) Icons.Outlined.WarningAmber else Icons.Outlined.MailOutline,
                    contentDescription = null,
                    tint = Charcoal,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = SoftGray)
            }
        }
        // Thumbnail: show a Coil image preview when an imageUrl is available for an image file.
        // Falls back to the caller-supplied imageContent slot, then to the OCR text excerpt.
        if (imageContent != null) {
            Spacer(modifier = Modifier.height(12.dp))
            imageContent()
        } else if (isImage && !imageUrl.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = "Evidence thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Mist),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Plum,
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                    is AsyncImagePainter.State.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Mist),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.WarningAmber,
                                contentDescription = "Failed to load image",
                                tint = SoftGray,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    else -> SubcomposeAsyncImageContent()
                }
            }
        } else if (!extractedText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(color = Mist, shape = RoundedCornerShape(18.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null, tint = SoftGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "“${extractedText.take(180)}${if (extractedText.length > 180) "…" else ""}”",
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                        color = SoftGray,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!filename.isNullOrBlank()) {
                MetadataChip(filename, Icons.Outlined.Description)
            }
            if (!location.isNullOrBlank()) {
                MetadataChip(location)
            }
        }
        if (onDelete != null || onView != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (onView != null) {
                    androidx.compose.material3.TextButton(onClick = onView) {
                        Text("View", color = Plum)
                    }
                }
                if (onDelete != null) {
                    androidx.compose.material3.TextButton(onClick = onDelete) {
                        Text("Delete", color = CriticalRed)
                    }
                }
            }
        }
    }
}
