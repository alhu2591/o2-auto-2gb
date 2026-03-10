package com.o2.auto2gb

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Shows a brief, dismissible notification whenever an auto-reply is sent.
 * Uses a separate channel (IMPORTANCE_DEFAULT) from the service channel (IMPORTANCE_MIN)
 * so the user can silence service notifications without silencing reply confirmations.
 *
 * Only shown if POST_NOTIFICATIONS is granted.
 */
object NotificationHelper {

    private const val CHANNEL_EVENTS = "o2_events"
    private var notifCounter = 200  // Start above service NOTIF_ID=101

    fun showReplySuccess(ctx: Context, toPhone: String, message: String) {
        if (!SmsService.hasNotificationPermission(ctx)) return

        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
        ensureEventChannel(mgr)

        val openPi = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(ctx, CHANNEL_EVENTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("✓ Auto-reply sent")
            .setContentText("Replied \"$message\" to $toPhone")
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        mgr.notify(notifCounter++, notif)
        // Reset counter to avoid overflow (max ~100 queued)
        if (notifCounter > 300) notifCounter = 200
    }

    private fun ensureEventChannel(mgr: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (mgr.getNotificationChannel(CHANNEL_EVENTS) != null) return
        NotificationChannel(
            CHANNEL_EVENTS,
            "Auto-Reply Events",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies when an automatic SMS reply is sent"
            enableLights(true)
            enableVibration(false)
        }.also { mgr.createNotificationChannel(it) }
    }
}
