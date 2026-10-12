package com.example.reversi.bot

import android.content.Context
import com.example.reversi.config.AppConfig
import com.example.reversi.game.Board
import com.example.reversi.game.Move
import com.example.reversi.game.Player
import kotlin.random.Random

/**
 * The app-facing opponent: a [PositionEvaluator] driven by alpha-beta search.
 * [Bot] knows nothing about how positions are scored — only that it has an
 * evaluator — so swapping the model architecture (MLP, CNN, …) never touches
 * this class or any game/UI code. Building the evaluator from a genome file is
 * [ModelLoader]'s job.
 *
 * [selection] controls move variety; the default is fully deterministic, which
 * tests (e.g. the C++ parity test) rely on. [Bot.load] applies the per-opponent
 * settings from [AppConfig.Bot].
 */
class Bot(
    private val evaluator: PositionEvaluator,
    val searchDepth: Int,
    val id: Int = -1,
    val selection: MoveSelection = MoveSelection.DETERMINISTIC,
    private val random: Random = Random.Default,
) {
    private val search = AlphaBetaSearch(searchDepth)

    fun chooseMove(board: Board, player: Player): Move =
        search.chooseMove(board, player, { b, p -> evaluator.evaluate(b, p) }, selection, random)

    companion object {
        /** Load the bundled model for [opponent]. */
        fun load(ctx: Context, opponent: Opponent): Bot {
            ctx.assets.open(opponent.assetPath).use { stream ->
                val model = ModelLoader.load(stream)
                val depth = (AppConfig.Bot.SEARCH_DEPTH_OVERRIDE ?: model.searchDepth)
                    .coerceAtLeast(1)
                val selection = AppConfig.Bot.VARIETY
                    .takeIf { AppConfig.Bot.VARIETY_ENABLED }
                    ?.get(opponent) ?: MoveSelection.DETERMINISTIC
                val random = AppConfig.Bot.VARIETY_SEED?.let(::Random) ?: Random.Default
                return Bot(model.evaluator, depth, model.id, selection, random)
            }
        }
    }
}
