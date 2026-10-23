package com.example.reversi.notify

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.reversi.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the app alive in the background while an online room is open, so
 * [AwayNotifier] can hear the room change and post alerts. Shows
 * [AwayNotifier.ongoing], and stops itself once that goes null.
 */
class OnlineGameService : Service() {
    private val scope = MainScope()
    private var job: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Started with startForegroundService, so this has to run even when
        // the room has already closed; the collector then stops at once.
        show(AwayNotifier.ongoing.value ?: Notice("Online game", ""))
        if (job == null) {
            job = scope.launch {
                AwayNotifier.ongoing.collect { next ->
                    if (next == null) stopSelf() else show(next)
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Post [notice] as the ongoing notification; the same id updates it in place. */
    private fun show(notice: Notice) {
        val n = build(notice)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(AwayNotifier.ONGOING_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(AwayNotifier.ONGOING_ID, n)
        }
    }

    private fun build(notice: Notice): Notification =
        NotificationCompat.Builder(this, AwayNotifier.CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_reversi)
            .setContentTitle(notice.title)
            .setContentText(notice.text)
            .setContentIntent(AwayNotifier.openApp(this))
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
