package com.example.othello.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.example.othello.R
import com.example.othello.config.AppConfig
import com.example.othello.game.Cell
import com.example.othello.game.GameState
import kotlinx.coroutines.delay

@Composable
fun BoardView(
    state: GameState,
    flips: FlipAnimations,
    showHints: Boolean,
    onTap: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cfg = AppConfig.Board

    BoxWithConstraints(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val boardPx = constraints.maxWidth.toFloat()
        val cellPx = boardPx / 8f
        val piecePx = cellPx * cfg.PIECE_TO_CELL_RATIO
        val boardDp = with(density) { boardPx.toDp() }
        val cellDp = with(density) { cellPx.toDp() }
        val pieceDp = with(density) { piecePx.toDp() }

        Box(
            Modifier.size(boardDp),
            contentAlignment = Alignment.TopStart,
        ) {
            Image(
                painter = painterResource(id = R.drawable.board),
                contentDescription = null,
                modifier = Modifier.size(boardDp),
                contentScale = ContentScale.FillBounds,
            )

            val legalSet = remember(state) {
                state.legalMoves.map { it.x to it.y }.toHashSet()
            }

            for (y in 0 until 8) for (x in 0 until 8) {
                val idx = y * 8 + x
                val cell = state.board.at(x, y)
                val xDp = with(density) { (x * cellPx).toDp() }
                val yDp = with(density) { (y * cellPx).toDp() }

                Box(
                    Modifier
                        .size(cellDp)
                        .offset(x = xDp, y = yDp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onTap(x, y) }
                )

                if (showHints && cfg.SHOW_LEGAL_HINTS && legalSet.contains(x to y)) {
                    Box(
                        Modifier.size(cellDp).offset(x = xDp, y = yDp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LegalHintDot(with(density) {
                            (cellPx * cfg.LEGAL_HINT_DIAMETER_RATIO).toDp()
                        })
                    }
                }

                if (cell != Cell.EMPTY) {
                    Box(
                        Modifier.size(cellDp).offset(x = xDp, y = yDp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val run = flips.running[idx]
                        if (run != null) {
                            val dir = if (cell == Cell.WHITE) FlipDir.B_TO_W else FlipDir.W_TO_B
                            FlippingPiece(dir, run.delayMs, pieceDp)
                        } else {
                            Image(
                                painter = painterResource(
                                    if (cell == Cell.BLACK) R.drawable.piece_black
                                    else R.drawable.piece_white
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(pieceDp),
                            )
                        }
                    }
                }
            }

            if (cfg.SHOW_LAST_MOVE_RING) {
                state.lastMove?.takeIf { !it.isPass }?.let { last ->
                    val xDp = with(density) { (last.x * cellPx).toDp() }
                    val yDp = with(density) { (last.y * cellPx).toDp() }
                    Box(
                        Modifier.size(cellDp).offset(x = xDp, y = yDp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LastMoveRing(with(density) {
                            (cellPx * cfg.LAST_MOVE_RING_DIAMETER_RATIO).toDp()
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun LegalHintDot(sizeDp: Dp) {
    Canvas(Modifier.size(sizeDp)) {
        drawCircle(color = AppConfig.Board.LEGAL_HINT_COLOR)
    }
}

@Composable
private fun LastMoveRing(sizeDp: Dp) {
    val cfg = AppConfig.Board
    Canvas(Modifier.size(sizeDp)) {
        drawCircle(
            color = AppConfig.Palette.Highlight.copy(alpha = cfg.LAST_MOVE_RING_ALPHA),
            style = Stroke(width = cfg.LAST_MOVE_RING_STROKE_PX),
        )
    }
}

enum class FlipDir { B_TO_W, W_TO_B }

@Composable
private fun FlippingPiece(dir: FlipDir, startDelayMs: Long, sizeDp: Dp) {
    val anim = AppConfig.Animation
    // Frame 1 is the pre-flip pose, so holding it during the ripple delay
    // keeps the cell showing its original colour until its turn comes up.
    var frame by remember { mutableIntStateOf(1) }
    LaunchedEffect(dir, startDelayMs) {
        if (startDelayMs > 0) delay(startDelayMs)
        for (i in 1..anim.FLIP_FRAME_COUNT) { frame = i; delay(anim.FLIP_FRAME_MS) }
    }
    val ctx = LocalContext.current
    val resId = remember(dir, frame) {
        val src = sourceFrameFor(frame, anim.FLIP_FRAME_COUNT, anim.FLIP_SOURCE_FRAME_COUNT)
        val n = "flip_${if (dir == FlipDir.B_TO_W) "b2w" else "w2b"}_${"%03d".format(src)}"
        ctx.resources.getIdentifier(n, "drawable", ctx.packageName)
    }
    if (resId != 0) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = null,
            modifier = Modifier.size(sizeDp),
        )
    }
}

/** Maps displayed frame index (1..count) to a source frame index (1..source). */
internal fun sourceFrameFor(displayed: Int, count: Int, source: Int): Int {
    if (count <= 1) return source
    if (count >= source) return displayed.coerceIn(1, source)
    return 1 + ((displayed - 1) * (source - 1)) / (count - 1)
}
