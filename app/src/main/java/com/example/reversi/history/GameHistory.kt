package com.example.reversi.history

import com.example.reversi.game.Player
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

enum class HistoryMode { BOT, ONLINE }

enum class HistoryStatus {
    /** Still being played, or paused and resumable (a saved vs-bot game). */
    IN_PROGRESS,
    /** Ended on the board or by resignation; [HistoryEntry.outcome] says how. */
    COMPLETED,
    /** Stopped before the end and can't be resumed: reset, left, or the opponent left. */
    ABANDONED,
}

enum class HistoryOutcome { WIN, LOSS, DRAW }

/**
 * One game in the history, seen from the local player's side. The local
 * player is stored only by colour; the screen names them through
 * [com.example.reversi.profile.PlayerProfile.selfLabel], so an account-backed
 * profile can rename "You" without touching saved entries.
 */
@Serializable
data class HistoryEntry(
    val id: String,
    val mode: HistoryMode,
    val myColor: Player,
    /** The bot's name, or the online opponent's display name if they set one. */
    val opponentName: String?,
    /** [com.example.reversi.bot.Opponent] enum name for bot games, stable across renames. */
    val botId: String? = null,
    val status: HistoryStatus,
    /** Set once [status] is [HistoryStatus.COMPLETED]. */
    val outcome: HistoryOutcome? = null,
    /** The colour that resigned, when the game ended that way. */
    val resignedBy: Player? = null,
    val blackCount: Int,
    val whiteCount: Int,
    val moveCount: Int,
    val startedAt: Long,
    /** When the last move (or resignation) was played. */
    val updatedAt: Long,
    /** When it was completed or abandoned; null while in progress. */
    val endedAt: Long? = null,
    /**
     * Every ply of a completed game, in [HistoryMoves] form, for replaying
     * it. Null for unfinished games and for games recorded before moves were
     * kept.
     */
    val moves: String? = null,
    /** Hints the player was shown in this game (vs-bot only). */
    val hintsUsed: Int = 0,
    /** Times the player rewound and played a different move (vs-bot only). */
    val takebacks: Int = 0,
) {
    val opponentColor: Player get() = myColor.opponent()

    /** When the game finished, or was last played if it hasn't. */
    val lastActivity: Long get() = endedAt ?: updatedAt
}

/**
 * Where game history lives. Kept on the device today ([LocalGameHistory]);
 * syncing it to an account later means a sibling implementation and a new
 * line in [HistoryRegistry.install]'s caller. Nothing else depends on how
 * entries are stored.
 */
interface GameHistory {

    /** Every entry, most recent activity first. */
    val entries: StateFlow<List<HistoryEntry>>

    /** Add [entry], or update the one with its id (keeping that one's start time). */
    fun record(entry: HistoryEntry)

    /** Mark entry [id] abandoned now, if it is still in progress. */
    fun abandon(id: String)

    /**
     * Mark every in-progress entry not in [resumable] abandoned, as of its
     * last move. Run once at startup: anything else in progress belonged to a
     * game that died with the previous process.
     */
    fun abandonAllExcept(resumable: Set<String>)
}

/** Process-wide holder for the single [GameHistory], like [com.example.reversi.net.OnlineRegistry]. */
object HistoryRegistry {
    @Volatile private var instance: GameHistory? = null

    /**
     * Install [create]'s history unless one already is, and report whether
     * it ran. Activity recreation calls this again; the history (and the
     * games it is tracking) must survive that.
     */
    @Synchronized
    fun installIfAbsent(create: () -> GameHistory): Boolean {
        if (instance != null) return false
        instance = create()
        return true
    }

    val current: GameHistory
        get() = instance ?: error(
            "GameHistory not installed. Call HistoryRegistry.installIfAbsent() from MainActivity.",
        )
}
