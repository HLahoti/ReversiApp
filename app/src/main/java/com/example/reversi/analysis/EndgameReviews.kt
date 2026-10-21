package com.example.reversi.analysis

import com.example.reversi.game.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Reviews of recently replayed games, kept for the life of the process so
 * stepping back into a replay doesn't solve its endgame again.
 */
object EndgameReviews {
    private const val KEEP = 16
    private val cache = object : LinkedHashMap<List<Move>, EndgameReview>(KEEP, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<Move>, EndgameReview>) = size > KEEP
    }

    /** The review of [moves], solved off the main thread unless cached. Cancellable. */
    suspend fun of(moves: List<Move>): EndgameReview {
        synchronized(cache) { cache[moves] }?.let { return it }
        val review = withContext(Dispatchers.Default) {
            val ctx = coroutineContext
            EndgameReview.of(moves, checkAbort = { ctx.ensureActive() })
        }
        synchronized(cache) { cache[moves] = review }
        return review
    }
}
