package com.example.othello.net.firebase

/*
 * Firebase setup checklist (one-time, per app):
 *   1. Create a Firebase project at https://console.firebase.google.com.
 *   2. Add an Android app with applicationId "com.example.othello"
 *      and drop the generated google-services.json into app/.
 *   3. In the console, enable:
 *          Authentication → Sign-in method → Anonymous
 *          Firestore Database → create in production mode
 *   4. Paste these starter Firestore security rules (friends-only; tightens
 *      writes to the two seated players and relies on client turn logic):
 *
 *          rules_version = '2';
 *          service cloud.firestore {
 *            match /databases/{db}/documents {
 *              match /rooms/{code} {
 *                allow read: if request.auth != null;
 *                allow create: if request.auth != null
 *                              && request.resource.data.hostUid == request.auth.uid
 *                              && request.resource.data.status == 'waiting';
 *                allow update: if request.auth != null
 *                              && (resource.data.hostUid == request.auth.uid
 *                                  || resource.data.guestUid == request.auth.uid
 *                                  || (resource.data.status == 'waiting'
 *                                      && request.resource.data.guestUid == request.auth.uid));
 *                allow delete: if request.auth != null
 *                              && (resource.data.hostUid == request.auth.uid
 *                                  || resource.data.guestUid == request.auth.uid);
 *                match /moves/{seq} {
 *                  allow read: if request.auth != null;
 *                  allow create: if request.auth != null
 *                                && (get(/databases/$(db)/documents/rooms/$(code)).data.hostUid == request.auth.uid
 *                                    || get(/databases/$(db)/documents/rooms/$(code)).data.guestUid == request.auth.uid);
 *                  allow delete: if request.auth != null
 *                                && (get(/databases/$(db)/documents/rooms/$(code)).data.hostUid == request.auth.uid
 *                                    || get(/databases/$(db)/documents/rooms/$(code)).data.guestUid == request.auth.uid);
 *                }
 *              }
 *            }
 *          }
 */

import android.content.Context
import com.example.othello.game.Player
import com.example.othello.net.OnlineClient
import com.example.othello.net.OnlineError
import com.example.othello.net.OnlineSession
import com.example.othello.net.PlayerId
import com.example.othello.net.PlayerIdentity
import com.example.othello.net.RoomCode
import com.example.othello.net.RoomCodes
import com.example.othello.net.RoomTracker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random
import kotlinx.coroutines.tasks.await

/**
 * Firebase-backed [OnlineClient]. Anonymous Auth for identity + Firestore for
 * room / move storage. This is the ONLY file that imports Firebase SDK types
 * besides [FirebaseOnlineSession] — swapping to a custom WebSocket means
 * writing a sibling pair and changing one line in MainActivity.
 */
