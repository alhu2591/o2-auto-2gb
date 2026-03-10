package com.o2.auto2gb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Foreground service:
 * - stopWithTask=false  → stays alive when app swiped from Recents
 * - onTaskRemoved()     → reschedules itself via AlarmManager if killed
 * - START_STICKY        → Android auto-restarts it if killed by OOM
 * - WorkManager watchdog → checks every 15min and restarts if needed
 *
 * NO persistent WakeLock here — foreground services keep CPU alive on their own.
 * WakeLock is only used per-SMS in SmsReceiver (short & targeted = battery safe).
 */
class SmsService : Service() {

    companion object {
        const val CHANNEL_ID   = "o2_bg_v2"
        const val NOTIF_ID     = 101
        const val ACTION_STOP  = "com.o2.auto2gb.ACTION_STOP"
        const val WATCHDOG_TAG = "o2_watchdog"
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

        try {
            // API 34+ requires foregroundServiceType in startForeground()
            // API 29-33: startForeground without type is fine
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {  // API 34
                startForeground(
                    NOTIF_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIF_ID, buildNotification())
            }
        } catch (e: Exception) {
            AppPrefs.setServiceEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }

        AppPrefs.setServiceEnabled(this, true)
        return START_STICKY
    }

    // Called when user swipes app from Recents — but service has stopWithTask=false
    // so this is called AFTER Android would normally kill the service.
    // We schedule an alarm to restart ourselves ~3 seconds later.
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
            // setAndAllowWhileIdle works in Doze mode, no extra permission needed
            alarm.setAndAllowWhileIdle(
                android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + 3_000L,
                pi
            )
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't update AppPrefs here — we only want to clear it on intentional stop (ACTION_STOP)
        // so the watchdog / boot receiver can restart us if we were killed unexpectedly
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
            NotificationChannel(
                CHANNEL_ID,
                "O2 Auto 2GB",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
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
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
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
