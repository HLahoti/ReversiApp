package com.example.reversi.bot.learning

import com.example.reversi.bot.SearchEngine
import com.example.reversi.game.Board
import com.example.reversi.game.Move
import com.example.reversi.game.Player
import java.io.File

/**
 * A finished game as a learner sees it: every ply from the standard opening
 * position (passes included) and which colour the learning bot played.
 */
data class GameRecord(val moves: List<Move>, val botColor: Player)

/**
 * A pluggable way for a bot to get better from the games it plays. It sits on
 * top of a fixed [SearchEngine] (the bundled network plus α-β search) and can
 * override that engine's choice in positions where it knows better.
 *
 * To try a different self-improvement idea, implement this interface, add a
 * [Learning] entry that builds it, and point an [com.example.reversi.bot.Opponent]
 * at that entry. [LearningBot], persistence and the app stay as they are.
 *
 * [LearningBot] serialises every call, so implementations need not be
 * thread-safe.
 */
interface LearningStrategy {
    /**
     * The move this strategy wants in this position, or null to let [engine]
     * choose as it normally would.
     */
    fun chooseMove(board: Board, player: Player, engine: SearchEngine): Move?

    /** Fold a finished game into what this strategy knows. May be slow. */
    fun learn(game: GameRecord, engine: SearchEngine)

    /** Everything learned so far, as text [import] can restore. */
    fun export(): String

    /**
     * Replace the current knowledge with an earlier [export]. Throws on data
     * it can't read, and must leave the current knowledge untouched if it does.
     */
    fun import(data: String)
}

/** Where a learning bot keeps what it learned between app runs. */
interface KnowledgeStore {
    /** The last [write], or null if nothing has been saved. */
    fun read(): String?
    fun write(data: String)
}

/**
 * A [KnowledgeStore] in a single file. Writes go to a temporary file that
 * then replaces the real one, so a crash mid-write keeps the previous version.
 */
class FileKnowledgeStore(private val file: File) : KnowledgeStore {
    override fun read(): String? = file.takeIf { it.exists() }?.readText()

    override fun write(data: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(data)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
