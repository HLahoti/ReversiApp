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
import com.example.reversi.data.UserSettings
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
    val botName by vm.botName.collectAsState()
    val hintsOff by UserSettings.hintsOff.collectAsState()
    val takebacksOff by UserSettings.takebacksOff.collectAsState()
    val haptics = rememberHaptics()
    var paused by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmResign by remember { mutableStateOf(false) }
    var confirmHintsOn by remember { mutableStateOf(false) }
    var confirmTakebacksOn by remember { mutableStateOf(false) }
    val gameOver = state.isTerminal || resigned != null
    // Online and vs-bot have one human seat to resign; pass-and-play has two.
    val canResign = !gameOver && when (val o = online) {
        null -> players.black == Participant.BOT || players.white == Participant.BOT
        else -> !o.opponentLeft
    }
    val vsBot = online == null &&
        (players.black == Participant.BOT || players.white == Participant.BOT)
    // Looking back at an earlier position with takebacks off: browse only.
    val rewoundNoTakebacks = vsBot && takebacksOff && viewingIndex < log.size - 1
    // Who "your opponent" is, where there is one: the online player or the bot.
    val opponentName = online?.opponentName ?: botName

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
                    GameTopBar(
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
                            players.of(state.toMove) == Participant.HUMAN && !rewoundNoTakebacks,
                        hint = hint,
                        openingFrame = openingFrame,
                        onTap = { x, y ->
                            if (rewoundNoTakebacks) {
                                haptics.tap()
                                confirmTakebacksOn = true
                                return@BoardView
                            }
                            val mv = com.example.reversi.game.Move(x, y)
                            val accepted = !animating && !thinking &&
                                state.board.isLegal(mv, state.toMove)
                            if (accepted) haptics.confirm() else haptics.tap()
                            vm.onTapCell(x, y)
                        },
                    )

                    // Nothing to pause, reset, resign or hint once the game is over.
                    if (!gameOver) {
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
                            onHint = if (vsBot) {
                                {
                                    haptics.tap()
                                    if (hintsOff) confirmHintsOn = true else vm.requestHint()
                                }
                            } else {
                                null
                            },
                            hintEnabled = !thinking && !animating && hint == null &&
                                players.of(state.toMove) == Participant.HUMAN &&
                                state.legalMoves.isNotEmpty(),
                            hintsOff = hintsOff,
                            // Nothing to reset or resign before the first move.
                            started = log.isNotEmpty(),
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    val o = online
                    if (gameOver) {
                        EndGamePanel(
                            black = state.blackCount,
                            white = state.whiteCount,
                            resigned = resigned,
                            resignedByMe = resigned?.let { players.of(it) == Participant.HUMAN } == true,
                            online = o,
                            vsOpponent = o != null || vsBot,
                            opponentName = opponentName,
                            myColor = if (players.black == Participant.HUMAN) Player.BLACK else Player.WHITE,
                            onPlayAgain = { haptics.confirm(); vm.playAgain() },
                        )
                    } else if (o != null && o.opponentLeft) {
                        GlassPanel(Modifier.fillMaxWidth()) {
                            Text("${o.opponentSubject} left the game.", color = Felt.OnDark)
                        }
                    } else if (state.legalMoves.isEmpty() &&
                        players.of(state.toMove) == Participant.HUMAN && !rewoundNoTakebacks
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
                text = {
                    Text("You'll forfeit this game and ${opponentName ?: "your opponent"} wins. This can't be undone.")
                },
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

        if (confirmTakebacksOn) {
            AlertDialog(
                onDismissRequest = { confirmTakebacksOn = false },
                title = { Text("Turn takebacks on?") },
                text = {
                    Text(
                        "Takebacks are turned off in Settings, so you can look back through the game " +
                            "but not play on from an earlier move. Turn them back on for all games?",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        haptics.confirm()
                        confirmTakebacksOn = false
                        UserSettings.setTakebacksOff(false)
                    }) { Text("Turn on") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        haptics.tap()
                        confirmTakebacksOn = false
                        // Back to the live position, where play carries on.
                        vm.jumpTo(log.size - 1)
                    }) { Text("Back to game") }
                },
            )
        }

        if (confirmHintsOn) {
            AlertDialog(
                onDismissRequest = { confirmHintsOn = false },
                title = { Text("Turn hints on?") },
                text = { Text("Hints are turned off in Settings. Turn them back on for all games?") },
                confirmButton = {
                    TextButton(onClick = {
                        haptics.confirm()
                        confirmHintsOn = false
                        UserSettings.setHintsOff(false)
                    }) { Text("Turn on") }
                },
                dismissButton = {
                    TextButton(onClick = { haptics.tap(); confirmHintsOn = false }) { Text("Keep off") }
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
internal fun GameTopBar(
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
 * Pause, reset, resign and hint, in a row under the board; only shown while
 * the game is in progress. [onReset] is null online; [onResign] is null in
 * pass-and-play; [onHint] is null outside vs-bot games. Reset and resign are
 * disabled until [started]. With [hintsOff] the hint bulb looks disabled but
 * still takes taps, so [onHint] can offer to turn hints back on.
 */
@Composable
private fun GameControls(
    onPause: () -> Unit,
    onReset: (() -> Unit)?,
    onResign: (() -> Unit)?,
    onHint: (() -> Unit)?,
    hintEnabled: Boolean,
    hintsOff: Boolean,
    started: Boolean,
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
            IconButton(onClick = onReset, enabled = started) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Reset",
                    tint = if (started) Felt.OnDark else Felt.OnDarkDim,
                )
            }
        }
        if (onResign != null) {
            IconButton(onClick = onResign, enabled = started) {
                Icon(
                    Icons.Default.Flag,
                    contentDescription = "Resign",
                    tint = if (started) Felt.OnDark else Felt.OnDarkDim,
                )
            }
        }
        if (onHint != null) {
            IconButton(onClick = onHint, enabled = hintsOff || hintEnabled) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = if (hintsOff) "Hints off" else "Hint",
                    tint = if (hintEnabled && !hintsOff) Felt.OnDark else Felt.OnDarkDim,
                )
            }
        }
    }
}