class FirebaseOnlineClient(
    context: Context,
    authProvider: () -> FirebaseAuth = { FirebaseAuth.getInstance() },
    dbProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : OnlineClient {

    private val tracker = RoomTracker(context)

    // Lazy so the app still boots when google-services.json is absent —
    // the error only surfaces when the user actually taps "Play online".
    private val auth: FirebaseAuth by lazy {
        try { authProvider() } catch (t: Throwable) {
            throw OnlineError.Backend(
                "Firebase is not configured. Add google-services.json to app/ " +
                    "and rebuild to enable online play.",
                t,
            )
        }
    }
    private val db: FirebaseFirestore by lazy {
        try { dbProvider() } catch (t: Throwable) {
            throw OnlineError.Backend(
                "Firebase is not configured. Add google-services.json to app/ " +
                    "and rebuild to enable online play.",
                t,
            )
        }
    }

    override suspend fun ensureSignedIn(displayName: String?): PlayerIdentity {
        val current = auth.currentUser
        val user = current ?: try {
            auth.signInAnonymously().await().user
                ?: throw OnlineError.Backend("Anonymous sign-in returned no user.")
        } catch (t: Throwable) {
            throw t.asOnlineError()
        }
        return PlayerIdentity(
            id = PlayerId(user.uid),
            displayName = displayName ?: user.displayName,
        )
    }

    override suspend fun createRoom(hostColor: Player?): OnlineSession {
        val me = ensureSignedIn()
        val chosen = hostColor ?: if (Random.nextBoolean()) Player.BLACK else Player.WHITE

        val code = pickUnusedCode()
        val doc = db.collection(FS.COL_ROOMS).document(code.value)

        val payload = mapOf(
            FS.F_STATUS to FS.S_WAITING,
            FS.F_CREATED_AT to FieldValue.serverTimestamp(),
            FS.F_HOST_UID to me.id.value,
            FS.F_HOST_COLOR to chosen.name,
            FS.F_HOST_NAME to me.displayName,
            FS.F_GUEST_UID to null,
            FS.F_GUEST_NAME to null,
            FS.F_MOVE_COUNT to 0,
            FS.F_EXPIRES_AT to FS.ttlFromNow(),
        )
        try {
            doc.set(payload).await()
        } catch (t: Throwable) {
            throw t.asOnlineError()
        }

        tracker.add(code)
        return FirebaseOnlineSession(db, code, me, tracker)
    }

    override suspend fun joinRoom(code: RoomCode): OnlineSession {
        val me = ensureSignedIn()
        val doc = db.collection(FS.COL_ROOMS).document(code.value)

        try {
            db.runTransaction { tx ->
                val snap = tx.get(doc)
                if (!snap.exists()) throw OnlineError.RoomNotFound(code)
                val status = snap.getString(FS.F_STATUS)
                val hostUid = snap.getString(FS.F_HOST_UID)
                val guestUid = snap.getString(FS.F_GUEST_UID)
                if (hostUid == me.id.value) {
                    // Rejoining your own room is fine, no mutation needed.
                    return@runTransaction null
                }
                if (guestUid != null && guestUid != me.id.value) {
                    throw OnlineError.RoomFull(code)
                }
                if (status != FS.S_WAITING && status != FS.S_ACTIVE) {
                    throw OnlineError.Backend("Room is $status and cannot be joined.")
                }
                tx.update(
                    doc,
                    mapOf(
                        FS.F_GUEST_UID to me.id.value,
                        FS.F_GUEST_NAME to me.displayName,
                        FS.F_STATUS to FS.S_ACTIVE,
                        FS.F_EXPIRES_AT to FS.ttlFromNow(),
                    ),
                )
                null
            }.await()
        } catch (t: Throwable) {
            throw t.asOnlineError()
        }

        tracker.add(code)
        return FirebaseOnlineSession(db, code, me, tracker)
    }

    override suspend fun sweepTrackedRooms() {
        // Need to be signed in to delete anything; if that itself fails, bail
        // without clearing the tracker — we'll try again next launch.
        try {
            ensureSignedIn()
        } catch (_: Throwable) {
            return
        }
        for (code in tracker.all()) {
            runCatching { deleteRoomAndMoves(code) }
            // Clear the entry regardless of outcome. A failure here almost
            // always means "the other player cleaned up first" (NOT_FOUND)
            // or "rules changed and we can't own this any more"; either way,
            // retrying forever would just waste reads.
            tracker.remove(code)
        }
    }

    /**
     * Delete every move doc under a room, then the room doc itself. Idempotent:
     * missing docs are not an error. Package-private so [FirebaseOnlineSession]
     * can reuse it from its own [OnlineSession.finish].
     */
    internal suspend fun deleteRoomAndMoves(code: RoomCode) {
        val roomDoc = db.collection(FS.COL_ROOMS).document(code.value)
        val movesCol = roomDoc.collection(FS.SUB_MOVES)
        // Firestore has no "delete subcollection" op; list, batch-delete.
        val moves = try {
            movesCol.get().await().documents
        } catch (_: Throwable) {
            emptyList()
        }
        if (moves.isNotEmpty()) {
            try {
                db.runBatch { batch -> moves.forEach { batch.delete(it.reference) } }.await()
            } catch (_: Throwable) {
                // Fall through; attempt room delete anyway.
            }
        }
        runCatching { roomDoc.delete().await() }
    }

    /** Spin until we find an unused code. In practice succeeds on the first try. */
    private suspend fun pickUnusedCode(): RoomCode {
        repeat(8) {
            val code = RoomCodes.generate()
            val exists = try {
                db.collection(FS.COL_ROOMS).document(code.value).get().await().exists()
            } catch (t: Throwable) {
                throw t.asOnlineError()
            }
            if (!exists) return code
        }
        throw OnlineError.Backend("Could not allocate a free room code after 8 tries.")
    }
}

/** Translate SDK exceptions into our backend-neutral taxonomy. */
internal fun Throwable.asOnlineError(): OnlineError = when (this) {
    is OnlineError -> this
    is com.google.firebase.firestore.FirebaseFirestoreException ->
        OnlineError.Backend(this.message ?: "Firestore error", this)
    is com.google.firebase.FirebaseNetworkException ->
        OnlineError.Network(this)
    is com.google.firebase.auth.FirebaseAuthException ->
        OnlineError.NotSignedIn()
    else -> OnlineError.Backend(this.message ?: this::class.java.simpleName, this)
}
