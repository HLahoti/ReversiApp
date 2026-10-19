package com.example.reversi.profile

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A [PlayerProfile] whose name the player types in and this device keeps.
 * Stand-in until there are real accounts; see [PlayerProfile].
 */
class LocalPlayerProfile(context: Context) : PlayerProfile {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _displayName = MutableStateFlow(DisplayNames.clean(prefs.getString(K_NAME, null)))
    override val displayName: StateFlow<String?> = _displayName.asStateFlow()

    override val canEditName: Boolean = true

    override fun setDisplayName(name: String) {
        val cleaned = DisplayNames.clean(name)
        _displayName.value = cleaned
        prefs.edit().putString(K_NAME, cleaned).apply()
    }

    private companion object {
        const val PREFS = "profile"
        const val K_NAME = "displayName"
    }
}
