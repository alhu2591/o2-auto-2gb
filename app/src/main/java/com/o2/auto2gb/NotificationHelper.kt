package com.o2.auto2gb

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {

    private const val CHANNEL_ID = "o2_events"
    private var counter = 200

    fun showReplySuccess(ctx: Context, toPhone: String, message: String) {
        if (!SmsService.hasNotificationPermission(ctx)) return
        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(mgr, ctx)

        val openPi = PendingIntent.getActivity(ctx, 0,
            Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        mgr.notify(counter++, NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(ctx.getString(R.string.notif_reply_title))
            .setContentText("\"$message\" → $toPhone")
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build())

        if (counter > 300) counter = 200
    }

    private fun ensureChannel(mgr: NotificationManager, ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        NotificationChannel(CHANNEL_ID,
            ctx.getString(R.string.channel_events_name),
            NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = ctx.getString(R.string.channel_events_desc)
            enableLights(true); enableVibration(false)
        }.also { mgr.createNotificationChannel(it) }
    }
}
