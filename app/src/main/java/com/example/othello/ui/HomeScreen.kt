package com.example.othello.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.othello.R
import com.example.othello.config.AppConfig
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onStartTwoPlayer: () -> Unit,
    onStartVsBot: (humanColor: HumanColorChoice) -> Unit,
) {
    val haptics = rememberHaptics()
    var choice by remember { mutableStateOf(HumanColorChoice.RANDOM) }
    BackdropBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpinningPiece(target = choice, size = 160.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Othello",
                color = Felt.OnDark,
                fontSize = 56.sp,
                fontWeight = FontWeight.Light,
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

@Composable
private fun SpinningPiece(target: HumanColorChoice, size: Dp) {
    val anim = AppConfig.Animation
    val liveTarget by rememberUpdatedState(target)
    var blackUp by remember { mutableStateOf(true) }
    var frame by remember { mutableIntStateOf(0) }
    var flipping by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val needFlip = when (liveTarget) {
                HumanColorChoice.BLACK -> !blackUp
                HumanColorChoice.WHITE -> blackUp
                HumanColorChoice.RANDOM -> true
            }
            if (needFlip) {
                flipping = true
                // Skip the opening frame (= fully-settled source colour): the
                // previous flip ended on that exact pose (last frame of the
                // b2w set == first frame of the w2b set, and vice versa), so
                // playing it again reads as the piece pausing mid-spin.
                for (i in 2..anim.FLIP_FRAME_COUNT) {
                    frame = i
                    delay(anim.FLIP_FRAME_MS)
                }
                blackUp = !blackUp
                flipping = false
                frame = 0
            } else {
                delay(50)
            }
        }
    }

    val ctx = LocalContext.current
    if (flipping && frame > 0) {
        val prefix = if (blackUp) "flip_b2w" else "flip_w2b"
        val resId = remember(prefix, frame) {
            val src = sourceFrameFor(frame, anim.FLIP_FRAME_COUNT, anim.FLIP_SOURCE_FRAME_COUNT)
            ctx.resources.getIdentifier(
                "${prefix}_${"%03d".format(src)}", "drawable", ctx.packageName,
            )
        }
        if (resId != 0) {
            Image(
                painter = painterResource(resId),
                contentDescription = null,
                modifier = Modifier.size(size),
            )
        }
    } else {
        Image(
            painter = painterResource(
                if (blackUp) R.drawable.piece_black else R.drawable.piece_white,
            ),
            contentDescription = null,
            modifier = Modifier.size(size),
        )
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
