package com.example.othello.net

import com.example.othello.game.Move
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
    suspend fun sendMove(seq: Int, by: com.example.othello.game.Player, move: Move)

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
