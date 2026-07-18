package com.cybershield.ai.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cybershield.ai.presentation.theme.CardWhite
import com.cybershield.ai.presentation.theme.Emerald
import com.cybershield.ai.presentation.theme.HairlineBorder
import com.cybershield.ai.presentation.theme.ObsidianSurfaceContainer
import com.cybershield.ai.presentation.theme.ObsidianSurfaceContainerHigh

/**
 * Dark-theme loading placeholder. Rather than a light "shimmer sweep"
 * (which reads as a bug against a near-black background), this uses a
 * moving emerald scan-line — matching the "ANALYZING TRAFFIC..." progress
 * treatment in the Guardian Feed reference screen.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    cornerRadius: Dp = 8.dp,
) {
    val transition = rememberInfiniteTransition(label = "scanline")
    val shift by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scanShift",
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            ObsidianSurfaceContainer,
            ObsidianSurfaceContainerHigh,
            Emerald.copy(alpha = 0.35f),
            ObsidianSurfaceContainerHigh,
            ObsidianSurfaceContainer,
        ),
        start = Offset(shift - 300f, 0f),
        end = Offset(shift, 120f),
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(ObsidianSurfaceContainer)
            .background(brush),
    )
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardWhite)
            .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            ShimmerBox(height = 12.dp, modifier = Modifier.fillMaxWidth(0.4f))
            Spacer(modifier = Modifier.height(14.dp))
            ShimmerBox(height = 18.dp)
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerBox(height = 12.dp, modifier = Modifier.fillMaxWidth(0.7f))
            Spacer(modifier = Modifier.height(16.dp))
            ShimmerBox(height = 72.dp, cornerRadius = 10.dp)
        }
    }
}
