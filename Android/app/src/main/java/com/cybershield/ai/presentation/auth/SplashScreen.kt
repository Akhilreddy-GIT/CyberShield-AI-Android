package com.cybershield.ai.presentation.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cybershield.ai.presentation.theme.BebasNeue
import com.cybershield.ai.presentation.theme.CsAnim
import com.cybershield.ai.presentation.theme.Emerald
import com.cybershield.ai.presentation.theme.JetBrainsMono
import com.cybershield.ai.presentation.theme.SoftGray
import com.cybershield.ai.presentation.theme.SplashBackground
import com.cybershield.ai.presentation.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Branded splash screen matching Stitch collage #10.
 *
 * Behavior per user decision: visible only until initialization completes,
 * with a minimum display of ~700ms to avoid a visual flash.
 *
 * @param authTokenFlow Flow emitting the persisted JWT (null = not logged in).
 * @param onNavigate Called once with `true` if a token exists, `false` otherwise.
 */
@Composable
fun SplashScreen(
    authTokenFlow: Flow<String?>,
    onNavigate: (hasToken: Boolean) -> Unit,
) {
    val scale = remember { Animatable(0.7f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Animate shield icon entrance
        scale.animateTo(1f, tween(CsAnim.SLOW, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(CsAnim.STANDARD))
    }
    LaunchedEffect(Unit) {
        val startMs = System.currentTimeMillis()
        // Check auth state from DataStore
        val token = authTokenFlow.first()
        // Enforce minimum display time to avoid flash
        val elapsed = System.currentTimeMillis() - startMs
        val remaining = CsAnim.SPLASH_MIN_MS - elapsed
        if (remaining > 0) delay(remaining)
        onNavigate(!token.isNullOrBlank())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.alpha(alpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = "CyberShield",
                tint = Emerald,
                modifier = Modifier
                    .size(72.dp)
                    .scale(scale.value),
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "CYBERSHIELD",
                fontFamily = BebasNeue,
                fontSize = 36.sp,
                letterSpacing = 4.sp,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "ADVANCED THREAT INTELLIGENCE",
                fontFamily = JetBrainsMono,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.sp,
                color = SoftGray,
            )
        }
    }
}
