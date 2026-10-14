package com.example.reversi.bot

import android.content.Context
import com.example.reversi.bot.learning.FileKnowledgeStore
import com.example.reversi.bot.learning.LearningBot
import java.io.File

/**
 * Builds the [BotPlayer] for an [Opponent]: the plain [Bot] for a fixed
 * opponent, or that engine wrapped in a [LearningBot] for one with
 * [Opponent.learning].
 *
 * A learning bot exists once per process, so every game and every screen
 * shares the same up-to-date knowledge, and learning from the last game is
 * never raced by a fresh copy loaded from disk.
 */
object Bots {
    private val learners = HashMap<Opponent, LearningBot>()

    fun load(ctx: Context, opponent: Opponent): BotPlayer {
        val learning = opponent.learning ?: return Bot.load(ctx, opponent)
        synchronized(learners) {
            return learners.getOrPut(opponent) {
                val file = File(
                    ctx.filesDir,
                    "learning/${opponent.name.lowercase()}_${learning.name.lowercase()}.json",
                )
                LearningBot(Bot.load(ctx, opponent), learning.create(), FileKnowledgeStore(file))
            }
        }
    }
}
