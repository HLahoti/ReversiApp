package com.example.reversi.bot.learning

import com.example.reversi.bot.BotPlayer
import com.example.reversi.bot.SearchEngine
import com.example.reversi.game.Board
import com.example.reversi.game.Move
import com.example.reversi.game.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * A [BotPlayer] that gets better with play: [strategy] answers where it has
 * something to say and [engine] plays everywhere else. After each finished
 * game the strategy learns from it and the result is saved to [store], so the
 * bot keeps improving across app runs.
 *
 * Every strategy call holds one lock. If a move is requested while the last
 * game is still being learned, it waits for learning to finish, so the bot
 * never plays from half-learned knowledge.
 */
class LearningBot(
    private val engine: SearchEngine,
    private val strategy: LearningStrategy,
    private val store: KnowledgeStore,
) : BotPlayer {
    private val lock = Any()

    init {
        // Unreadable saved knowledge (a format change, a corrupt file) is
        // dropped: the bot starts fresh and the next save overwrites it.
        runCatching { store.read()?.let(strategy::import) }
    }

    override fun chooseMove(board: Board, player: Player): Move =
        synchronized(lock) { strategy.chooseMove(board, player, engine) }
            ?: engine.chooseMove(board, player)

    override fun onGameFinished(game: GameRecord) {
        synchronized(lock) {
            strategy.learn(game, engine)
            store.write(strategy.export())
        }
    }
}

/**
 * Runs learning after a game. Not tied to a screen, so learning finishes even
 * if the player leaves the game right after it ends.
 */
object LearningScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)
