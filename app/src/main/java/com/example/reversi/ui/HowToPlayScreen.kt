package com.example.reversi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reversi.config.AppConfig
import com.example.reversi.game.Board
import com.example.reversi.game.GameState
import com.example.reversi.game.Move
import com.example.reversi.game.Player

/** One page of the tutorial: a board to show plus the text explaining it. */
private data class HowToStep(
    val state: GameState,
    val showHints: Boolean,
    val heading: String,
    val body: String,
)

/**
 * First-timer walkthrough. Shows the board exactly as it looks during play and
 * steps through a few fixed positions, each with a short caption. Static only —
 * no taps are accepted on the board.
 */
@Composable
fun HowToPlayScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val steps = remember { buildHowToSteps() }
    var index by remember { mutableIntStateOf(0) }
    val step = steps[index]

    BackdropBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                IconButton(onClick = { haptics.tap(); onBack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Felt.OnDark,
                    )
                }
                Text(
                    "How to play",
                    color = Felt.OnDark,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(16.dp))
            BoardView(
                state = step.state,
                flips = FlipAnimations(),
                showHints = step.showHints,
                openingFrame = null,
                onTap = { _, _ -> },
            )

            Spacer(Modifier.height(16.dp))
            GlassPanel(Modifier.fillMaxWidth().heightIn(min = 150.dp)) {
                Column {
                    Text(
                        step.heading,
                        color = Felt.OnDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        step.body,
                        color = Felt.OnDark,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            StepControls(
                index = index,
                count = steps.size,
                onPrev = { haptics.tap(); index-- },
                onNext = {
                    if (index == steps.lastIndex) {
                        haptics.confirm(); onBack()
                    } else {
                        haptics.tap(); index++
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StepControls(
    index: Int,
    count: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        NavButton(text = "Back", enabled = index > 0, onClick = onPrev)
        Text(
            "${index + 1} / $count",
            color = Felt.OnDarkDim,
            fontSize = 14.sp,
        )
        NavButton(
            text = if (index == count - 1) "Done" else "Next",
            enabled = true,
            onClick = onNext,
        )
    }
}

@Composable
private fun NavButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Felt.Highlight.copy(alpha = 0.9f),
            contentColor = Felt.Deep,
            disabledContainerColor = AppConfig.Palette.Glass,
            disabledContentColor = Felt.OnDarkDim,
        ),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * The fixed tutorial positions. The outflank example derives its "after" board
 * from the engine, so the captured row and the move marker are the real thing.
 */
private fun buildHowToSteps(): List<HowToStep> {
    // A clean, isolated horizontal outflank: Black at the left end, three
    // White pieces beside it, and the one legal move on the open right end.
    val outflankBefore = GameState(
        board = Board.fromRows(
            listOf(
                "........",
                "........",
                "........",
                "........",
                "..BWWW..",
                "........",
                "........",
                "........",
            )
        ),
        toMove = Player.BLACK,
    )
    // Playing the single legal move (the open end of the row) captures the row.
    val outflankAfter = outflankBefore.play(Move(6, 4)) ?: outflankBefore

    // A finished-looking board, filled and clearly won by Black.
    val endgame = GameState(
        board = Board.fromRows(
            listOf(
                "BBBBBBBB",
                "BBBBBBWB",
                "BBBWWWBB",
                "BBWBWBBB",
                "BWBBWBBB",
                "BBWWWBBB",
                "BBBWBBWB",
                "BBBBBBBB",
            )
        ),
        toMove = Player.BLACK,
    )

    return listOf(
        HowToStep(
            state = GameState(toMove = Player.BLACK),
            showHints = true,
            heading = "Setup",
            body = "The board starts with four pieces in the four centre squares — " +
                "two black and two white, arranged diagonally. Black always goes first.",
        ),
        HowToStep(
            state = GameState(toMove = Player.BLACK),
            showHints = true,
            heading = "Where you can play",
            body = "The small dots mark your legal moves. A move is only legal if " +
                "it outflanks the opponent: it traps one or more of their pieces " +
                "in a line between the piece you place and another of your pieces.",
        ),
        HowToStep(
            state = outflankBefore,
            showHints = true,
            heading = "Outflanking",
            body = "It's Black's turn. Playing on the dot traps White's row between " +
                "the new piece and the black piece on the far end. A trapping line " +
                "can run across, up and down, or diagonally.",
        ),
        HowToStep(
            state = outflankAfter,
            showHints = false,
            heading = "Flipping",
            body = "Once the piece is placed, every opponent piece caught in that " +
                "line immediately flips to your colour. One move captured White's " +
                "whole row.",
        ),
        HowToStep(
            state = endgame,
            showHints = false,
            heading = "How the game ends",
            body = "The game is over when the board is full or neither player can " +
                "make a legal move. The player with the most pieces on the board wins.",
        ),
    )
}
