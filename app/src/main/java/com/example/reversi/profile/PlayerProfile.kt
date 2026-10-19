package com.example.reversi.profile

import kotlinx.coroutines.flow.StateFlow

/**
 * Who the player is, as far as their opponents can tell. Today that's a name
 * typed into the online lobby and kept on the device ([LocalPlayerProfile]);
 * moving to Google sign-in or real accounts means writing a sibling that
 * reads the account's name and changing the one install line in MainActivity.
 * Nothing else depends on where the name comes from.
 */
interface PlayerProfile {

    /**
     * What the local player is called on their own screens, such as the
     * game history. "You" until there are accounts.
     */
    val selfLabel: String

    /** The name shown to online opponents, or null until one is set. */
    val displayName: StateFlow<String?>

    /**
     * Whether the player can type their own name. False for a profile whose
     * name comes from an account; the lobby then shows it read-only.
     */
    val canEditName: Boolean

    /** Set the name, cleaned with [DisplayNames.clean]; blank clears it. */
    fun setDisplayName(name: String)
}

/** Rules every display name follows, whichever [PlayerProfile] supplies it. */
object DisplayNames {
    const val MAX_LENGTH = 10

    /** Trim, collapse inner whitespace and cap at [MAX_LENGTH]; null if nothing is left. */
    fun clean(raw: String?): String? =
        raw?.trim()?.replace(Regex("\\s+"), " ")?.take(MAX_LENGTH)?.trimEnd()?.ifEmpty { null }

    /** True when two names would be hard to tell apart on screen. */
    fun same(a: String?, b: String?): Boolean {
        val x = clean(a) ?: return false
        val y = clean(b) ?: return false
        return x.equals(y, ignoreCase = true)
    }
}

/**
 * Process-wide holder for the single [PlayerProfile], written once from
 * MainActivity, like [com.example.reversi.net.OnlineRegistry].
 */
object ProfileRegistry {
    @Volatile private var instance: PlayerProfile? = null

    fun install(profile: PlayerProfile) {
        instance = profile
    }

    val current: PlayerProfile
        get() = instance ?: error(
            "PlayerProfile not installed. Call ProfileRegistry.install() from MainActivity.",
        )
}
