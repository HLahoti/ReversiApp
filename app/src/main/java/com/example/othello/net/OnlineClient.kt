package com.example.othello.net

import com.example.othello.game.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Entry point for the online layer. One instance per app, lives in the
 * [OnlineRegistry] singleton. Everything the lobby / view-model touches is
 * declared here — the Firebase implementation drops in behind the interface
 * and a WebSocket implementation will swap in the same way.
 */
interface OnlineClient {

    /**
     * Ensure the local device has an identity (anonymous auth under the hood
     * for the Firebase impl; a locally-persisted UUID + token exchange for a
     * custom server). Must be called before create/join.
     */
    suspend fun ensureSignedIn(displayName: String? = null): PlayerIdentity

    /**
     * Create a brand-new room with this device as host. Returns a live
     * session already in [SessionStatus.WAITING]; it will flip to
     * [SessionStatus.ACTIVE] once a second player joins.
     *
     * @param hostColor the colour the host wants to play (null = random).
     */
    suspend fun createRoom(hostColor: Player? = null): OnlineSession

    /**
     * Join an existing room by its short code. The returned session is
     * immediately [SessionStatus.ACTIVE] on the happy path.
     */
    suspend fun joinRoom(code: RoomCode): OnlineSession

    /**
     * Best-effort cleanup of every room this device has tracked but hasn't
     * cleaned up. Each tracker entry is attempted once and cleared regardless
     * of outcome — a room already deleted by the other player, or one we
     * can no longer delete due to a rules change, is simply dropped.
     *
     * Safe to call on app launch; runs in whatever scope the caller supplies.
     */
    suspend fun sweepTrackedRooms()
}

/**
 * Process-wide holder for the single [OnlineClient] instance. The MainActivity
 * writes the Firebase impl in here on launch; everything else reads from
 * [current]. Swapping to a different backend later is a one-line change at
 * the write site.
 */
object OnlineRegistry {
    @Volatile private var instance: OnlineClient? = null

    fun install(client: OnlineClient) {
        instance = client
    }

    val current: OnlineClient
        get() = instance ?: error(
            "OnlineClient not installed. Call OnlineRegistry.install() from MainActivity.",
        )
}

/**
 * Application-wide scope for fire-and-forget online cleanup work that must
 * outlive the ViewModel that triggered it (so a `finish()` kicked off during
 * a back-navigation isn't cancelled when the VM is cleared mid-request).
 *
 * SupervisorJob so one failed cleanup doesn't poison the next one.
 */
object OnlineScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)

