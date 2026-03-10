package com.o2.auto2gb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Foreground service — optional layer on top of SmsReceiver.
 * SmsReceiver works 100% independently even if this service is stopped.
 * This service exists only to:
 *   1. Keep the process alive (faster SMS response, no cold-start delay)
 *   2. Show a status notification if POST_NOTIFICATIONS is granted
 *
 * If notifications are blocked:
 *   - Android 13+ (API 33): POST_NOTIFICATIONS denied → we skip startForeground,
 *     no service, but SmsReceiver STILL works independently.
 *   - Android 12 and below: notifications cannot be fully blocked per-app for
 *     foreground services — Android shows them regardless.
 */
class SmsService : Service() {

    companion object {
        const val CHANNEL_ID   = "o2_bg_v2"
        const val NOTIF_ID     = 101
        const val ACTION_STOP  = "com.o2.auto2gb.ACTION_STOP"
        const val WATCHDOG_TAG = "o2_watchdog"

        fun hasNotificationPermission(ctx: android.content.Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    ctx, android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                // Below Android 13, notifications always allowed for foreground services
                true
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scheduleWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AppPrefs.setServiceEnabled(this, false)
            cancelWatchdog()
            stopSelf()
            return START_NOT_STICKY
        }

        // If notifications are blocked on Android 13+, we CANNOT run as foreground service.
        // SmsReceiver will still handle all SMS independently — so just stop gracefully.
        if (!hasNotificationPermission(this)) {
            // Mark as enabled so watchdog/boot know user wants it,
            // and SmsReceiver will handle SMS without the service.
            AppPrefs.setServiceEnabled(this, true)
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34
                startForeground(
                    NOTIF_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIF_ID, buildNotification())
            }
        } catch (e: Exception) {
            // startForeground failed (e.g. notification channel blocked at OS level)
            // Service can't run, but SmsReceiver remains active
            AppPrefs.setServiceEnabled(this, true)
            stopSelf()
            return START_NOT_STICKY
        }

        AppPrefs.setServiceEnabled(this, true)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!AppPrefs.isServiceEnabled(this)) return
        try {
            val restart = Intent(applicationContext, SmsService::class.java)
            val pi = PendingIntent.getService(
                applicationContext, 42, restart,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarm = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            alarm.setAndAllowWhileIdle(
                android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + 3_000L,
                pi
            )
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleWatchdog() {
        try {
            val req = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(15, TimeUnit.MINUTES)
                .addTag(WATCHDOG_TAG)
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                WATCHDOG_TAG, ExistingPeriodicWorkPolicy.KEEP, req
            )
        } catch (_: Exception) {}
    }

    private fun cancelWatchdog() {
        try {
            WorkManager.getInstance(applicationContext).cancelAllWorkByTag(WATCHDOG_TAG)
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
            NotificationChannel(CHANNEL_ID, "O2 Auto 2GB", NotificationManager.IMPORTANCE_MIN).apply {
                description         = "Background SMS monitoring service"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }.also { mgr.createNotificationChannel(it) }
        }
    }

    private fun buildNotification(): Notification {
        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, SmsService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("O2 Auto 2GB — Active")
            .setContentText("Monitoring SMS. Will reply \"Weiter\" to +4980112")
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
