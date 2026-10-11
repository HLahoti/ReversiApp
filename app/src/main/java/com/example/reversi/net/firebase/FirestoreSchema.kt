package com.example.reversi.net.firebase

import com.google.firebase.Timestamp
import java.util.Date

/**
 * Field + path constants for the Firestore schema. Centralised so a rename
 * only touches one file, and the UI layer never sees a raw string key.
 *
 * Schema (one room per game):
 *
 *   rooms/{code}:
 *       status:      "waiting" | "active" | "finished" | "abandoned"
 *       createdAt:   server timestamp
 *       hostUid:     String
 *       hostColor:   "BLACK" | "WHITE"
 *       hostName:    String?
 *       guestUid:    String?
 *       guestName:   String?
 *       moveCount:   Int        (count of child docs in /moves for this round)
 *       round:       Int        (0 for the first game, +1 per rematch)
 *       rematchHost: Boolean    (host pressed "Play again" this round)
 *       rematchGuest: Boolean   (guest pressed "Play again" this round)
 *       expiresAt:   Timestamp  (TTL field — see Firestore TTL policy)
 *
 *   rooms/{code}/moves/{"%03d-%06d" round, seq}:
 *       round:       Int
 *       seq:         Int
 *       by:          "BLACK" | "WHITE"
 *       x:           Int
 *       y:           Int
 *       isPass:      Boolean
 *       ts:          server timestamp
 *       expiresAt:   Timestamp  (TTL field; matches the room's lifetime)
 */
internal object FS {

    const val COL_ROOMS = "rooms"
    const val SUB_MOVES = "moves"

    const val F_STATUS = "status"
    const val F_CREATED_AT = "createdAt"
    const val F_HOST_UID = "hostUid"
    const val F_HOST_COLOR = "hostColor"
    const val F_HOST_NAME = "hostName"
    const val F_GUEST_UID = "guestUid"
    const val F_GUEST_NAME = "guestName"
    const val F_MOVE_COUNT = "moveCount"
    const val F_ROUND = "round"
    const val F_REMATCH_HOST = "rematchHost"
    const val F_REMATCH_GUEST = "rematchGuest"
    const val F_EXPIRES_AT = "expiresAt"

    const val F_SEQ = "seq"
    const val F_BY = "by"
    const val F_X = "x"
    const val F_Y = "y"
    const val F_IS_PASS = "isPass"
    const val F_TS = "ts"

    const val S_WAITING = "waiting"
    const val S_ACTIVE = "active"
    const val S_FINISHED = "finished"
    const val S_ABANDONED = "abandoned"

    /** How far ahead to push the TTL on every write. */
    const val TTL_HOURS: Long = 24

    /** Timestamp [TTL_HOURS] ahead of now, used as the TTL field on writes. */
    fun ttlFromNow(hours: Long = TTL_HOURS): Timestamp =
        Timestamp(Date(System.currentTimeMillis() + hours * 3600L * 1000L))

    fun moveDocId(round: Int, seq: Int): String = "%03d-%06d".format(round, seq)
}
