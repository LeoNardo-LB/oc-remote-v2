package dev.minios.ocremote.ui.theme

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut

object AppMotion {
    const val SHORT = 150
    const val MEDIUM = 300
    const val LONG = 500

    val StandardEasing = EaseInOut
    val EmphasizedEasing = EaseOut
    val ExitEasing = EaseIn
}
