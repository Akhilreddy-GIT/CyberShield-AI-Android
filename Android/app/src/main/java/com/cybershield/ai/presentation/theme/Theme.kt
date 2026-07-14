package com.cybershield.ai.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF0F3460)
val Deep = Color(0xFF1A1A2E)
val Accent = Color(0xFFE94560)
val SoftBlue = Color(0xFFE8EAF6)
val Teal = Color(0xFF16C79A)
val Amber = Color(0xFFF0A500)
val CriticalRed = Color(0xFFD32F2F)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    secondary = Accent,
    onSecondary = Color.White,
    tertiary = Teal,
    background = Color(0xFFF7F8FC),
    onBackground = Deep,
    surface = Color.White,
    onSurface = Deep,
    error = CriticalRed,
)

private val DarkColors = darkColorScheme(
    primary = SoftBlue,
    onPrimary = Deep,
    secondary = Accent,
    onSecondary = Color.White,
    tertiary = Teal,
    background = Deep,
    onBackground = Color.White,
    surface = Color(0xFF16213E),
    onSurface = Color.White,
    error = Accent,
)

@Composable
fun CyberShieldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
