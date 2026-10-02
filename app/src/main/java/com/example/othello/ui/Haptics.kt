package com.example.othello.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.othello.config.AppConfig

/** Thin wrapper so every tap in the UI goes through one place.
 *  Honours [AppConfig.Haptics.ENABLED] — flip it off for a silent build. */
class Haptics internal constructor(private val raw: HapticFeedback) {
    fun tap() {
        if (AppConfig.Haptics.ENABLED)
            raw.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    fun confirm() {
        if (AppConfig.Haptics.ENABLED)
            raw.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val fb = LocalHapticFeedback.current
    return Haptics(fb)
}
