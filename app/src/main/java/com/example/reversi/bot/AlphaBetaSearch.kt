package com.example.reversi.bot

import com.example.reversi.game.Board
import com.example.reversi.game.Cell
import com.example.reversi.game.Move
import com.example.reversi.game.Player
import kotlin.random.Random

/** Leaf evaluator — higher is better for `player`. */
typealias Evaluator = (Board, Player) -> Float

/** Negamax α-β search.  Mirrors the C++ AlphaBetaSearch. */
class AlphaBetaSearch(var depth: Int = 3) {

    /**
     * With [MoveSelection.DETERMINISTIC] this is plain α-β and returns the
     * first best move. Otherwise the root bound is lowered to
     * `bestSoFar - margin`, so every move within the margin of the final best
     * gets an exact score (anything below only gets an upper bound, which is
     * enough to rule it out), and [MoveSelection.sample] picks among them.
     */
    fun chooseMove(
        board: Board, player: Player, eval: Evaluator,
        selection: MoveSelection = MoveSelection.DETERMINISTIC,
        random: Random = Random.Default,
    ): Move {
        val moves = board.legalMoves(player)
        if (moves.isEmpty()) return Move.PASS

        val ply = board.count(Cell.BLACK) + board.count(Cell.WHITE) - 4
        val margin = selection.marginAt(ply)
        val temperature = selection.temperatureAt(ply)

        val scores = FloatArray(moves.size)
        var best = moves.first()
        var bestScore = -INF
        val beta = INF
        for ((i, m) in moves.withIndex()) {
            val alpha = bestScore - margin
            val child = board.apply(m, player).first
            val score = -negamax(child, player.opponent(), depth - 1, -beta, -alpha, eval)
            scores[i] = score
            if (score > bestScore) { bestScore = score; best = m }
        }
        if (margin <= 0f || temperature <= 0f) return best
        return MoveSelection.sample(moves, scores, bestScore, margin, temperature, random)
    }

    private fun negamax(
        board: Board, player: Player, depth: Int,
        a: Float, b: Float, eval: Evaluator,
    ): Float {
        if (board.isTerminal()) return terminalScore(board, player)
        if (depth <= 0) return eval(board, player)
        val moves = board.legalMoves(player)
        if (moves.isEmpty()) {
            return -negamax(board, player.opponent(), depth - 1, -b, -a, eval)
        }
        var best = -INF
        var alpha = a
        for (m in moves) {
            val child = board.apply(m, player).first
            val score = -negamax(child, player.opponent(), depth - 1, -b, -alpha, eval)
            if (score > best) best = score
            if (score > alpha) alpha = score
            if (alpha >= b) break
        }
        return best
    }

    companion object {
        private const val INF = Float.POSITIVE_INFINITY
        private const val WIN = 1000f

        private fun terminalScore(board: Board, player: Player): Float {
            val me = board.count(player.toCell())
            val opp = board.count(player.opponent().toCell())
            val diff = me - opp
            return when {
                diff > 0 -> WIN + diff
                diff < 0 -> -WIN + diff
                else -> 0f
            }
        }
    }
}
