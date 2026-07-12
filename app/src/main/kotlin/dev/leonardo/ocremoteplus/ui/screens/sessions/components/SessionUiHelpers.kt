package dev.leonardo.ocremoteplus.ui.screens.sessions.components

import androidx.compose.runtime.Composable
import dev.leonardo.ocremoteplus.ui.theme.LocalAmoledMode

/**
 * Check if the current theme is AMOLED dark mode.
 */
@Composable
internal fun isAmoledTheme(): Boolean = LocalAmoledMode.current
