package com.cybershield.ai.presentation.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * "Obsidian Emerald" — CyberShield AI's design system.
 * Source: Stitch redesign / obsidian_emerald/DESIGN.md.
 * "Quiet Security" philosophy: stoic, premium, dark-mode-only. Purple and
 * blue are intentionally excluded from the palette everywhere in this file.
 */

// ---- Core surfaces (Deep Forest) ----
val ObsidianBackground = Color(0xFF0F1511)
val ObsidianSurfaceDim = Color(0xFF0F1511)
val ObsidianSurfaceBright = Color(0xFF353A37)
val ObsidianSurfaceContainerLowest = Color(0xFF0A0F0C)
val ObsidianSurfaceContainerLow = Color(0xFF181D19)
val ObsidianSurfaceContainer = Color(0xFF1C211D)
val ObsidianSurfaceContainerHigh = Color(0xFF262B28)
val ObsidianSurfaceContainerHighest = Color(0xFF313632)
val ObsidianSurfaceCard = Color(0xFF121815)
val ObsidianSurfaceVariant = Color(0xFF313632)

// ---- On-surface text ----
val OnSurface = Color(0xFFDFE4DE)
val OnSurfaceVariant = Color(0xFFBBCABF)
val InverseSurface = Color(0xFFDFE4DE)
val InverseOnSurface = Color(0xFF2C322E)
val TextPrimary = Color(0xFFF4F6F1)
val TextMuted = Color(0xFFC8D1CA)

// ---- Outline / hairline ----
val Outline = Color(0xFF86948A)
val OutlineVariant = Color(0xFF3C4A42)

// ---- Primary: Emerald ----
val Emerald = Color(0xFF4EDEA3)
val OnEmerald = Color(0xFF003824)
val EmeraldContainer = Color(0xFF10B981)
val OnEmeraldContainer = Color(0xFF00422B)
val EmeraldInverse = Color(0xFF006C49)
val EmeraldPressed = Color(0xFF0D8F68)
val EmeraldFixed = Color(0xFF6FFBBE)
val EmeraldFixedDim = Color(0xFF4EDEA3)

// ---- Secondary: Premium Brass (moment-of-delight only) ----
val Brass = Color(0xFFE8C17A)
val OnBrass = Color(0xFF412D00)
val BrassContainer = Color(0xFF5C4205)
val OnBrassContainer = Color(0xFFD5B06B)
val BrassFixed = Color(0xFFFFDEA5)

// ---- Tertiary ----
val Tertiary = Color(0xFFC2C8C3)
val OnTertiary = Color(0xFF2C322E)
val TertiaryContainer = Color(0xFF9EA49F)
val OnTertiaryContainer = Color(0xFF343A37)

// ---- Error ----
val ErrorRed = Color(0xFFFFB4AB)
val OnErrorRed = Color(0xFF690005)
val ErrorContainer = Color(0xFF93000A)
val OnErrorContainer = Color(0xFFFFDAD6)

// ---- Risk system (status-indicator contexts only, per DESIGN.md) ----
val RiskLow = Color(0xFF6EE7B7)
val RiskMedium = Color(0xFFF5B942)
val RiskHigh = Color(0xFFF2784B)
val RiskCritical = Color(0xFFE63946)

// ---- Legacy aliases so existing screen code keeps compiling while each
// screen is migrated batch-by-batch to the semantic names above. These
// intentionally point at Obsidian Emerald values now, not the old
// cream/plum palette — this is what makes the reskin apply everywhere
// at once instead of requiring every call site to change in lockstep. ----
val Cream = ObsidianBackground
val CreamDeep = ObsidianSurfaceContainerLowest
val Blush = ObsidianSurfaceContainer
val BlushSoft = ObsidianSurfaceContainerLow

val Plum = Emerald
val PlumDeep = EmeraldInverse
val PlumMuted = OnSurfaceVariant
val PlumLight = EmeraldFixed
val PlumContainer = ObsidianSurfaceContainerHigh

val Charcoal = TextPrimary
val SoftGray = TextMuted
val Mist = ObsidianSurfaceContainer
val CardWhite = ObsidianSurfaceCard

val AccentPink = EmeraldContainer
val ActivePill = ObsidianSurfaceContainerHighest

val SuccessGreen = RiskLow
val SuccessContainer = Color(0xFF0F2A20)
val Amber = RiskMedium
val AmberContainer = Color(0xFF332609)
val CriticalRed = RiskCritical
val CriticalContainer = Color(0xFF3A1416)

val Navy = Plum
val Deep = Charcoal
val Accent = PlumMuted
val SoftBlue = PlumContainer
val Teal = SuccessGreen

/** Background wash: near-black with a faint emerald tint at the top,
 * matching the "Ambient Glow" elevation model instead of a flat fill. */
val ScreenGradient: Brush
    get() = Brush.verticalGradient(
        colors = listOf(
            ObsidianSurfaceContainerLowest,
            ObsidianBackground,
            ObsidianBackground,
        ),
    )

/** Hero / bento card gradient — subtle, dark, with a hint of emerald glow
 * rather than the old light cream card. */
val HeroCardGradient: Brush
    get() = Brush.verticalGradient(
        colors = listOf(
            ObsidianSurfaceContainer,
            ObsidianSurfaceCard,
            ObsidianSurfaceContainerLowest,
        ),
    )

val GuardianCardGradient: Brush
    get() = Brush.linearGradient(
        colors = listOf(
            ObsidianSurfaceContainer,
            ObsidianSurfaceCard,
        ),
    )

/** Emerald hairline border used on every card per DESIGN.md ("glass-edge"
 * effect): Primary Emerald at 8-10% opacity, 1px. */
val HairlineBorder = Emerald.copy(alpha = 0.09f)
val HairlineBorderFocused = Emerald.copy(alpha = 0.45f)

/** Brass divider — reserved for exactly one "moment of delight" per major
 * journey (e.g. Settings → Premium section), per DESIGN.md. */
val BrassDivider = Brass.copy(alpha = 0.6f)
val EmeraldDivider = Emerald.copy(alpha = 0.10f)

// ---- Auth / Splash ----
/** Splash screen background — the deepest possible black with a green tint. */
val SplashBackground = Color(0xFF060A08)
/** Text field container inside auth screens (darker than CardWhite). */
val AuthFieldBackground = ObsidianSurfaceContainerLow

// ---- Profile / Privacy ----
/** Default avatar ring color when no image is loaded. */
val AvatarRing = Emerald.copy(alpha = 0.5f)
/** Privacy Health score gradient end (light emerald). */
val PrivacyGreen = Color(0xFF34D399)
/** Danger-zone action text and border (deactivate account, etc.). */
val DangerZoneRed = CriticalRed.copy(alpha = 0.7f)
