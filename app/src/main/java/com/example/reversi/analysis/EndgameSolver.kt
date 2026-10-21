package com.example.reversi.analysis

import com.example.reversi.game.Board
import com.example.reversi.game.Cell
import com.example.reversi.game.Move
import com.example.reversi.game.Player

/**
 * Exact endgame solver: plays every line to the end and returns the final
 * disc difference under perfect play by both sides. Positions are a pair of
 * bitboards (bit `y * 8 + x`), the mover's discs and the opponent's, since
 * [Board] allocates on every move and is far too slow for this.
 *
 * Scores are plain disc counts, mover minus opponent, matching how the app
 * scores finished games ([com.example.reversi.bot.AlphaBetaSearch.terminalScore]):
 * squares left empty when neither side can move count for nobody.
 *
 * [checkAbort] is called every few thousand positions so a long solve can be
 * cancelled; it stops the search by throwing.
 */
class EndgameSolver(private val checkAbort: () -> Unit = {}) {

    /** Positions visited since this solver was made, for benchmarking. */
    var nodes = 0L
        private set

    /** Exact score for [mover] in [board], with [mover] to play. */
    fun solve(board: Board, mover: Player): Int {
        val (p, o) = bits(board, mover)
        return negamax(p, o, -MAX, MAX)
    }

    /**
     * Exact score for the mover after playing [move] (a legal move or a
     * forced pass) in position ([p], [o]), searched with the window
     * ([alpha], [beta]) from the mover's side. Fail-soft: a result at or
     * below [alpha] is only an upper bound, which is all a caller looking
     * for something better needs.
     */
    internal fun scoreAfter(p: Long, o: Long, move: Int, alpha: Int, beta: Int): Int {
        if (move < 0) return -negamax(o, p, -beta, -alpha)
        val f = flips(move, p, o)
        val np = p or f or (1L shl move)
        val no = o and f.inv()
        return -negamax(no, np, -beta, -alpha)
    }

    private fun negamax(p: Long, o: Long, alpha0: Int, beta: Int): Int {
        if (++nodes and ABORT_CHECK_MASK == 0L) checkAbort()
        var alpha = alpha0
        val moves = movesOf(p, o)
        if (moves == 0L) {
            if (movesOf(o, p) == 0L) return java.lang.Long.bitCount(p) - java.lang.Long.bitCount(o)
            return -negamax(o, p, -beta, -alpha)
        }
        val empties = 64 - java.lang.Long.bitCount(p or o)
        var best = -MAX - 1
        if (empties <= ORDERING_MIN_EMPTIES) {
            var m = moves
            while (m != 0L) {
                val sq = java.lang.Long.numberOfTrailingZeros(m)
                m = m and (m - 1)
                val f = flips(sq, p, o)
                val score = -negamax(o and f.inv(), p or f or (1L shl sq), -beta, -alpha)
                if (score > best) {
                    best = score
                    if (score > alpha) {
                        alpha = score
                        if (alpha >= beta) break
                    }
                }
            }
            return best
        }
        // Fastest-first: try the replies that leave the opponent fewest moves
        // of their own. It finds the cutoffs much sooner than square order.
        val n = java.lang.Long.bitCount(moves)
        val squares = IntArray(n)
        val keys = IntArray(n)
        var m = moves
        for (i in 0 until n) {
            val sq = java.lang.Long.numberOfTrailingZeros(m)
            m = m and (m - 1)
            val f = flips(sq, p, o)
            val np = p or f or (1L shl sq)
            val no = o and f.inv()
            // Corners are almost always good, so they go first among equals.
            val corner = if ((1L shl sq) and CORNERS != 0L) 0 else 1
            squares[i] = sq
            keys[i] = java.lang.Long.bitCount(movesOf(no, np)) * 2 + corner
        }
        for (i in 1 until n) {
            val k = keys[i]; val s = squares[i]
            var j = i - 1
            while (j >= 0 && keys[j] > k) { keys[j + 1] = keys[j]; squares[j + 1] = squares[j]; j-- }
            keys[j + 1] = k; squares[j + 1] = s
        }
        for (sq in squares) {
            val f = flips(sq, p, o)
            val score = -negamax(o and f.inv(), p or f or (1L shl sq), -beta, -alpha)
            if (score > best) {
                best = score
                if (score > alpha) {
                    alpha = score
                    if (alpha >= beta) break
                }
            }
        }
        return best
    }

    companion object {
        /** Bigger than any disc difference. */
        internal const val MAX = 64

        /** Below this many empties, move ordering costs more than it saves. */
        private const val ORDERING_MIN_EMPTIES = 6
        private const val ABORT_CHECK_MASK = 0xFFFL
        private const val CORNERS = (1L shl 0) or (1L shl 7) or (1L shl 56) or (1L shl 63)

        private const val NOT_A = -0x101010101010102L // every file but A
        private const val NOT_H = 0x7F7F7F7F7F7F7F7FL // every file but H
        private val SHIFTS = intArrayOf(1, -1, 8, -8, 9, 7, -7, -9)
        private val MASKS = longArrayOf(NOT_A, NOT_H, -1L, -1L, NOT_A, NOT_H, NOT_A, NOT_H)

        private fun shift(b: Long, d: Int, mask: Long): Long =
            (if (d > 0) b shl d else b ushr -d) and mask

        /** Squares where the side with discs [p] can play against [o]. */
        internal fun movesOf(p: Long, o: Long): Long {
            val empty = (p or o).inv()
            var moves = 0L
            for (i in 0 until 8) {
                val d = SHIFTS[i]; val mask = MASKS[i]
                var t = shift(p, d, mask) and o
                repeat(5) { t = t or (shift(t, d, mask) and o) }
                moves = moves or (shift(t, d, mask) and empty)
            }
            return moves
        }

        /** Discs of [o] that a disc placed on [sq] by [p] turns over. */
        internal fun flips(sq: Int, p: Long, o: Long): Long {
            var all = 0L
            val from = 1L shl sq
            for (i in 0 until 8) {
                val d = SHIFTS[i]; val mask = MASKS[i]
                var line = 0L
                var x = shift(from, d, mask)
                while (x != 0L && x and o != 0L) {
                    line = line or x
                    x = shift(x, d, mask)
                }
                if (x and p != 0L) all = all or line
            }
            return all
        }

        /** [board] as bitboards: [mover]'s discs, then the opponent's. */
        internal fun bits(board: Board, mover: Player): Pair<Long, Long> {
            var p = 0L; var o = 0L
            val own = mover.toCell()
            for (y in 0 until 8) for (x in 0 until 8) {
                val c = board.at(x, y)
                if (c == Cell.EMPTY) continue
                val bit = 1L shl (y * 8 + x)
                if (c == own) p = p or bit else o = o or bit
            }
            return p to o
        }

        internal fun square(m: Move): Int = if (m.isPass) -1 else m.y * 8 + m.x
        internal fun move(sq: Int): Move = if (sq < 0) Move.PASS else Move(sq % 8, sq / 8)
    }
}
