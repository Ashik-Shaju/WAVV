package com.wavv.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

internal object MusicInsightsNotification {
    private const val PREFERENCES = "music_insights_notifications"
    private const val FIRST_COMPLETION_SENT = "first_completion_sent"
    private const val PERMISSION_PROMPTED = "notification_permission_prompted"
    private const val CHANNEL_ID = "music_insights"
    private const val NOTIFICATION_ID = 2401

    @Synchronized
    fun notifyFirstCompletionIfNeeded(context: Context) {
        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getBoolean(FIRST_COMPLETION_SENT, false)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val notifications = NotificationManagerCompat.from(appContext)
        if (!notifications.areNotificationsEnabled()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Music insights",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Updates when Wavv finishes its first music analysis"
                }
                appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }

            val openAppIntent = PendingIntent.getActivity(
                appContext,
                NOTIFICATION_ID,
                Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            notifications.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(appContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_music_insights_notification)
                    .setContentTitle("Music insights are up to date")
                    .setContentText("Your music insights are ready to explore.")
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText("Wavv finished analyzing your library. Your music insights are ready to explore."),
                    )
                    .setContentIntent(openAppIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build(),
            )
            preferences.edit().putBoolean(FIRST_COMPLETION_SENT, true).apply()
        } catch (_: SecurityException) {
            // Android may revoke notification permission between the permission check and notify().
        }
    }

    fun shouldRequestPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return !preferences.getBoolean(FIRST_COMPLETION_SENT, false) &&
            !preferences.getBoolean(PERMISSION_PROMPTED, false) &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }

    fun markPermissionPrompted(context: Context) {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(PERMISSION_PROMPTED, true).apply()
    }
}
