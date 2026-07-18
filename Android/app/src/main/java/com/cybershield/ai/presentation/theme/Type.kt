package com.cybershield.ai.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cybershield.ai.R

/**
 * Obsidian Emerald typography. Three families per DESIGN.md:
 * - Bebas Neue: display/headline — "towering, architectural" with tight
 *   leading, used for the massive-scale editorial contrast the system
 *   calls for.
 * - Source Sans 3: body copy — humanist counterpoint, slightly increased
 *   letter-spacing for an "airy" feel against the dark background.
 * - JetBrains Mono: technical labels — scan results, metadata, version
 *   numbers, bento-card top-right labels.
 *
 * Fonts are bundled under res/font (OFL-licensed) rather than fetched via
 * the Google Fonts downloadable-font provider, since this is a security
 * app that shouldn't have its typography depend on Play Services network
 * availability at runtime.
 */

val BebasNeue = FontFamily(
    Font(R.font.bebas_neue_regular, FontWeight.Normal),
)

val SourceSans3 = FontFamily(
    Font(R.font.source_sans_3_variable, FontWeight.Light),
    Font(R.font.source_sans_3_variable, FontWeight.Normal),
    Font(R.font.source_sans_3_variable, FontWeight.Medium),
    Font(R.font.source_sans_3_variable, FontWeight.SemiBold),
    Font(R.font.source_sans_3_variable, FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_variable, FontWeight.Medium),
    Font(R.font.jetbrains_mono_variable, FontWeight.SemiBold),
)

/** Use for the top-right label on bento cards, metadata, timestamps, scan
 * output — anything that should read as "technical instrumentation". */
val LabelCapsStyle = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 1.2.sp, // ~0.1em at 12sp
    color = TextMuted,
)

val CyberShieldTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = BebasNeue,
        fontWeight = FontWeight.Normal,
        fontSize = 64.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1.28).sp, // -0.02em at 64sp
        color = TextPrimary,
    ),
    displayMedium = TextStyle(
        fontFamily = BebasNeue,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp,
        color = TextPrimary,
    ),
    headlineLarge = TextStyle(
        fontFamily = BebasNeue,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp,
        color = TextPrimary,
    ),
    headlineMedium = TextStyle(
        fontFamily = BebasNeue,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 32.sp,
        color = TextPrimary,
    ),
    headlineSmall = TextStyle(
        fontFamily = BebasNeue,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 28.sp,
        color = TextPrimary,
    ),
    titleLarge = TextStyle(
        fontFamily = SourceSans3,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        color = TextPrimary,
    ),
    titleMedium = TextStyle(
        fontFamily = SourceSans3,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = TextPrimary,
    ),
    titleSmall = TextStyle(
        fontFamily = SourceSans3,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextPrimary,
    ),
    bodyLarge = TextStyle(
        fontFamily = SourceSans3,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.18.sp, // ~0.01em
        color = OnSurface,
    ),
    bodyMedium = TextStyle(
        fontFamily = SourceSans3,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.16.sp,
        color = OnSurface,
    ),
    bodySmall = TextStyle(
        fontFamily = SourceSans3,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 19.sp,
        color = OnSurface,
    ),
    labelLarge = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.8.sp,
        color = TextMuted,
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.2.sp,
        color = TextMuted,
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.0.sp,
        color = TextMuted,
    ),
)
