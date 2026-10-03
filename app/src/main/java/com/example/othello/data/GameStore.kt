package com.example.othello.data

import android.content.Context
import com.example.othello.bot.Difficulty
import com.example.othello.game.Player
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One recorded ply. `isPass` makes `(x, y)` irrelevant. */
@Serializable
data class SavedMove(val x: Int, val y: Int, val isPass: Boolean = false)

/**
 * On-disk representation of a paused game. `humanColor` is null for a
 * two-player (pass-and-play) save and `"BLACK"`/`"WHITE"` for a vs-bot save.
 * `difficulty` is the [Difficulty] enum name (null for two-player saves and
 * for older vs-bot saves written before the picker existed).
 * The move list replays from the standard Othello opening position.
 */
@Serializable
data class SavedGame(
    val humanColor: String? = null,
    val difficulty: String? = null,
    val moves: List<SavedMove>,
)

enum class SavedMode { TWO_PLAYER, VS_BOT }

/**
 * File-backed store for the two per-mode game saves. Writes are overwrites;
 * clears delete the file so [has] reports false. Corrupt files are deleted
 * on read and treated as "no save".
 */
class GameStore(context: Context) {
    private val dir = File(context.filesDir, "saves").apply { mkdirs() }
    private val twoPlayerFile = File(dir, "two_player.json")
    private val vsBotFile = File(dir, "vs_bot.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun has(mode: SavedMode): Boolean = fileFor(mode).exists()

    fun load(mode: SavedMode): SavedGame? {
        val f = fileFor(mode)
        if (!f.exists()) return null
        return try {
            json.decodeFromString(SavedGame.serializer(), f.readText())
        } catch (t: Throwable) {
            f.delete()
            null
        }
    }

    fun writeTwoPlayer(moves: List<SavedMove>) {
        twoPlayerFile.writeText(
            json.encodeToString(SavedGame.serializer(), SavedGame(humanColor = null, moves = moves))
        )
    }

    fun writeVsBot(moves: List<SavedMove>, humanColor: Player, difficulty: Difficulty?) {
        vsBotFile.writeText(
            json.encodeToString(
                SavedGame.serializer(),
                SavedGame(
                    humanColor = humanColor.name,
                    difficulty = difficulty?.name,
                    moves = moves,
                ),
            )
        )
    }

    fun clear(mode: SavedMode) {
        fileFor(mode).delete()
    }

    private fun fileFor(mode: SavedMode): File = when (mode) {
        SavedMode.TWO_PLAYER -> twoPlayerFile
        SavedMode.VS_BOT -> vsBotFile
    }
}
