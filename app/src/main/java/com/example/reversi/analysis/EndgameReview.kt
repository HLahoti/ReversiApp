package com.example.reversi.analysis

import com.example.reversi.config.AppConfig
import com.example.reversi.game.GameState
import com.example.reversi.game.Move
import com.example.reversi.game.Player
import kotlin.math.tanh

/** How bad a move was. Moves that don't earn one aren't marked. */
enum class Grade(val suffix: String) {
    MISTAKE("?"),
    BLUNDER("??"),
}

/**
 * The verdict on one move in the solved endgame. Scores are exact final disc
 * differences from [mover]'s side under perfect play from then on: [played]
 * after the move that was made, [best] after the best one available.
 */
data class MoveVerdict(
    val ply: Int,
    val mover: Player,
    val move: Move,
    val played: Int,
    val bestMove: Move,
    val best: Int,
    val grade: Grade?,
) {
    /** Discs given away compared with the best move. */
    val loss: Int get() = best - played
}

/**
 * Every move in a finished game played with [AppConfig.Analysis.MAX_EMPTIES]
 * or fewer squares empty, solved exactly and graded. Earlier moves, forced
 * moves and passes get no verdict.
 */
class EndgameReview(val verdicts: Map<Int, MoveVerdict>) {

    /** The ply where solving started, or null if the game never got that far. */
    val firstPly: Int? get() = verdicts.keys.minOrNull()

    fun count(mover: Player, grade: Grade) = verdicts.values.count { it.mover == mover && it.grade == grade }

    companion object {
        /**
         * Review [moves], a whole game from the opening position. [checkAbort]
         * is passed to the solver, to cancel a review that is no longer wanted.
         */
        fun of(
            moves: List<Move>,
            maxEmpties: Int = AppConfig.Analysis.MAX_EMPTIES,
            checkAbort: () -> Unit = {},
        ): EndgameReview {
            val solver = EndgameSolver(checkAbort)
            val verdicts = HashMap<Int, MoveVerdict>()
            var state = GameState()
            for ((ply, move) in moves.withIndex()) {
                val empties = 64 - state.blackCount - state.whiteCount
                if (!move.isPass && empties <= maxEmpties && state.legalMoves.size > 1) {
                    verdicts[ply] = judge(solver, state, move, ply)
                }
                state = state.play(move) ?: break
            }
            return EndgameReview(verdicts)
        }

        /**
         * Solve the move played exactly, then look for anything better with
         * that as the bar: the alternatives only need an exact score if they
         * beat it, which keeps most of them cheap.
         */
        private fun judge(solver: EndgameSolver, state: GameState, move: Move, ply: Int): MoveVerdict {
            val (p, o) = EndgameSolver.bits(state.board, state.toMove)
            val max = EndgameSolver.MAX
            val played = solver.scoreAfter(p, o, EndgameSolver.square(move), -max, max)
            var best = played
            var bestMove = move
            for (alt in state.legalMoves) {
                if (alt == move) continue
                val score = solver.scoreAfter(p, o, EndgameSolver.square(alt), best, max)
                if (score > best) { best = score; bestMove = alt }
            }
            return MoveVerdict(ply, state.toMove, move, played, bestMove, best, grade(played, best))
        }

        /**
         * Throwing away a win is a blunder, and any other worse result a
         * mistake at least. Otherwise moves are graded by how much of the
         * game's worth they give away ([stake]), not by raw discs.
         */
        fun grade(played: Int, best: Int): Grade? {
            val cfg = AppConfig.Analysis
            val given = stake(best) - stake(played)
            val drop = Integer.signum(best) - Integer.signum(played)
            return when {
                drop >= 2 || given >= cfg.BLUNDER_STAKE -> Grade.BLUNDER
                drop == 1 || given >= cfg.MISTAKE_STAKE -> Grade.MISTAKE
                else -> null
            }
        }

        /** What a final margin of [discs] is worth, from -1 to 1. */
        fun stake(discs: Int): Float = tanh(discs / AppConfig.Analysis.STAKE_SCALE_DISCS)
    }
}
