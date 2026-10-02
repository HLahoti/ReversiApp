package com.example.othello.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.othello.config.AppConfig

/** Semi-transparent "glass" surface used by cards, buttons and panels. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 24,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    val p = AppConfig.Palette
    val shape = RoundedCornerShape(cornerRadius.dp)
    CompositionLocalProvider(LocalContentColor provides p.OnDark) {
        Box(
            modifier
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        0.0f to p.GlassTop,
                        1.0f to p.GlassBottom,
                    )
                )
                .border(BorderStroke(1.dp, p.GlassBorder), shape)
                .padding(padding)
        ) { content() }
    }
}
