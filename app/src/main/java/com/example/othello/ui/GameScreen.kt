package com.example.othello.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.othello.config.AppConfig
import com.example.othello.game.Player

@Composable
fun GameScreen(
    vm: GameViewModel,
    title: String,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val flips by vm.flips.collectAsState()
    val thinking by vm.botThinking.collectAsState()
    val animating by vm.animating.collectAsState()
    val players by vm.players.collectAsState()
    val log by vm.log.collectAsState()
    val viewingIndex by vm.viewingIndex.collectAsState()
    val haptics = rememberHaptics()
    var paused by remember { mutableStateOf(false) }

    // Save on app pause / background and again when the screen is torn down
    // (back arrow, system back, or navigating away). persist() internally
    // clears the save if there's nothing worth resuming (fresh board or a
    // finished game).
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentVm by rememberUpdatedState(vm)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) currentVm.persist()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            currentVm.persist()
        }
    }

    Box(Modifier.fillMaxSize()) {
        val contentModifier =
            if (paused) Modifier.blur(AppConfig.Pause.BLUR_RADIUS) else Modifier
        Box(contentModifier) {
            BackdropBackground {
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 16.dp),
                ) {
                    TopBar(
                        title = title,
                        onBack = { haptics.tap(); onBack() },
                        onReset = { haptics.confirm(); vm.resetGame() },
                        onPause = { haptics.tap(); paused = true },
                    )

                    Spacer(Modifier.height(8.dp))
                    ScoreBar(
                        black = state.blackCount,
                        white = state.whiteCount,
                        toMove = state.toMove,
                        thinking = thinking,
                        blackIsBot = players.of(Player.BLACK) == Participant.BOT,
                        whiteIsBot = players.of(Player.WHITE) == Participant.BOT,
                    )

                    Spacer(Modifier.height(16.dp))
                    BoardView(
                        state = state,
                        flips = flips,
                        showHints = !thinking && !animating &&
                            players.of(state.toMove) == Participant.HUMAN,
                        onTap = { x, y ->
                            val mv = com.example.othello.game.Move(x, y)
                            val accepted = !animating && !thinking &&
                                state.board.isLegal(mv, state.toMove)
                            if (accepted) haptics.confirm() else haptics.tap()
                            vm.onTapCell(x, y)
                        },
                    )

                    Spacer(Modifier.height(16.dp))
                    if (state.isTerminal) {
                        EndGamePanel(
                            black = state.blackCount,
                            white = state.whiteCount,
                            onPlayAgain = { haptics.confirm(); vm.resetGame() },
                        )
                    } else if (state.legalMoves.isEmpty() &&
                        players.of(state.toMove) == Participant.HUMAN
                    ) {
                        GlassPanel(Modifier.fillMaxWidth()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No legal moves — you must pass.", color = Felt.OnDark)
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = { haptics.confirm(); vm.passTurn() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Felt.Highlight,
                                        contentColor = Felt.Deep,
                                    ),
                                ) { Text("Pass") }
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))
                    MoveLog(
                        entries = log,
                        currentIndex = viewingIndex,
                        onJump = { idx -> haptics.tap(); vm.jumpTo(idx) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        if (paused) {
            PausedOverlay(onResume = { haptics.tap(); paused = false })
        }
    }
}

@Composable
private fun PausedOverlay(onResume: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = AppConfig.Pause.OVERLAY_ALPHA))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onResume() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Paused",
                color = Felt.OnDark,
                fontSize = 44.sp,
                fontWeight = FontWeight.Light,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Tap anywhere to resume",
                color = Felt.OnDarkDim,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onPause: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Felt.OnDark,
            )
        }
        Text(title, color = Felt.OnDark, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPause) {
                Icon(Icons.Default.Pause, contentDescription = "Pause", tint = Felt.OnDark)
            }
            IconButton(onClick = onReset) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Felt.OnDark)
            }
        }
    }
}

@Composable
private fun ScoreBar(
    black: Int,
    white: Int,
    toMove: Player,
    thinking: Boolean,
    blackIsBot: Boolean,
    whiteIsBot: Boolean,
) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerBadge("Black", black, color = AppConfig.Palette.BlackBadge,
                active = toMove == Player.BLACK, isBot = blackIsBot, thinking = thinking)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when {
                        thinking -> "Thinking…"
                        else -> if (toMove == Player.BLACK) "Black to move" else "White to move"
                    },
                    color = Felt.OnDarkDim, fontSize = 13.sp,
                )
            }
            PlayerBadge("White", white, color = AppConfig.Palette.WhiteBadge,
                active = toMove == Player.WHITE, isBot = whiteIsBot, thinking = thinking)
        }
    }
}

@Composable
private fun PlayerBadge(
    label: String,
    score: Int,
    color: Color,
    active: Boolean,
    isBot: Boolean,
    thinking: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(28.dp)
                .background(color = color, shape = CircleShape)
                .padding(2.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "$label${if (isBot) " · bot" else ""}",
                color = if (active) Felt.Highlight else Felt.OnDarkDim,
                fontSize = 12.sp,
            )
            Text(
                "$score",
                color = Felt.OnDark,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun EndGamePanel(black: Int, white: Int, onPlayAgain: () -> Unit) {
    val msg = when {
        black > white -> "Black wins · $black – $white"
        white > black -> "White wins · $white – $black"
        else -> "Draw · $black – $white"
    }
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(msg, color = Felt.OnDark, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onPlayAgain,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Felt.Highlight,
                    contentColor = Felt.Deep,
                ),
            ) { Text("Play again") }
        }
    }
}
