package com.o2.auto2gb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Foreground service — يبقى حياً حتى لو أغلق المستخدم التطبيق.
 * android:stopWithTask="false" في Manifest يمنع Android من إيقافه.
 * onTaskRemoved() يُعيد تشغيله لو أُغلق من Recent Apps.
 */
class SmsService : Service() {

    companion object {
        const val CHANNEL_ID = "o2_bg_v2"
        const val NOTIF_ID   = 101
        const val ACTION_STOP = "com.o2.auto2gb.ACTION_STOP"
        const val WATCHDOG_TAG = "o2_watchdog"
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        scheduleWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle stop action from notification
        if (intent?.action == ACTION_STOP) {
            AppPrefs.isServiceEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }

        // MUST be called within 5 seconds of onStartCommand
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, buildNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, buildNotification())
            }
        } catch (e: Exception) {
            AppPrefs.isServiceEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }

        AppPrefs.isServiceEnabled = true
        return START_STICKY  // Android restarts this service automatically if killed
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Called when user swipes app from Recent Apps.
        // Re-schedule our own restart if user still wants service.
        super.onTaskRemoved(rootIntent)
        if (AppPrefs.isServiceEnabled) {
            val restart = Intent(applicationContext, SmsService::class.java).also {
                it.setPackage(packageName)
            }
            val pi = android.app.PendingIntent.getService(
                applicationContext, 1, restart,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarm = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            alarm.set(
                android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + 3000,
                pi
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        // If destroyed unexpectedly and user still wants it → restart via watchdog
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Wake lock: prevents CPU sleep during SMS processing ──────────────────
    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "O2Auto2GB::ServiceLock"
            ).also {
                it.setReferenceCounted(false)
                // Only hold for 10s at a time — WakeLock is renewed on each SMS
                it.acquire(10 * 1000L)
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
    }

    // ── Watchdog: periodic WorkManager task restarts service if killed ───────
    private fun scheduleWatchdog() {
        try {
            val req = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(15, TimeUnit.MINUTES)
                .addTag(WATCHDOG_TAG)
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                WATCHDOG_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        } catch (_: Exception) {}
    }

    // ── Notification ─────────────────────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
            NotificationChannel(CHANNEL_ID, "O2 Auto 2GB", NotificationManager.IMPORTANCE_MIN).apply {
                description      = "Background service for automatic SMS reply"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }.also { mgr.createNotificationChannel(it) }
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = PendingIntent.getService(
            this, 1,
            Intent(this, SmsService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("O2 Auto 2GB")
            .setContentText("Monitoring SMS — will auto-reply to O2 triggers")
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_delete, "Stop", stopAction)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
