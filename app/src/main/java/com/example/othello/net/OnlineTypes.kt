package com.example.othello.net

import com.example.othello.game.Move
import com.example.othello.game.Player

/**
 * Human-friendly room identifier shared out-of-band (e.g. "KX7P2A").
 * Short, uppercase, uses an unambiguous alphabet (no 0/O/1/I).
 */
@JvmInline
value class RoomCode(val value: String) {
    override fun toString(): String = value
}

/**
 * Opaque identifier for a player across a session. Backend-specific under the
 * hood (Firebase UID today, server-issued token tomorrow); the UI only sees
 * strings and never pulls the type apart.
 */
@JvmInline
value class PlayerId(val value: String) {
    override fun toString(): String = value
}

data class PlayerIdentity(
    val id: PlayerId,
    val displayName: String?,
)

/**
 * Lifecycle a room goes through. The UI only waits on these transitions; it
 * does not know how they are stored on the wire.
 */
enum class SessionStatus {
    /** Host created the room, waiting for a second player. */
    WAITING,
    /** Both players present, game in progress. */
    ACTIVE,
    /** Game ended by terminal position or resignation. */
    FINISHED,
    /** One side left or the room was torn down before completion. */
    ABANDONED,
}

/**
 * A move as it travels across the wire. Carries its own ordinal so late
 * joiners / reconnects can replay deterministically. Backend-neutral — any
 * transport serialises these the same way.
 */
data class RemoteMove(
    val seq: Int,
    val by: Player,
    val x: Int,
    val y: Int,
    val isPass: Boolean,
) {
    fun toMove(): Move = if (isPass) Move.PASS else Move(x, y)

    companion object {
        fun from(seq: Int, by: Player, move: Move): RemoteMove =
            RemoteMove(seq = seq, by = by, x = move.x, y = move.y, isPass = move.isPass)
    }
}

/**
 * The complete, replayable view of a room at one moment. The session emits a
 * new value whenever anything in this struct changes — the view-model never
 * needs to subscribe to multiple streams.
 */
data class SessionState(
    val status: SessionStatus = SessionStatus.WAITING,
    val moves: List<RemoteMove> = emptyList(),
    /** This device's assigned colour, once the seat has been chosen. */
    val myColor: Player? = null,
    val opponent: PlayerIdentity? = null,
    /** Human-readable error, surfaced to the UI; cleared on next transition. */
    val error: String? = null,
) {
    val isJoinable: Boolean get() = status == SessionStatus.WAITING && opponent == null
    val isPlayable: Boolean get() = status == SessionStatus.ACTIVE
}

/**
 * Backend-neutral failure signal. Keep the categories coarse — any transport
 * can map its own error codes into these without leaking its SDK types.
 */
sealed class OnlineError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class RoomNotFound(code: RoomCode) : OnlineError("Room $code was not found.")
    class RoomFull(code: RoomCode) : OnlineError("Room $code already has two players.")
    class InvalidCode(code: String) : OnlineError("'$code' is not a valid room code.")
    class NotSignedIn : OnlineError("You must sign in before creating or joining a room.")
    class Network(cause: Throwable) : OnlineError("Network problem: ${cause.message}", cause)
    class Backend(message: String, cause: Throwable? = null) : OnlineError(message, cause)
}
