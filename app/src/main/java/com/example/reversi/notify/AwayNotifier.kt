package com.example.reversi.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.reversi.MainActivity
import com.example.reversi.R
import com.example.reversi.data.UserSettings
import com.example.reversi.net.OnlineScope
import com.example.reversi.net.OnlineSession
import com.example.reversi.net.SessionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tells the player what happened in their online game while they were in
 * another app (see [OnlineAlerts] for what counts).
 *
 * WHY A FOREGROUND SERVICE: the app is on Firebase's free plan, which can't
 * run the server code push notifications need. So the alerts come from the
 * app itself, from the room listeners it already has open, and
 * [OnlineGameService] keeps the app alive in the background while a room is
 * open. Its ongoing notification is the price of that.
 *
 * Backend-neutral: it only sees [OnlineSession]. Sessions join it through
 * [watch], and leave it when they are closed or finished.
 */
object AwayNotifier {
    private const val CHANNEL_ALERTS = "online_alerts"
    internal const val CHANNEL_ONGOING = "online_ongoing"
    private const val ALERT_ID = 1
    internal const val ONGOING_ID = 2

    private lateinit var app: Context
    @Volatile private var visible = false

    private var session: OnlineSession? = null
    private var job: Job? = null
    private var last: SessionState? = null

    private val _ongoing = MutableStateFlow<Notice?>(null)
    /** What [OnlineGameService] shows; null when it should stop. */
    internal val ongoing: StateFlow<Notice?> = _ongoing.asStateFlow()

    /** Call once at startup, before any session is watched. */
    fun install(context: Context) {
        app = context.applicationContext
        val nm = NotificationManagerCompat.from(app)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ALERTS, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Online game alerts")
                .setDescription("Your opponent joined, played, asked for a rematch or the game ended.")
                .build(),
        )
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ONGOING, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Online game in progress")
                .setDescription("Shown while a room is open, so alerts can reach you.")
                .build(),
        )
    }

    /** Add to the activity's lifecycle: alerts are only posted while it's out of view. */
    val visibility = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            visible = true
            // Back in the game, so whatever the alert said is on screen.
            NotificationManagerCompat.from(app).cancel(ALERT_ID)
        }

        override fun onStop(owner: LifecycleOwner) {
            visible = false
        }
    }

    /**
     * Follow [session] until it is closed or finished, and return it wrapped
     * so that closing it stops the alerts. Call while the app is in view
     * (creating or joining a room), since Android only lets a foreground
     * service start then.
     */
    @Synchronized
    fun watch(session: OnlineSession): OnlineSession {
        unwatch(this.session)
        this.session = session
        job = OnlineScope.launch {
            session.state.collect { next ->
                // Before the room's first snapshot there's no seat to judge by.
                if (next.myColor == null) return@collect
                val prev = synchronized(this@AwayNotifier) {
                    if (this@AwayNotifier.session !== session) return@collect
                    last.also { last = next; _ongoing.value = ongoingFor(next) }
                }
                if (prev == null || visible || !UserSettings.awayAlerts.value) return@collect
                OnlineAlerts.between(prev, next)?.let(::post)
            }
        }
        // Set before the service starts: it stops itself on a null.
        _ongoing.value = ongoingFor(session.state.value)
        startService()
        return object : OnlineSession by session {
            override suspend fun finish() {
                unwatch(session)
                session.finish()
            }

            override fun close() {
                unwatch(session)
                session.close()
            }
        }
    }

    @Synchronized
    private fun unwatch(session: OnlineSession?) {
        if (session == null || session !== this.session) return
        job?.cancel()
        job = null
        this.session = null
        last = null
        _ongoing.value = null
        NotificationManagerCompat.from(app).cancel(ALERT_ID)
    }

    private fun ongoingFor(state: SessionState): Notice? {
        val s = session ?: return null
        if (!UserSettings.awayAlerts.value || !canNotify()) return null
        return OnlineAlerts.ongoing(state, s.roomCode.value)
    }

    private fun startService() {
        if (!UserSettings.awayAlerts.value || !canNotify()) return
        try {
            ContextCompat.startForegroundService(app, Intent(app, OnlineGameService::class.java))
        } catch (_: IllegalStateException) {
            // Not allowed from the background; the game still works, just without alerts.
        }
    }

    private fun post(notice: Notice) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val n = NotificationCompat.Builder(app, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_reversi)
            .setContentTitle(notice.title)
            .setContentText(notice.text)
            .setContentIntent(openApp(app))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        // One slot, so a later alert replaces an unread one instead of piling up.
        NotificationManagerCompat.from(app).notify(ALERT_ID, n)
    }

    private fun canNotify(): Boolean = NotificationManagerCompat.from(app).areNotificationsEnabled()

    /**
     * Brings the app back on whatever screen it was showing: the running
     * activity is reused rather than a new one stacked on top.
     */
    internal fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
