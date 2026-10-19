package com.example.reversi.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The player's choices from the Settings screen, persisted across launches. */
object UserSettings {
    private const val PREFS = "settings"
    private const val K_HINTS_OFF = "hintsOff"
    private const val K_OPENING_ANIMATION = "openingAnimation"

    private lateinit var prefs: SharedPreferences

    private val _hintsOff = MutableStateFlow(false)
    /** True when the hint bulb is turned off in vs-bot games. */
    val hintsOff: StateFlow<Boolean> = _hintsOff.asStateFlow()

    private val _openingAnimation = MutableStateFlow(true)
    /** True when a new game opens with the game-start intro. On by default. */
    val openingAnimation: StateFlow<Boolean> = _openingAnimation.asStateFlow()

    /** Call once at startup, before any screen reads a setting. */
    fun install(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _hintsOff.value = prefs.getBoolean(K_HINTS_OFF, false)
        _openingAnimation.value = prefs.getBoolean(K_OPENING_ANIMATION, true)
    }

    fun setHintsOff(off: Boolean) {
        _hintsOff.value = off
        prefs.edit().putBoolean(K_HINTS_OFF, off).apply()
    }

    fun setOpeningAnimation(on: Boolean) {
        _openingAnimation.value = on
        prefs.edit().putBoolean(K_OPENING_ANIMATION, on).apply()
    }
}
