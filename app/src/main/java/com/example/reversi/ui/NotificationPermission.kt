package com.example.reversi.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * True when Android will show the app's notifications: the permission is
 * granted (Android 13 and up) and they aren't blocked in system settings.
 */
fun notificationsAllowed(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

/**
 * The system page for the app's notifications, for when Android won't show
 * the permission prompt any more (the player refused it twice) or they're
 * blocked there.
 */
fun appNotificationSettings(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= 26) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        // No notification page before Android 8; the app's info page links to it.
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }
