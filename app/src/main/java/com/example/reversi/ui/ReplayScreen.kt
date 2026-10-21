package com.example.reversi.ui

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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reversi.analysis.EndgameReview
import com.example.reversi.analysis.EndgameReviews
import com.example.reversi.analysis.MoveVerdict
import com.example.reversi.game.Player
import com.example.reversi.history.HistoryEntry
import com.example.reversi.history.HistoryMode
import com.example.reversi.history.HistoryMoves
import com.example.reversi.history.HistoryOutcome
import com.example.reversi.history.HistoryRegistry
import com.example.reversi.profile.ProfileRegistry

/**
 * A finished game from the history, to step through move by move with the
 * usual flip animations. Read-only: the board takes no taps.
 */
@Composable
fun ReplayScreen(entryId: String, onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val entry = remember(entryId) { HistoryRegistry.current.entries.value.find { it.id == entryId } }
    val record = remember(entry) { entry?.moves?.let(HistoryMoves::decode) }
    val vm: GameViewModel = viewModel()
    LaunchedEffect(record) { record?.let(vm::configureReplay) }
    val review by produceState<EndgameReview?>(null, record) {
        value = record?.let { EndgameReviews.of(it) }
    }
    val selfLabel = ProfileRegistry.current.selfLabel
    val nameOf = { p: Player -> if (p == entry?.myColor) selfLabel else entry?.let(::opponentLabel) ?: "Bot" }

    val state by vm.state.collectAsState()
    val flips by vm.flips.collectAsState()
    val log by vm.log.collectAsState()
    val viewingIndex by vm.viewingIndex.collectAsState()
    val animating by vm.animating.collectAsState()
    val playing by vm.replayPlaying.collectAsState()
    val last = log.size - 1
    val verdict = review?.verdicts?.get(viewingIndex)?.takeIf { it.grade != null }

    BackdropBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp),
        ) {
            GameTopBar(
                title = entry?.let(::replayTitle) ?: "Replay",
                onBack = { haptics.tap(); onBack() },
            )
            if (entry == null || record == null) {
                Spacer(Modifier.height(24.dp))
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text("This game's moves weren't kept, so it can't be replayed.", color = Felt.OnDark)
                }
                return@Column
            }

            Spacer(Modifier.height(8.dp))
            ScoreBar(
                black = state.blackCount,
                white = state.whiteCount,
                toMove = state.toMove,
                thinking = false,
            )
            Spacer(Modifier.height(16.dp))
            BoardView(
                state = state,
                flips = flips,
                showHints = false,
                hint = null,
                openingFrame = null,
                onTap = { _, _ -> },
                badge = verdict?.let { MoveBadge(it.move, it.grade!!) },
                ghost = verdict?.bestMove,
                ghostColor = verdict?.mover ?: Player.BLACK,
            )

            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val atStart = viewingIndex < 0
                val atEnd = viewingIndex >= last
                ReplayButton(Icons.Default.SkipPrevious, "Start", enabled = !atStart) {
                    haptics.tap(); vm.replayTo(-1)
                }
                ReplayButton(Icons.Default.ChevronLeft, "Previous move", enabled = !atStart && !animating) {
                    haptics.tap(); vm.stepReplay(forward = false)
                }
                ReplayButton(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (playing) "Pause" else "Play",
                    enabled = true,
                ) {
                    haptics.tap(); vm.toggleReplayPlay()
                }
                ReplayButton(Icons.Default.ChevronRight, "Next move", enabled = !atEnd && !animating) {
                    haptics.tap(); vm.stepReplay(forward = true)
                }
                ReplayButton(Icons.Default.SkipNext, "End", enabled = !atEnd) {
                    haptics.tap(); vm.replayTo(last)
                }
            }

            Spacer(Modifier.height(8.dp))
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        replayResult(entry, selfLabel),
                        color = Felt.OnDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (viewingIndex < 0) "Start · ${log.size} moves" else "Move ${viewingIndex + 1} of ${log.size}",
                        color = Felt.OnDarkDim,
                        fontSize = 14.sp,
                    )
                    ReviewNote(review, verdict, nameOf)
                }
            }

            Spacer(Modifier.weight(1f))
            MoveLog(
                entries = log,
                currentIndex = viewingIndex,
                onJump = { idx -> haptics.tap(); vm.replayTo(idx) },
                modifier = Modifier.fillMaxWidth(),
                marks = remember(review) {
                    review?.verdicts?.mapNotNull { (ply, v) -> v.grade?.let { ply to it } }?.toMap().orEmpty()
                },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Under the move count: the grade and the better move when the move shown
 * was marked, otherwise how the endgame went for each side.
 */
@Composable
private fun ReviewNote(review: EndgameReview?, verdict: MoveVerdict?, nameOf: (Player) -> String) {
    if (review == null) {
        Spacer(Modifier.height(6.dp))
        Text("Checking the endgame…", color = Felt.OnDarkDim, fontSize = 13.sp)
        return
    }
    if (verdict != null) {
        val grade = verdict.grade!!
        Spacer(Modifier.height(6.dp))
        Text(
            "${nameOf(verdict.mover)}: ${squareName(verdict.move)}${grade.suffix} ${grade.label}",
            color = grade.color,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(verdictDetail(verdict), color = Felt.OnDarkDim, fontSize = 13.sp, textAlign = TextAlign.Center)
        return
    }
    reviewSummary(review, nameOf)?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, color = Felt.OnDarkDim, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ReplayButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = label, tint = if (enabled) Felt.OnDark else Felt.OnDarkDim)
    }
}

private fun opponentLabel(e: HistoryEntry): String =
    e.opponentName ?: if (e.mode == HistoryMode.ONLINE) "Online player" else "Bot"

/** Like the game titles: "vs Guk · you were black". */
private fun replayTitle(e: HistoryEntry): String =
    "vs ${opponentLabel(e)} · you were ${e.myColor.name.lowercase()}"

/** "You won · 34 – 30", or who resigned. Scores are given winner first. */
private fun replayResult(e: HistoryEntry, self: String): String {
    val opponent = opponentLabel(e)
    val mine = if (e.myColor == Player.BLACK) e.blackCount else e.whiteCount
    val theirs = e.blackCount + e.whiteCount - mine
    return when {
        e.resignedBy == e.myColor -> "$opponent won · $self resigned"
        e.resignedBy != null -> "$self won · $opponent resigned"
        e.outcome == HistoryOutcome.WIN -> "$self won · $mine – $theirs"
        e.outcome == HistoryOutcome.LOSS -> "$opponent won · $theirs – $mine"
        else -> "Draw · $mine – $theirs"
    }
}
