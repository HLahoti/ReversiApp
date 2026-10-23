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
    private const val K_TAKEBACKS_OFF = "takebacksOff"
    private const val K_AWAY_ALERTS = "awayAlerts"

    private lateinit var prefs: SharedPreferences

    private val _hintsOff = MutableStateFlow(false)
    /** True when the hint bulb is turned off in vs-bot games. */
    val hintsOff: StateFlow<Boolean> = _hintsOff.asStateFlow()

    private val _takebacksOff = MutableStateFlow(false)
    /**
     * True when vs-bot games can't be played on from an earlier move: the
     * move log can still be browsed, but only the latest position is live.
     */
    val takebacksOff: StateFlow<Boolean> = _takebacksOff.asStateFlow()

    private val _openingAnimation = MutableStateFlow(true)
    /** True when a new game opens with the game-start intro. On by default. */
    val openingAnimation: StateFlow<Boolean> = _openingAnimation.asStateFlow()

    private val _awayAlerts = MutableStateFlow(true)
    /**
     * True when an online game sends notifications while the player is in
     * another app; see [com.example.reversi.notify.AwayNotifier]. On by default.
     */
    val awayAlerts: StateFlow<Boolean> = _awayAlerts.asStateFlow()

    /** Call once at startup, before any screen reads a setting. */
    fun install(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _hintsOff.value = prefs.getBoolean(K_HINTS_OFF, false)
        _openingAnimation.value = prefs.getBoolean(K_OPENING_ANIMATION, true)
        _takebacksOff.value = prefs.getBoolean(K_TAKEBACKS_OFF, false)
        _awayAlerts.value = prefs.getBoolean(K_AWAY_ALERTS, true)
    }

    fun setHintsOff(off: Boolean) {
        _hintsOff.value = off
        prefs.edit().putBoolean(K_HINTS_OFF, off).apply()
    }

    fun setTakebacksOff(off: Boolean) {
        _takebacksOff.value = off
        prefs.edit().putBoolean(K_TAKEBACKS_OFF, off).apply()
    }

    fun setAwayAlerts(on: Boolean) {
        _awayAlerts.value = on
        prefs.edit().putBoolean(K_AWAY_ALERTS, on).apply()
    }

    fun setOpeningAnimation(on: Boolean) {
        _openingAnimation.value = on
        prefs.edit().putBoolean(K_OPENING_ANIMATION, on).apply()
    }
}
