package com.example.reversi.bot.learning

import com.example.reversi.game.Board
import com.example.reversi.game.Cell
import com.example.reversi.game.Move
import com.example.reversi.game.Player

/**
 * The 8 symmetries of the board (rotations and reflections), used to treat
 * mirrored positions as one. All four opening moves are mirror images of each
 * other, for example, so a lesson learned after d3 also applies after c4.
 *
 * A position's [canonical] form is its smallest key over all 8 transforms.
 * Moves are stored in that canonical frame: [toCanonical] maps a real move
 * into it and [fromCanonical] maps a stored move back onto the real board.
 * Squares are encoded as `y * 8 + x`, with [PASS] for a pass.
 */
internal object BoardSymmetry {
    const val PASS = -1
    private const val N = Board.SIZE

    /** A position's canonical key, and the transform that produced it. */
    data class Canonical(val key: String, val transform: Int)

    private fun map(x: Int, y: Int, t: Int): Int {
        val m = N - 1
        val (tx, ty) = when (t) {
            0 -> x to y
            1 -> m - x to y
            2 -> x to m - y
            3 -> m - x to m - y
            4 -> y to x
            5 -> m - y to x
            6 -> y to m - x
            else -> m - y to m - x
        }
        return ty * N + tx
    }

    /** 5 and 6 are the two quarter-turns; every other transform undoes itself. */
    private fun inverse(t: Int): Int = when (t) {
        5 -> 6
        6 -> 5
        else -> t
    }

    /** Key = 64 squares ('.', 'B', 'W') plus the side to move ('b' or 'w'). */
    fun canonical(board: Board, toMove: Player): Canonical {
        val side = if (toMove == Player.BLACK) 'b' else 'w'
        var best: String? = null
        var bestT = 0
        val chars = CharArray(N * N + 1)
        chars[N * N] = side
        for (t in 0 until 8) {
            for (y in 0 until N) for (x in 0 until N) {
                chars[map(x, y, t)] = when (board.at(x, y)) {
                    Cell.BLACK -> 'B'
                    Cell.WHITE -> 'W'
                    Cell.EMPTY -> '.'
                }
            }
            val key = String(chars)
            if (best == null || key < best) { best = key; bestT = t }
        }
        return Canonical(best!!, bestT)
    }

    fun toCanonical(m: Move, t: Int): Int = if (m.isPass) PASS else map(m.x, m.y, t)

    fun fromCanonical(square: Int, t: Int): Move {
        if (square == PASS) return Move.PASS
        val real = map(square % N, square / N, inverse(t))
        return Move(real % N, real / N)
    }
}
