package com.example.othello.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.othello.R
import com.example.othello.bot.Difficulty
import com.example.othello.config.AppConfig
import com.example.othello.data.GameStore
import com.example.othello.data.SavedMode
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onStartTwoPlayer: (resume: Boolean) -> Unit,
    onStartVsBot: (humanColor: HumanColorChoice, difficulty: Difficulty, resume: Boolean) -> Unit,
) {
    val haptics = rememberHaptics()
    val ctx = LocalContext.current
    val store = remember(ctx) { GameStore(ctx.applicationContext) }
    var choice by remember { mutableStateOf(HumanColorChoice.RANDOM) }
    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }

    // Re-check save existence whenever this screen comes to the foreground
    // (back from a game, app resume) so Resume/New buttons stay in sync.
    var hasTwoPlayer by remember { mutableStateOf(store.has(SavedMode.TWO_PLAYER)) }
    var hasVsBot by remember { mutableStateOf(store.has(SavedMode.VS_BOT)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasTwoPlayer = store.has(SavedMode.TWO_PLAYER)
                hasVsBot = store.has(SavedMode.VS_BOT)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Two-step flow: null = mode picker; set = the picked mode's options panel.
    var step by remember { mutableStateOf<SavedMode?>(null) }
    // System back on the options step returns to the picker instead of
    // exiting the app.
    BackHandler(enabled = step != null) { step = null }

    // Which mode's "New game" has a confirm dialog pending.
    var confirmDiscard by remember { mutableStateOf<SavedMode?>(null) }

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

            when (step) {
                null -> ModePicker(
                    onPickTwoPlayer = { haptics.tap(); step = SavedMode.TWO_PLAYER },
                    onPickVsBot = { haptics.tap(); step = SavedMode.VS_BOT },
                )
                SavedMode.TWO_PLAYER -> OptionsPanel(
                    title = "Pass & play",
                    onBack = { haptics.tap(); step = null },
                ) {
                    if (hasTwoPlayer) {
                        PrimaryButton(
                            text = "Resume game",
                            onClick = { haptics.confirm(); onStartTwoPlayer(true) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton(
                            text = "New game",
                            onClick = { haptics.tap(); confirmDiscard = SavedMode.TWO_PLAYER },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        PrimaryButton(
                            text = "Start game",
                            onClick = { haptics.confirm(); onStartTwoPlayer(false) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                SavedMode.VS_BOT -> OptionsPanel(
                    title = "Play against the bot",
                    onBack = { haptics.tap(); step = null },
                ) {
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
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Difficulty.values().forEach { d ->
                            ColorChip(d.label, difficulty == d) {
                                haptics.tap(); difficulty = d
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    if (hasVsBot) {
                        PrimaryButton(
                            text = "Resume game",
                            onClick = { haptics.confirm(); onStartVsBot(choice, difficulty, true) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton(
                            text = "New game",
                            onClick = { haptics.tap(); confirmDiscard = SavedMode.VS_BOT },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        PrimaryButton(
                            text = "Start",
                            onClick = { haptics.confirm(); onStartVsBot(choice, difficulty, false) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    confirmDiscard?.let { mode ->
        AlertDialog(
            onDismissRequest = { confirmDiscard = null },
            title = { Text("Discard saved game?") },
            text = {
                Text(
                    when (mode) {
                        SavedMode.TWO_PLAYER ->
                            "Your paused 2-player game will be discarded. This can't be undone."
                        SavedMode.VS_BOT ->
                            "Your paused game against the bot will be discarded. This can't be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptics.confirm()
                    confirmDiscard = null
                    when (mode) {
                        SavedMode.TWO_PLAYER -> onStartTwoPlayer(false)
                        SavedMode.VS_BOT -> onStartVsBot(choice, difficulty, false)
                    }
                }) { Text("Discard & start") }
            },
            dismissButton = {
                TextButton(onClick = { haptics.tap(); confirmDiscard = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ModePicker(
    onPickTwoPlayer: () -> Unit,
    onPickVsBot: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PrimaryButton(
            text = "Pass & play",
            onClick = onPickTwoPlayer,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = "Play against the bot",
            onClick = onPickVsBot,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OptionsPanel(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Felt.OnDark,
                    )
                }
                Text(title, color = Felt.OnDark, fontSize = 18.sp)
            }
            Spacer(Modifier.height(10.dp))
            content()
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
private fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppConfig.Palette.Glass,
            contentColor = Felt.OnDark,
        ),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Normal)
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
