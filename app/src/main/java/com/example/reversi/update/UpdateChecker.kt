package com.example.reversi.update

import android.app.Activity

/**
 * Update checking, for as long as the app is sideloaded.
 *
 * WHY THIS EXISTS: the app ships as an APK on GitHub Releases, not on the Play
 * Store, so nothing tells players a new version is out. Until the app moves
 * to the Play Store, [GitHubReleaseChecker] asks GitHub and the home screen
 * and Settings point players at the release page.
 *
 * MOVING TO THE PLAY STORE: write a Play implementation of this interface
 * (Play Core's in-app updates: `AppUpdateManager.appUpdateInfo` for [latest],
 * `startUpdateFlowForResult` for [startUpdate]) and swap the one
 * `Updates.install(...)` line in MainActivity. [Updates], the banner and the
 * About section only see this interface and need no change. Once the Play
 * version is the only one in use, delete [GitHubReleaseChecker].
 */
interface UpdateChecker {
    /**
     * The newest version available, or null when the installed one is
     * current. Throws on network or backend failure; [Updates] treats that as
     * "don't know" and tries again on its next check.
     */
    suspend fun latest(): AvailableUpdate?

    /** Send the player to get [update] (a release page, a store sheet, ...). */
    fun startUpdate(activity: Activity, update: AvailableUpdate)
}

/** A version newer than the installed one. */
data class AvailableUpdate(
    /** Shown to players, e.g. "1.6". */
    val versionName: String,
    /** The release's headline, if the source has one. */
    val title: String? = null,
    /** Where [UpdateChecker.startUpdate] sends the player, if it's a page. */
    val url: String? = null,
)
