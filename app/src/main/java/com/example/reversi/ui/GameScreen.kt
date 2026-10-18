package com.example.reversi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reversi.config.AppConfig
import com.example.reversi.game.Player

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
    val openingFrame by vm.openingFrame.collectAsState()
    val online by vm.online.collectAsState()
    val resigned by vm.resigned.collectAsState()
    val hint by vm.hint.collectAsState()
    val haptics = rememberHaptics()
    var paused by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmResign by remember { mutableStateOf(false) }
    val gameOver = state.isTerminal || resigned != null
    // Online and vs-bot have one human seat to resign; pass-and-play has two.
    val canResign = !gameOver && when (val o = online) {
        null -> players.black == Participant.BOT || players.white == Participant.BOT
        else -> !o.opponentLeft
    }
    val vsBot = online == null &&
        (players.black == Participant.BOT || players.white == Participant.BOT)

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
                    )

                    Spacer(Modifier.height(8.dp))
                    ScoreBar(
                        black = state.blackCount,
                        white = state.whiteCount,
                        toMove = state.toMove,
                        thinking = thinking,
                    )

                    Spacer(Modifier.height(16.dp))
                    BoardView(
                        state = state,
                        flips = flips,
                        showHints = !thinking && !animating && resigned == null &&
                            players.of(state.toMove) == Participant.HUMAN,
                        hint = hint,
                        openingFrame = openingFrame,
                        onTap = { x, y ->
                            val mv = com.example.reversi.game.Move(x, y)
                            val accepted = !animating && !thinking &&
                                state.board.isLegal(mv, state.toMove)
                            if (accepted) haptics.confirm() else haptics.tap()
                            vm.onTapCell(x, y)
                        },
                    )

                    Spacer(Modifier.height(8.dp))
                    GameControls(
                        onPause = { haptics.tap(); paused = true },
                        // Online, a new game needs both players: that's the
                        // rematch flow behind "Play again", not a reset button.
                        onReset = if (online == null) {
                            { haptics.tap(); confirmReset = true }
                        } else {
                            null
                        },
                        onResign = if (canResign) {
                            { haptics.tap(); confirmResign = true }
                        } else {
                            null
                        },
                        onHint = if (vsBot && !gameOver) {
                            { haptics.tap(); vm.requestHint() }
                        } else {
                            null
                        },
                        hintEnabled = !thinking && !animating && hint == null &&
                            players.of(state.toMove) == Participant.HUMAN &&
                            state.legalMoves.isNotEmpty(),
                    )

                    Spacer(Modifier.height(8.dp))
                    val o = online
                    if (gameOver) {
                        EndGamePanel(
                            black = state.blackCount,
                            white = state.whiteCount,
                            resigned = resigned,
                            resignedByMe = resigned?.let { players.of(it) == Participant.HUMAN } == true,
                            online = o,
                            onPlayAgain = { haptics.confirm(); vm.playAgain() },
                        )
                    } else if (o != null && o.opponentLeft) {
                        GlassPanel(Modifier.fillMaxWidth()) {
                            Text("Your opponent left the game.", color = Felt.OnDark)
                        }
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

        if (confirmResign) {
            AlertDialog(
                onDismissRequest = { confirmResign = false },
                title = { Text("Resign game?") },
                text = { Text("You'll forfeit this game and your opponent wins. This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        haptics.confirm()
                        confirmResign = false
                        vm.resign()
                    }) { Text("Resign") }
                },
                dismissButton = {
                    TextButton(onClick = { haptics.tap(); confirmResign = false }) { Text("Cancel") }
                },
            )
        }

        if (confirmReset) {
            AlertDialog(
                onDismissRequest = { confirmReset = false },
                title = { Text("Reset game?") },
                text = { Text("The current game will be lost and a new one will start. This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        haptics.confirm()
                        confirmReset = false
                        vm.resetGame()
                    }) { Text("Reset") }
                },
                dismissButton = {
                    TextButton(onClick = { haptics.tap(); confirmReset = false }) { Text("Cancel") }
                },
            )
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
        // Balances the back button so the title stays centred.
        Spacer(Modifier.size(48.dp))
    }
}

/**
 * Pause, reset, resign and hint, in a row under the board. [onReset] is null
 * online; [onResign] is null in pass-and-play and once the game is over;
 * [onHint] is null outside vs-bot games and once the game is over.
 */
@Composable
private fun GameControls(
    onPause: () -> Unit,
    onReset: (() -> Unit)?,
    onResign: (() -> Unit)?,
    onHint: (() -> Unit)?,
    hintEnabled: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = onPause) {
            Icon(Icons.Default.Pause, contentDescription = "Pause", tint = Felt.OnDark)
        }
        if (onReset != null) {
            IconButton(onClick = onReset) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Felt.OnDark)
            }
        }
        if (onResign != null) {
            IconButton(onClick = onResign) {
                Icon(Icons.Default.Flag, contentDescription = "Resign", tint = Felt.OnDark)
            }
        }
        if (onHint != null) {
            IconButton(onClick = onHint, enabled = hintEnabled) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = "Hint",
                    tint = if (hintEnabled) Felt.OnDark else Felt.OnDarkDim,
                )
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
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .border(BorderStroke(1.dp, Felt.GlassBorder), shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(AppConfig.Palette.BlackBadge),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$black",
                color = Felt.OnDark,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        0.0f to Felt.GlassTop,
                        1.0f to Felt.GlassBottom,
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    thinking -> "Thinking…"
                    toMove == Player.BLACK -> "Black to move"
                    else -> "White to move"
                },
                color = Felt.OnDark,
                fontSize = 14.sp,
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(AppConfig.Palette.WhiteBadge),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$white",
                color = Felt.Deep,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun EndGamePanel(
    black: Int,
    white: Int,
    resigned: Player?,
    resignedByMe: Boolean,
    online: OnlineUiState?,
    onPlayAgain: () -> Unit,
) {
    val msg = when {
        resigned != null -> {
            val winner = if (resigned == Player.BLACK) "White" else "Black"
            winner + if (resignedByMe) " wins · you resigned" else " wins · your opponent resigned"
        }
        black > white -> "Black wins · $black – $white"
        white > black -> "White wins · $white – $black"
        else -> "Draw · $black – $white"
    }
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(msg, color = Felt.OnDark, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            when {
                online?.opponentLeft == true ->
                    Text("Your opponent left the game.", color = Felt.OnDarkDim, fontSize = 14.sp)
                online?.rematchRequested == true ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(40.dp),
                    ) {
                        CircularProgressIndicator(
                            color = Felt.Highlight,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Waiting for opponent to rematch…",
                            color = Felt.OnDark,
                            fontSize = 14.sp,
                        )
                    }
                else -> {
                    if (online?.opponentWantsRematch == true) {
                        Text(
                            "Your opponent wants a rematch",
                            color = Felt.OnDarkDim,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
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
    }
}
