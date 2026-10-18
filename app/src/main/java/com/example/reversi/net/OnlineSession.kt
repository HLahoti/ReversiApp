package com.example.reversi.net

import com.example.reversi.game.Move
import kotlinx.coroutines.flow.StateFlow

/**
 * One active online game, from either side. The view-model drives the game by
 * reading [state] and calling [sendMove] / [leave]; it has no idea whether
 * the bytes end up in Firestore, a WebSocket, or a mock in a test.
 *
 * Lifecycle: created by [OnlineClient.createRoom] or [OnlineClient.joinRoom],
 * torn down with [close]. Every [state] emission is a self-contained snapshot
 * — the UI never needs to diff; it rebuilds from the latest value.
 */
interface OnlineSession {

    val roomCode: RoomCode

    /** Live snapshot of the room: status, assigned seat, agreed move list, errors. */
    val state: StateFlow<SessionState>

    /**
     * Append [move] to the room's authoritative move list.
     *
     * Callers submit moves optimistically — the engine applies them locally
     * right away, and the eventual echo through [state] is deduped by [seq].
     * [seq] MUST equal the index this move will occupy (1-based: the first
     * move is seq 1). The backend may reject out-of-order writes.
     */
    suspend fun sendMove(round: Int, seq: Int, by: com.example.reversi.game.Player, move: Move)

    /**
     * Ask for another game in this room after round [round] has ended. Once
     * both players have asked, the room moves on to round `round + 1` with an
     * empty move list and the players' colours swapped, and every [state]
     * emission from then on carries the new round. A request for a round that has already moved on is ignored.
     */
    suspend fun requestRematch(round: Int)

    /**
     * Forfeit round [round] as this device's colour. Every [state] emission
     * for that round then carries [SessionState.resignedBy], and the room
     * refuses further moves in it. A request for a round that has already
     * moved on, or that someone already resigned, is ignored.
     */
    suspend fun resign(round: Int)

    /**
     * Delete the room and every move under it, then close listeners.
     *
     * Idempotent and best-effort: if the room is already gone (the other
     * player cleaned up first) the call is treated as success. Backend errors
     * other than "not found" are swallowed — the local tracker entry is
     * cleared regardless so we don't loop on an un-deletable room.
     */
    suspend fun finish()

    /** Cancel listeners / release resources without touching the room. Idempotent. */
    fun close()
}
