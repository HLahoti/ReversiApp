package com.example.reversi.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.example.reversi.config.AppConfig

/** Thin alias so UI code reads `Felt.Highlight` instead of the full path. */
val Felt = AppConfig.Palette

private val DarkScheme = darkColorScheme(
    primary = Felt.Highlight,
    onPrimary = Felt.Deep,
    secondary = Felt.Soft,
    background = Felt.Deep,
    surface = Felt.Mid,
    onBackground = Felt.OnDark,
    onSurface = Felt.OnDark,
)

@Composable
fun ReversiTheme(content: @Composable () -> Unit) {
    // Palette is intentionally dark-only; swap in AppConfig to change.
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
