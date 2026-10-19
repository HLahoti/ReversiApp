package com.example.reversi.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.example.reversi.update.Updates
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.reversi.R
import com.example.reversi.bot.Opponent
import com.example.reversi.config.AppConfig
import com.example.reversi.data.GameStore
import com.example.reversi.data.SavedMode
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onStartTwoPlayer: (resume: Boolean) -> Unit,
    onStartVsBot: (humanColor: HumanColorChoice, opponent: Opponent, resume: Boolean) -> Unit,
    onStartOnline: () -> Unit,
    onHistory: () -> Unit,
    onHowToPlay: () -> Unit,
    onSettings: () -> Unit,
) {
    val haptics = rememberHaptics()
    val ctx = LocalContext.current
    val store = remember(ctx) { GameStore(ctx.applicationContext) }
    var choice by remember { mutableStateOf(HumanColorChoice.RANDOM) }
    var opponent by remember { mutableStateOf(Opponent.DEFAULT) }

    // Re-check save existence whenever this screen comes to the foreground
    // (back from a game, app resume) so Resume/New buttons stay in sync.
    var hasTwoPlayer by remember { mutableStateOf(store.has(SavedMode.TWO_PLAYER)) }
    var savedBots by remember { mutableStateOf(store.opponentsWithSaves()) }
    val hasVsBot = opponent in savedBots
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasTwoPlayer = store.has(SavedMode.TWO_PLAYER)
                savedBots = store.opponentsWithSaves()
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

    // Scrollable so the bot options' Start button stays reachable on short
    // screens. fillMaxSize before verticalScroll keeps the viewport height as
    // the minimum, so content still centres when it fits.
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    // Opening a mode's options (or picking a bot that swaps Start for
    // Resume/New game) jumps to the bottom so the buttons are in view; going
    // back returns to the top.
    LaunchedEffect(step, hasVsBot) {
        if (step == null) {
            scroll.animateScrollTo(0)
        } else {
            withFrameNanos { } // let the new panel lay out so maxValue is current
            scroll.animateScrollTo(scroll.maxValue)
        }
    }

    BackdropBackground {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SpinningPiece(target = choice, size = 160.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Reversi",
                    color = Felt.OnDark,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Light,
                )
                Spacer(Modifier.height(44.dp))

                when (step) {
                    null -> ModePicker(
                        onPickTwoPlayer = { haptics.tap(); step = SavedMode.TWO_PLAYER },
                        onPickVsBot = { haptics.tap(); step = SavedMode.VS_BOT },
                        onPickOnline = { haptics.confirm(); onStartOnline() },
                        onHistory = { haptics.tap(); onHistory() },
                        onHowToPlay = { haptics.tap(); onHowToPlay() },
                        onSettings = { haptics.tap(); onSettings() },
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
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Opponent · weakest → strongest",
                            color = Felt.OnDark.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        OpponentChips(
                            Opponent.entries.filter { it.learning == null },
                            opponent,
                            savedBots,
                        ) { haptics.tap(); opponent = it }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Learns as you play",
                            color = Felt.OnDark.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        OpponentChips(
                            Opponent.entries.filter { it.learning != null },
                            opponent,
                            savedBots,
                        ) { haptics.tap(); opponent = it }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Starts just below Pragg and studies every game you finish against it. " +
                                "It never loses the same way twice, so it grows with you.",
                            color = Felt.OnDark.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        Spacer(Modifier.height(14.dp))
                        if (hasVsBot) {
                            PrimaryButton(
                                text = "Resume game",
                                onClick = { haptics.confirm(); onStartVsBot(choice, opponent, true) },
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
                                onClick = { haptics.confirm(); onStartVsBot(choice, opponent, false) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                UpdateBanner()
            }
            MoreBelowHint(
                visible = scroll.canScrollForward,
                onClick = { scope.launch { scroll.animateScrollTo(scroll.maxValue) } },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
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
                            "Your paused game against ${opponent.displayName} will be discarded. This can't be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptics.confirm()
                    confirmDiscard = null
                    when (mode) {
                        SavedMode.TWO_PLAYER -> onStartTwoPlayer(false)
                        SavedMode.VS_BOT -> onStartVsBot(choice, opponent, false)
                    }
                }) { Text("Discard & start") }
            },
            dismissButton = {
                TextButton(onClick = { haptics.tap(); confirmDiscard = null }) { Text("Cancel") }
            },
        )
    }
}

/**
 * "Version x is available", while an update is known and the player hasn't
 * closed this version's banner. Update opens it; see [Updates].
 */
@Composable
private fun UpdateBanner() {
    val haptics = rememberHaptics()
    val activity = LocalContext.current as? Activity
    val update by Updates.available.collectAsState()
    val dismissed by Updates.dismissedVersion.collectAsState()
    val u = update?.takeIf { it.versionName != dismissed } ?: return
    GlassPanel(
        Modifier.fillMaxWidth(),
        cornerRadius = 16,
        padding = PaddingValues(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Version ${u.versionName} is available",
                color = Felt.OnDark,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { haptics.confirm(); activity?.let(Updates::startUpdate) }) {
                Text("Update", color = Felt.Highlight, fontWeight = FontWeight.Medium)
            }
            IconButton(onClick = { haptics.tap(); Updates.dismissBanner() }) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Felt.OnDarkDim)
            }
        }
    }
}

