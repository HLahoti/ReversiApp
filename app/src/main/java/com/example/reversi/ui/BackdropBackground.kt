package com.example.reversi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.example.reversi.config.AppConfig

/** Full-screen gradient backdrop used behind every screen. */
@Composable
fun BackdropBackground(content: @Composable () -> Unit) {
    val p = AppConfig.Palette
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    0.0f to p.BackdropInner,
                    0.55f to p.BackdropMid,
                    1.0f to p.BackdropOuter,
                    center = p.BackdropCenter,
                    radius = p.BACKDROP_RADIUS,
                )
            )
    ) {
        content()
    }
}
