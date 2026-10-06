package com.example.othello.net

import com.example.othello.game.Player
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-wide handoff slot: a live [OnlineSession] cannot be serialised
 * through a navigation route, so the lobby parks it here under a short token
 * and the game screen picks it up on arrival.
 *
 * Tokens are single-use. Taking a token both reads and removes the entry.
 */
object SessionHandoff {

    data class Handoff(val session: OnlineSession, val localColor: Player)

    private val slots = ConcurrentHashMap<String, Handoff>()
    private val nextId = AtomicLong(1)

    fun park(session: OnlineSession, localColor: Player): String {
        val token = "handoff-${nextId.getAndIncrement()}"
        slots[token] = Handoff(session, localColor)
        return token
    }

    fun take(token: String): Handoff? = slots.remove(token)

    /** Discard a parked session without consuming it (e.g. back-pressed in nav). */
    fun discard(token: String) {
        slots.remove(token)?.session?.close()
    }
}