/** Fade and arrow at the bottom edge, shown while more content sits below. */
@Composable
private fun MoreBelowHint(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Felt.Deep.copy(alpha = 0.85f))))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(top = 24.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onClick) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Scroll down for more",
                    tint = Felt.OnDark,
                )
            }
        }
    }
}

@Composable
private fun ModePicker(
    onPickTwoPlayer: () -> Unit,
    onPickVsBot: () -> Unit,
    onPickOnline: () -> Unit,
    onHistory: () -> Unit,
    onHowToPlay: () -> Unit,
    onSettings: () -> Unit,
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
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = "Play online",
            onClick = onPickOnline,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        IconBar(
            items = listOf(
                Triple(Icons.Default.History, "History", onHistory),
                Triple(Icons.AutoMirrored.Filled.HelpOutline, "How to play", onHowToPlay),
                Triple(Icons.Default.Settings, "Settings", onSettings),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The secondary destinations as one glass bar of icons, split evenly with
 * thin dividers; each icon's label is its content description.
 */
@Composable
private fun IconBar(items: List<Triple<ImageVector, String, () -> Unit>>, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(AppConfig.Palette.Glass),
    ) {
        items.forEachIndexed { i, (icon, label, onClick) ->
            if (i > 0) {
                Box(Modifier.width(1.dp).height(24.dp).background(Felt.GlassBorder))
            }
            IconButton(onClick = onClick, modifier = Modifier.weight(1f).fillMaxHeight()) {
                Icon(icon, contentDescription = label, tint = Felt.OnDark)
            }
        }
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

/** A row of bot chips, in the order given (weakest first), wrapping as needed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpponentChips(
    opponents: List<Opponent>,
    selected: Opponent,
    withSaves: Set<Opponent>,
    onSelect: (Opponent) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        opponents.forEach { o ->
            ColorChip(o.displayName, selected == o, hasSave = o in withSaves) { onSelect(o) }
        }
    }
}

/** [hasSave] adds a dot after the label, marking a bot with a paused game. */
@Composable
private fun ColorChip(
    label: String,
    selected: Boolean,
    hasSave: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = if (selected) Felt.Highlight.copy(alpha = 0.9f) else AppConfig.Palette.Glass
    val fg = if (selected) Felt.Deep else Felt.OnDark
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
    ) {
        Text(label, fontSize = 14.sp)
        if (hasSave) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (selected) Felt.Deep else Felt.Highlight)
                    .semantics { contentDescription = "has a paused game" },
            )
        }
    }
}
