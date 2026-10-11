package com.example.reversi.bot

import android.content.Context
import com.example.reversi.config.AppConfig
import com.example.reversi.game.Board
import com.example.reversi.game.Move
import com.example.reversi.game.Player

/**
 * The app-facing opponent: a [PositionEvaluator] driven by alpha-beta search.
 * [Bot] knows nothing about how positions are scored — only that it has an
 * evaluator — so swapping the model architecture (MLP, CNN, …) never touches
 * this class or any game/UI code. Building the evaluator from a genome file is
 * [ModelLoader]'s job.
 */
class Bot(
    private val evaluator: PositionEvaluator,
    val searchDepth: Int,
    val id: Int = -1,
) {
    private val search = AlphaBetaSearch(searchDepth)

    fun chooseMove(board: Board, player: Player): Move =
        search.chooseMove(board, player) { b, p -> evaluator.evaluate(b, p) }

    companion object {
        /** Load the bundled model for [opponent]. */
        fun load(ctx: Context, opponent: Opponent): Bot {
            ctx.assets.open(opponent.assetPath).use { stream ->
                val model = ModelLoader.load(stream)
                val depth = (AppConfig.Bot.SEARCH_DEPTH_OVERRIDE ?: model.searchDepth)
                    .coerceAtLeast(1)
                return Bot(model.evaluator, depth, model.id)
            }
        }
    }
}
