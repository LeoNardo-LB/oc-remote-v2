package dev.minios.ocremote.ui.theme

/**
 * Semantic alpha tokens for consistent content emphasis across the app.
 *
 * Usage: `colorScheme.onSurface.copy(alpha = AlphaTokens.FAINT)`
 *
 * Token scale:
 *   FAINT  (0.35) — metadata, timestamps, placeholder, subtle dividers
 *   MUTED  (0.50) — secondary text, subtle icons, secondary borders
 *   MEDIUM (0.65) — tertiary content, standard borders, keyboard overlays
 *   NORMAL (0.70) — primary text variants, default emphasis
 *   HIGH   (0.75) — card selected borders, amoled card borders
 *   STRONG (0.80) — control borders (Switch/Radio/Checkbox), selected indicators
 *
 * AMOLED_CODE (0.92) — AMOLED mode code text max contrast
 */
object AlphaTokens {
    const val FAINT = 0.35f
    const val MUTED = 0.50f
    const val MEDIUM = 0.65f
    const val NORMAL = 0.70f
    const val HIGH = 0.75f
    const val STRONG = 0.80f

    const val AMOLED_CODE = 0.92f
}
