package com.example.reversi.update

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's one view of "is there an update?", shared by the home-screen
 * banner and the About section in Settings.
 *
 * Backend-neutral: it only talks to the installed [UpdateChecker], so it
 * stays as is when the GitHub checker is swapped for a Play Store one (see
 * [UpdateChecker]). What it adds on top:
 *  - asks the checker at most once every [CHECK_INTERVAL_MS], and
 *  - remembers the last answer, so the banner shows straight away on the
 *    next launch instead of waiting on the network.
 * The remembered answer is dropped once the installed version changes, so
 * updating the app clears the banner.
 */
object Updates {
    private const val PREFS = "updates"
    private const val K_CHECKED_AT = "checkedAt"
    private const val K_CHECKED_ON = "checkedOnVersion"
    private const val K_VERSION = "version"
    private const val K_TITLE = "title"
    private const val K_URL = "url"
    private const val K_DISMISSED = "dismissedVersion"

    /** At most one check a day. */
    const val CHECK_INTERVAL_MS: Long = 24L * 60 * 60 * 1000

    private var checker: UpdateChecker? = null
    private lateinit var prefs: SharedPreferences
    private var installedVersion = ""

    private val _available = MutableStateFlow<AvailableUpdate?>(null)
    /** The newer version, or null if none is known. */
    val available: StateFlow<AvailableUpdate?> = _available.asStateFlow()

    private val _dismissed = MutableStateFlow<String?>(null)
    /** The version whose banner the player closed; Settings still shows it. */
    val dismissedVersion: StateFlow<String?> = _dismissed.asStateFlow()

    /** Call once at startup, before [checkIfDue]. */
    fun install(context: Context, checker: UpdateChecker, installedVersion: String) {
        this.checker = checker
        this.installedVersion = installedVersion
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(K_CHECKED_ON, null) != installedVersion) {
            // Installed a new version since the last check: what we knew is stale.
            prefs.edit().clear().apply()
        }
        _available.value = prefs.getString(K_VERSION, null)?.let {
            AvailableUpdate(it, prefs.getString(K_TITLE, null), prefs.getString(K_URL, null))
        }
        _dismissed.value = prefs.getString(K_DISMISSED, null)
    }

    /**
     * Ask the checker if the last check was over a day ago. Failures are
     * swallowed and leave the last answer in place; the next launch retries.
     */
    suspend fun checkIfDue(now: Long = System.currentTimeMillis()) {
        val c = checker ?: return
        if (now - prefs.getLong(K_CHECKED_AT, 0L) < CHECK_INTERVAL_MS) return
        val update = runCatching { c.latest() }.getOrElse { return }
        prefs.edit()
            .putLong(K_CHECKED_AT, now)
            .putString(K_CHECKED_ON, installedVersion)
            .putString(K_VERSION, update?.versionName)
            .putString(K_TITLE, update?.title)
            .putString(K_URL, update?.url)
            .apply()
        _available.value = update
    }

    fun startUpdate(activity: Activity) {
        val update = _available.value ?: return
        checker?.startUpdate(activity, update)
    }

    /** Hide the home-screen banner for the current update. */
    fun dismissBanner() {
        val v = _available.value?.versionName ?: return
        prefs.edit().putString(K_DISMISSED, v).apply()
        _dismissed.value = v
    }
}
