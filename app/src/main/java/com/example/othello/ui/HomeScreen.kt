package com.example.othello.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.othello.config.AppConfig

@Composable
fun HomeScreen(
    onStartTwoPlayer: () -> Unit,
    onStartVsBot: (humanColor: HumanColorChoice) -> Unit,
) {
    val haptics = rememberHaptics()
    BackdropBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Othello",
                color = Felt.OnDark,
                fontSize = 56.sp,
                fontWeight = FontWeight.Light,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "A neural engine lives inside.",
                color = Felt.OnDarkDim,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(44.dp))

            GlassPanel(Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Pass & play", color = Felt.OnDark, fontSize = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = "Start 2-player game",
                        onClick = { haptics.confirm(); onStartTwoPlayer() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            GlassPanel(Modifier.fillMaxWidth()) {
                var choice by remember { mutableStateOf(HumanColorChoice.RANDOM) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Play against the bot", color = Felt.OnDark, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ColorChip("Black", choice == HumanColorChoice.BLACK) {
                            haptics.tap(); choice = HumanColorChoice.BLACK
                        }
                        ColorChip("White", choice == HumanColorChoice.WHITE) {
                            haptics.tap(); choice = HumanColorChoice.WHITE
                        }
                        ColorChip("Random", choice == HumanColorChoice.RANDOM) {
                            haptics.tap(); choice = HumanColorChoice.RANDOM
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = "Start",
                        onClick = { haptics.confirm(); onStartVsBot(choice) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

enum class HumanColorChoice { BLACK, WHITE, RANDOM }

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Felt.Highlight.copy(alpha = 0.9f),
            contentColor = Felt.Deep,
        ),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ColorChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Felt.Highlight.copy(alpha = 0.9f) else AppConfig.Palette.Glass
    val fg = if (selected) Felt.Deep else Felt.OnDark
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
    ) { Text(label, fontSize = 14.sp) }
}
