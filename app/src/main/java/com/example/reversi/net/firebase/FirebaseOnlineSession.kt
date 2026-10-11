package com.example.reversi.net.firebase

import com.example.reversi.game.Move
import com.example.reversi.game.Player
import com.example.reversi.net.OnlineError
import com.example.reversi.net.OnlineSession
import com.example.reversi.net.PlayerId
import com.example.reversi.net.PlayerIdentity
import com.example.reversi.net.RemoteMove
import com.example.reversi.net.RoomCode
import com.example.reversi.net.RoomTracker
import com.example.reversi.net.SessionState
import com.example.reversi.net.SessionStatus
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await

/**
 * A single live room, watched over two Firestore listeners (one on the room
 * doc for status/seating, one on the /moves subcollection for the game log).
 * Both feed into [state] so the view-model has a single source of truth.
 *
 * NOTE: this file knows about Firestore. Nothing else in the app does — all
 * other code consumes it through the [OnlineSession] interface.
 */
internal class FirebaseOnlineSession(
    private val db: FirebaseFirestore,
    override val roomCode: RoomCode,
    private val me: PlayerIdentity,
    private val tracker: RoomTracker,
) : OnlineSession {

    private val _state = MutableStateFlow(SessionState())
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    private val roomDoc = db.collection(FS.COL_ROOMS).document(roomCode.value)
    private val movesCol = roomDoc.collection(FS.SUB_MOVES)

    private val roomReg: ListenerRegistration
    private val movesReg: ListenerRegistration

    /**
     * Every move doc in the room, tagged with its round. The two listeners
     * can deliver in either order around a rematch, so [state] is always
     * rebuilt from this plus the latest round rather than trusting either
     * snapshot alone.
     */
    private var allMoves: List<Pair<Int, RemoteMove>> = emptyList()

    init {
        roomReg = roomDoc.addSnapshotListener { snap, err ->
            if (err != null) {
                _state.update { it.copy(error = err.message) }
                return@addSnapshotListener
            }
            if (snap == null || !snap.exists()) {
                _state.update {
                    it.copy(status = SessionStatus.ABANDONED, error = "Room no longer exists.")
                }
                return@addSnapshotListener
            }
            applyRoomSnapshot(snap)
        }

        movesReg = movesCol
            .orderBy(FS.F_SEQ, Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    _state.update { it.copy(error = err.message) }
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                allMoves = snap.documents.mapNotNull { doc ->
                    doc.toRemoteMove()?.let { (doc.getLong(FS.F_ROUND)?.toInt() ?: 0) to it }
                }
                _state.update { it.copy(moves = movesFor(it.round)) }
            }
    }

    override suspend fun sendMove(round: Int, seq: Int, by: Player, move: Move) {
        val ttl = FS.ttlFromNow()
        val payload = mapOf(
            FS.F_ROUND to round,
            FS.F_SEQ to seq,
            FS.F_BY to by.name,
            FS.F_X to move.x,
            FS.F_Y to move.y,
            FS.F_IS_PASS to move.isPass,
            FS.F_TS to FieldValue.serverTimestamp(),
            FS.F_EXPIRES_AT to ttl,
        )
        val doc = movesCol.document(FS.moveDocId(round, seq))
        try {
            db.runBatch { batch ->
                batch.set(doc, payload)
                // Pushing the room's TTL forward on every move keeps active
                // games from being GC'd mid-play, while idle rooms still age
                // out ~24 h after their last activity.
                batch.update(
                    roomDoc,
                    mapOf(
                        FS.F_MOVE_COUNT to seq,
                        FS.F_EXPIRES_AT to ttl,
                    ),
                )
            }.await()
        } catch (t: Throwable) {
            throw t.asOnlineError()
        }
    }

    override suspend fun requestRematch(round: Int) {
        val ttl = FS.ttlFromNow()
        val startedRound = try {
            db.runTransaction { tx ->
                val snap = tx.get(roomDoc)
                if (!snap.exists()) throw OnlineError.RoomNotFound(roomCode)
                val current = snap.getLong(FS.F_ROUND)?.toInt() ?: 0
                // Already moved on (the other player's request completed the
                // rematch first); nothing to do.
                if (current != round) return@runTransaction null
                val isHost = snap.getString(FS.F_HOST_UID) == me.id.value
                val otherField = if (isHost) FS.F_REMATCH_GUEST else FS.F_REMATCH_HOST
                if (snap.getBoolean(otherField) == true) {
                    // Both players are in: start the next round, with the
                    // players swapping colours so the first move alternates.
                    val hostColor = snap.getString(FS.F_HOST_COLOR)
                        ?.let { runCatching { Player.valueOf(it) }.getOrNull() }
                        ?: Player.BLACK
                    tx.update(
                        roomDoc,
                        mapOf(
                            FS.F_ROUND to current + 1,
                            FS.F_HOST_COLOR to hostColor.opponent().name,
                            FS.F_REMATCH_HOST to false,
                            FS.F_REMATCH_GUEST to false,
                            FS.F_MOVE_COUNT to 0,
                            FS.F_EXPIRES_AT to ttl,
                        ),
                    )
                    current + 1
                } else {
                    val myField = if (isHost) FS.F_REMATCH_HOST else FS.F_REMATCH_GUEST
                    tx.update(roomDoc, mapOf(myField to true, FS.F_EXPIRES_AT to ttl))
                    null
                }
            }.await()
        } catch (t: Throwable) {
            throw t.asOnlineError()
        }
        // Whoever started the new round clears out the old rounds' moves.
        // Best-effort: they're filtered out by round anyway, and finish()
        // deletes everything left when the room closes.
        if (startedRound != null) {
            runCatching {
                val stale = movesCol.get().await().documents
                    .filter { (it.getLong(FS.F_ROUND)?.toInt() ?: 0) < startedRound }
                if (stale.isNotEmpty()) {
                    db.runBatch { batch -> stale.forEach { batch.delete(it.reference) } }.await()
                }
            }
        }
    }

    override suspend fun finish() {
        // Tear listeners down first so the subsequent delete doesn't bounce
        // a "doc no longer exists" ABANDONED snapshot into the UI.
        close()
        val moves = runCatching { movesCol.get().await().documents }.getOrDefault(emptyList())
        if (moves.isNotEmpty()) {
            runCatching {
                db.runBatch { batch -> moves.forEach { batch.delete(it.reference) } }.await()
            }
        }
        runCatching { roomDoc.delete().await() }
        // Drop the tracker entry regardless — a failed delete here is almost
        // always because the other player already cleaned up.
        tracker.remove(roomCode)
    }

    override fun close() {
        roomReg.remove()
        movesReg.remove()
    }

    private fun applyRoomSnapshot(snap: DocumentSnapshot) {
        val status = snap.getString(FS.F_STATUS).toStatus()
        val hostUid = snap.getString(FS.F_HOST_UID)
        val hostColor = snap.getString(FS.F_HOST_COLOR)?.let { runCatching { Player.valueOf(it) }.getOrNull() }
        val hostName = snap.getString(FS.F_HOST_NAME)
        val guestUid = snap.getString(FS.F_GUEST_UID)
        val guestName = snap.getString(FS.F_GUEST_NAME)
        val round = snap.getLong(FS.F_ROUND)?.toInt() ?: 0
        val rematchHost = snap.getBoolean(FS.F_REMATCH_HOST) == true
        val rematchGuest = snap.getBoolean(FS.F_REMATCH_GUEST) == true
        val isHost = me.id.value == hostUid

        val myColor = when (me.id.value) {
            hostUid -> hostColor
            guestUid -> hostColor?.opponent()
            else -> null
        }
        val opponent = when (me.id.value) {
            hostUid -> guestUid?.let { PlayerIdentity(PlayerId(it), guestName) }
            guestUid -> hostUid?.let { PlayerIdentity(PlayerId(it), hostName) }
            else -> null
        }
        _state.update {
            it.copy(
                status = status,
                myColor = myColor,
                opponent = opponent,
                error = null,
                round = round,
                moves = movesFor(round),
                rematchRequestedByMe = if (isHost) rematchHost else rematchGuest,
                rematchRequestedByOpponent = if (isHost) rematchGuest else rematchHost,
            )
        }
    }

    /** [allMoves] for [round] only, in play order. */
    private fun movesFor(round: Int): List<RemoteMove> =
        allMoves.filter { it.first == round }.map { it.second }.sortedBy { it.seq }

    private fun String?.toStatus(): SessionStatus = when (this) {
        FS.S_WAITING -> SessionStatus.WAITING
        FS.S_ACTIVE -> SessionStatus.ACTIVE
        FS.S_FINISHED -> SessionStatus.FINISHED
        FS.S_ABANDONED -> SessionStatus.ABANDONED
        else -> SessionStatus.WAITING
    }

    private fun DocumentSnapshot.toRemoteMove(): RemoteMove? {
        val seq = getLong(FS.F_SEQ)?.toInt() ?: return null
        val by = getString(FS.F_BY)?.let { runCatching { Player.valueOf(it) }.getOrNull() } ?: return null
        val x = getLong(FS.F_X)?.toInt() ?: return null
        val y = getLong(FS.F_Y)?.toInt() ?: return null
        val isPass = getBoolean(FS.F_IS_PASS) ?: false
        return RemoteMove(seq = seq, by = by, x = x, y = y, isPass = isPass)
    }
}
