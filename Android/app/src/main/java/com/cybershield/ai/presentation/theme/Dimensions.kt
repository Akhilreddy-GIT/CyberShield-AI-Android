package com.cybershield.ai.presentation.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centralized dimension tokens for the Obsidian Emerald design system.
 *
 * New screens should reference these tokens rather than repeating inline dp
 * values. Existing screens can adopt them incrementally — the values are
 * intentionally chosen to match the numbers already used in most composables
 * so that migrating is a find-and-replace, not a visual change.
 */

// ── Spacing ─────────────────────────────────────────────────────────────
object CsSpacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp

    /** Standard horizontal padding for screen content columns. */
    val screenHorizontal: Dp = 20.dp
    /** Standard card inner padding. */
    val cardPadding: Dp = 20.dp
    /** Compact card inner padding. */
    val cardPaddingCompact: Dp = 16.dp
}

// ── Corner Radii ────────────────────────────────────────────────────────
object CsRadius {
    val xs: Dp = 6.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    /** Fully rounded (pill shape) — pass to RoundedCornerShape(percent = 50). */
    const val pill: Int = 50
}

// ── Icon Sizes ──────────────────────────────────────────────────────────
object CsIconSize {
    val xs: Dp = 14.dp
    val sm: Dp = 16.dp
    val md: Dp = 20.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 40.dp
}

// ── Animation Timing (milliseconds) ────────────────────────────────────
object CsAnim {
    const val INSTANT = 100
    const val FAST = 200
    const val MEDIUM = 300
    const val STANDARD = 400
    const val SLOW = 600
    /** Splash minimum display time to avoid visual flash. */
    const val SPLASH_MIN_MS = 700L
    /** Score ring / progress ring fill animation. */
    const val SCORE_RING = 1100
    /** Breathing / ambient glow cycle. */
    const val BREATHING = 2200
}

// ── Elevation ───────────────────────────────────────────────────────────
object CsElevation {
    /** Obsidian Emerald uses hairline borders, not drop shadows. Cards use 0. */
    val card: Dp = 0.dp
    val raised: Dp = 2.dp
}

// ── Border ──────────────────────────────────────────────────────────────
object CsBorder {
    val hairline: Dp = 1.dp
    val focused: Dp = 1.5.dp
}
