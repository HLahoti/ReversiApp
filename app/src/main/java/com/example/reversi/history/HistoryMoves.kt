package com.example.reversi.history

import com.example.reversi.game.Move

/**
 * Compact text form of a game's moves for [HistoryEntry.moves]: two
 * characters per ply, the square in lowercase ("d3") or "--" for a pass, so
 * a full game is about 120 characters.
 */
object HistoryMoves {
    private const val PASS = "--"

    fun encode(moves: List<Move>): String = buildString(moves.size * 2) {
        for (m in moves) {
            if (m.isPass) append(PASS) else append('a' + m.x).append('1' + m.y)
        }
    }

    /** The moves in [text], or null if it isn't a well-formed record. */
    fun decode(text: String): List<Move>? {
        if (text.length % 2 != 0) return null
        return text.chunked(2).map { ply ->
            if (ply == PASS) return@map Move.PASS
            val x = ply[0] - 'a'
            val y = ply[1] - '1'
            if (x !in 0..7 || y !in 0..7) return null
            Move(x, y)
        }
    }
}
