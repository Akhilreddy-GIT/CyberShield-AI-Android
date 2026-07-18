package com.cybershield.ai.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Obsidian Emerald is strictly dark-mode (DESIGN.md: "The palette is
 * strictly dark-mode... to provide a low-strain, premium environment").
 * There is deliberately no light color scheme and no isSystemInDarkTheme()
 * branch — a security app with a "Quiet Security" identity should look
 * the same regardless of OS theme setting, the same way Stripe Dashboard
 * or Linear's dark surfaces don't flip to light mode.
 */
private val ObsidianEmeraldColors = darkColorScheme(
    primary = Emerald,
    onPrimary = OnEmerald,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    inversePrimary = EmeraldInverse,

    secondary = Brass,
    onSecondary = OnBrass,
    secondaryContainer = BrassContainer,
    onSecondaryContainer = OnBrassContainer,

    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,

    background = ObsidianBackground,
    onBackground = OnSurface,

    surface = ObsidianSurfaceCard,
    onSurface = OnSurface,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceTint = Emerald,
    surfaceDim = ObsidianSurfaceDim,
    surfaceBright = ObsidianSurfaceBright,
    surfaceContainerLowest = ObsidianSurfaceContainerLowest,
    surfaceContainerLow = ObsidianSurfaceContainerLow,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceContainerHigh,
    surfaceContainerHighest = ObsidianSurfaceContainerHighest,

    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,

    outline = Outline,
    outlineVariant = OutlineVariant,

    error = ErrorRed,
    onError = OnErrorRed,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
)

/**
 * "Soft-Technical" shape language per DESIGN.md: 0.5rem (8dp) base radius
 * for structural cards — balances Bebas Neue's sharpness with approachable
 * roundedness. Small interactive elements (chips, badges) use full pill
 * shapes and are handled ad hoc at the composable level rather than via
 * MaterialTheme.shapes, since M3's Shapes system is for containers, not
 * pills.
 */
val CyberShieldShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun CyberShieldTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ObsidianEmeraldColors,
        typography = CyberShieldTypography,
        shapes = CyberShieldShapes,
        content = content,
    )
}
