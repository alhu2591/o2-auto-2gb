package com.o2.auto2gb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class SmsService : Service() {

    companion object {
        const val CHANNEL_ID = "o2_bg"
        const val NOTIF_ID = 101
    }

    override fun onCreate() {
        super.onCreate()
        // Create channel here — before startForeground() is called
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // MUST call startForeground immediately — Android gives 5 seconds max
        try {
            startForeground(NOTIF_ID, buildNotification())
        } catch (e: Exception) {
            // If foreground fails, stop self gracefully instead of crashing
            stopSelf()
            return START_NOT_STICKY
        }
        SmsServiceState.isRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        SmsServiceState.isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
            val ch = NotificationChannel(
                CHANNEL_ID,
                "O2 Auto 2GB",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Background service for automatic SMS reply"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            mgr.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("O2 Auto 2GB")
            .setContentText("Monitoring O2 SMS for auto-reply")
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()
    }
}