@Composable
internal fun ScoreBar(
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
    /**
     * Played against one opponent (online or a bot), so the result names
     * "you" and [opponentName] rather than the colours, as pass-and-play does.
     */
    vsOpponent: Boolean,
    opponentName: String?,
    /** The local player's colour; only read when [vsOpponent]. */
    myColor: Player,
    onPlayAgain: () -> Unit,
) {
    val mine = if (myColor == Player.BLACK) black else white
    val theirs = if (myColor == Player.BLACK) white else black
    val msg = when {
        vsOpponent -> {
            val them = opponentName ?: "Your opponent"
            when {
                resigned != null && resignedByMe -> "$them wins · you resigned"
                resigned != null -> "You win · ${opponentName ?: "your opponent"} resigned"
                mine > theirs -> "You win · $mine – $theirs"
                theirs > mine -> "$them wins · $theirs – $mine"
                else -> "Draw · $black – $white"
            }
        }
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
                    Text("${online.opponentSubject} left the game.", color = Felt.OnDarkDim, fontSize = 14.sp)
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
                            "Waiting for ${online.opponentObject} to rematch…",
                            color = Felt.OnDark,
                            fontSize = 14.sp,
                        )
                    }
                else -> {
                    if (online?.opponentWantsRematch == true) {
                        Text(
                            "${online.opponentSubject} wants a rematch",
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

/** The opponent at the start of a sentence: their display name online, when known. */
private val OnlineUiState?.opponentSubject: String
    get() = this?.opponentName ?: "Your opponent"

/** The opponent mid-sentence: their display name online, when known. */
private val OnlineUiState?.opponentObject: String
    get() = this?.opponentName ?: "your opponent"
