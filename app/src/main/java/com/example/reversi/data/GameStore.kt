package com.example.reversi.data

import android.content.Context
import com.example.reversi.bot.Opponent
import com.example.reversi.game.Player
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One recorded ply. `isPass` makes `(x, y)` irrelevant. */
@Serializable
data class SavedMove(val x: Int, val y: Int, val isPass: Boolean = false)

/**
 * On-disk representation of a paused game. `humanColor` is null for a
 * two-player (pass-and-play) save and `"BLACK"`/`"WHITE"` for a vs-bot save.
 * `difficulty` is the [Opponent] enum name — the key predates the named
 * roster and is kept so older saves still parse; [Opponent.fromSaved] maps the
 * legacy EASY/MEDIUM/HARD values. Null for two-player saves and for vs-bot
 * saves written before the picker existed.
 * The move list replays from the standard Reversi opening position.
 */
@Serializable
data class SavedGame(
    val humanColor: String? = null,
    val difficulty: String? = null,
    val moves: List<SavedMove>,
)

enum class SavedMode { TWO_PLAYER, VS_BOT }

/**
 * File-backed store for game saves: one two-player slot plus one vs-bot slot
 * per [Opponent], so pausing a game against one bot never overwrites a game
 * against another. Vs-bot calls take the opponent; it is ignored for
 * [SavedMode.TWO_PLAYER]. Writes are overwrites; clears delete the file so
 * [has] reports false. Corrupt files are deleted on read and treated as "no
 * save".
 */
class GameStore(private val dir: File) {
    constructor(context: Context) : this(File(context.filesDir, "saves"))

    private val twoPlayerFile = File(dir, "two_player.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        dir.mkdirs()
        migrateSharedVsBotSave()
    }

    fun has(mode: SavedMode, opponent: Opponent? = null): Boolean = fileFor(mode, opponent).exists()

    /** Opponents that currently have a paused game. */
    fun opponentsWithSaves(): Set<Opponent> =
        Opponent.entries.filterTo(mutableSetOf()) { vsBotFile(it).exists() }

    fun load(mode: SavedMode, opponent: Opponent? = null): SavedGame? = read(fileFor(mode, opponent))

    fun writeTwoPlayer(moves: List<SavedMove>) {
        twoPlayerFile.writeText(
            json.encodeToString(SavedGame.serializer(), SavedGame(humanColor = null, moves = moves))
        )
    }

    fun writeVsBot(moves: List<SavedMove>, humanColor: Player, opponent: Opponent) {
        vsBotFile(opponent).writeText(
            json.encodeToString(
                SavedGame.serializer(),
                SavedGame(
                    humanColor = humanColor.name,
                    difficulty = opponent.name,
                    moves = moves,
                ),
            )
        )
    }

    fun clear(mode: SavedMode, opponent: Opponent? = null) {
        fileFor(mode, opponent).delete()
    }

    private fun read(f: File): SavedGame? {
        if (!f.exists()) return null
        return try {
            json.decodeFromString(SavedGame.serializer(), f.readText())
        } catch (t: Throwable) {
            f.delete()
            null
        }
    }

    private fun fileFor(mode: SavedMode, opponent: Opponent?): File = when (mode) {
        SavedMode.TWO_PLAYER -> twoPlayerFile
        SavedMode.VS_BOT -> vsBotFile(requireNotNull(opponent) { "vs-bot save needs an opponent" })
    }

    /** Keyed by the enum name, which is already persisted and kept stable. */
    private fun vsBotFile(o: Opponent): File = File(dir, "vs_bot_${o.name.lowercase()}.json")

    /**
     * Before per-bot slots, every vs-bot game shared `vs_bot.json`. Move it
     * into the slot of the bot it was played against (the default bot for
     * saves older than the picker) unless that slot is already taken.
     */
    private fun migrateSharedVsBotSave() {
        val legacy = File(dir, "vs_bot.json")
        if (!legacy.exists()) return
        val saved = read(legacy) ?: return
        val target = vsBotFile(Opponent.fromSaved(saved.difficulty) ?: Opponent.DEFAULT)
        if (!target.exists()) legacy.renameTo(target)
        legacy.delete()
    }
}
