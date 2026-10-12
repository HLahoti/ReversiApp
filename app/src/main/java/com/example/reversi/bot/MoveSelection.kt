package com.example.reversi.bot

import com.example.reversi.game.Move
import kotlin.math.exp
import kotlin.random.Random

/**
 * How a bot picks among root moves once the search has scored them — the
 * knob that keeps a deterministic engine from replaying the same game.
 *
 * Every root move whose score is within [margin] of the best is a candidate;
 * one is drawn with softmax weight `exp((score - best) / temperature)`, so
 * [margin] caps how bad a pick can be and [temperature] decides how strongly
 * the draw favours the best move. Both fade out with the game: they apply in
 * full for the first [variedPlies] plies, then shrink linearly to zero over
 * the next [taperPlies], after which the bot always plays its best move.
 *
 * Scores are in evaluator units (tanh output, roughly -1..1); a decided
 * endgame scores ±1000, far outside any sensible margin, so a won line is
 * never traded for a lost one.
 */
data class MoveSelection(
    val margin: Float,
    val temperature: Float,
    val variedPlies: Int,
    val taperPlies: Int,
) {
    init {
        require(margin >= 0f) { "margin must be >= 0: $margin" }
        require(temperature >= 0f) { "temperature must be >= 0: $temperature" }
        require(variedPlies >= 0 && taperPlies >= 0) { "plies must be >= 0" }
    }

    /** 1 during the varied opening, falling linearly to 0 over the taper. */
    fun strengthAt(ply: Int): Float = when {
        ply < variedPlies -> 1f
        taperPlies == 0 -> 0f
        else -> ((variedPlies + taperPlies - ply).toFloat() / taperPlies).coerceIn(0f, 1f)
    }

    fun marginAt(ply: Int): Float = margin * strengthAt(ply)
    fun temperatureAt(ply: Int): Float = temperature * strengthAt(ply)

    companion object {
        /** Always the best move, first one on ties — the original behaviour. */
        val DETERMINISTIC = MoveSelection(margin = 0f, temperature = 0f, variedPlies = 0, taperPlies = 0)

        /**
         * Draw a move from the candidates within [margin] of [bestScore].
         * [scores] must be exact for every move that could be a candidate,
         * which [AlphaBetaSearch] guarantees by searching with a lowered root
         * bound.
         */
        fun sample(
            moves: List<Move>, scores: FloatArray, bestScore: Float,
            margin: Float, temperature: Float, random: Random,
        ): Move {
            val floor = bestScore - margin
            val idx = moves.indices.filter { scores[it] >= floor }
            val weights = DoubleArray(idx.size) { exp(((scores[idx[it]] - bestScore) / temperature).toDouble()) }
            var r = random.nextDouble() * weights.sum()
            for (k in idx.indices) {
                r -= weights[k]
                if (r < 0) return moves[idx[k]]
            }
            return moves[idx.last()]
        }
    }
}
