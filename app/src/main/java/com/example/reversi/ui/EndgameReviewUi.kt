package com.example.reversi.ui

import androidx.compose.ui.graphics.Color
import com.example.reversi.analysis.EndgameReview
import com.example.reversi.analysis.Grade
import com.example.reversi.analysis.MoveVerdict
import com.example.reversi.config.AppConfig
import com.example.reversi.game.Move
import com.example.reversi.game.Player

/** A grade drawn on a placed disc in a reviewed replay. */
data class MoveBadge(val move: Move, val grade: Grade)

internal val Grade.color: Color
    get() = when (this) {
        Grade.MISTAKE -> AppConfig.Palette.Mistake
        Grade.BLUNDER -> AppConfig.Palette.Blunder
    }

internal val Grade.label: String
    get() = when (this) {
        Grade.MISTAKE -> "Mistake"
        Grade.BLUNDER -> "Blunder"
    }

internal fun squareName(m: Move): String = "${'A' + m.x}${m.y + 1}"

/** "H8 was best: a win by 6 instead of a loss by 6." */
internal fun verdictDetail(v: MoveVerdict): String =
    "${squareName(v.bestMove)} was best: ${result(v.best)} instead of ${result(v.played)}."

private fun result(discs: Int): String = when {
    discs > 0 -> "a win by $discs"
    discs < 0 -> "a loss by ${-discs}"
    else -> "a draw"
}

/**
 * "Endgame checked from move 47 · You: 1 blunder · Vishy: no mistakes", or
 * null if the game ended before any move was checked.
 */
internal fun reviewSummary(review: EndgameReview, names: (Player) -> String): String? {
    val first = review.firstPly ?: return null
    val sides = listOf(Player.BLACK, Player.WHITE).joinToString(" · ") { p ->
        val parts = Grade.entries.reversed().mapNotNull { g ->
            val n = review.count(p, g)
            if (n == 0) null else "$n ${g.label.lowercase()}${if (n == 1) "" else "s"}"
        }
        "${names(p)}: ${parts.joinToString(", ").ifEmpty { "no mistakes" }}"
    }
    return "Endgame checked from move ${first + 1}\n$sides"
}
