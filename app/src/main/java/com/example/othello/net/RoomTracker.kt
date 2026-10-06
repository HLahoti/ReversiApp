package com.example.othello.net

import android.content.Context

/**
 * Persistent per-device set of rooms this player has created or joined.
 *
 * The entry's lifetime is "until cleanup is attempted" — a code goes in when
 * a session is created/joined and comes out when a successful (or any)
 * cleanup attempt is made. Survives app restarts so an orphaned room from a
 * previous session can be swept the next time the app opens.
 */
class RoomTracker(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun add(code: RoomCode) {
        synchronized(this) {
            val next = (current() + code.value).toSet()
            prefs.edit().putStringSet(KEY, next).apply()
        }
    }

    fun remove(code: RoomCode) {
        synchronized(this) {
            val next = (current() - code.value).toSet()
            prefs.edit().putStringSet(KEY, next).apply()
        }
    }

    fun all(): List<RoomCode> = synchronized(this) {
        current().map { RoomCode(it) }
    }

    fun clearAll() {
        synchronized(this) {
            prefs.edit().remove(KEY).apply()
        }
    }

    /** Defensive copy — SharedPreferences hands back a shared mutable set. */
    private fun current(): Set<String> = prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    private companion object {
        const val PREFS = "othello.online.rooms"
        const val KEY = "my_rooms"
    }
}
