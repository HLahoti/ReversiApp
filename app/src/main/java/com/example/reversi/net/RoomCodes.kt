package com.example.reversi.net

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * Short, human-friendly room identifiers. Six characters picked from an
 * unambiguous alphabet (no 0/O/1/I/L) so friends can read them aloud or
 * punch them in without ambiguity.
 */
object RoomCodes {

    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val LENGTH = 6

    /** Codes are the only thing keeping a room private, so draw them from a CSPRNG. */
    private val secureRandom: Random = SecureRandom().asKotlinRandom()

    fun generate(random: Random = secureRandom): RoomCode {
        val chars = CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] }
        return RoomCode(String(chars))
    }

    /**
     * Normalise user input (upper-case, strip spaces/dashes) and validate.
     * Returns null if the input is not a well-formed code — the UI shows an
     * error rather than hitting the backend.
     */
    fun parse(raw: String): RoomCode? {
        val cleaned = raw.uppercase().filter { it in ALPHABET }
        if (cleaned.length != LENGTH) return null
        return RoomCode(cleaned)
    }
}
