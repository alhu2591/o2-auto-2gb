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

class SmsService : Service() {

    companion object {
        const val CHANNEL_ID   = "o2_bg_v2"
        const val NOTIF_ID     = 101
        const val ACTION_STOP  = "com.o2.auto2gb.ACTION_STOP"
        const val WATCHDOG_TAG = "o2_watchdog"

        fun hasNotificationPermission(ctx: android.content.Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
            else true
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
        if (!hasNotificationPermission(this)) {
            AppPrefs.setServiceEnabled(this, true)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, buildNotification())
            }
        } catch (e: Exception) {
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
            val pi = PendingIntent.getService(applicationContext, 42,
                Intent(applicationContext, SmsService::class.java),
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
            (getSystemService(ALARM_SERVICE) as android.app.AlarmManager)
                .setAndAllowWhileIdle(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + 3_000L, pi)
        } catch (_: Exception) {}
    }

    override fun onDestroy() { super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleWatchdog() {
        try {
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                WATCHDOG_TAG, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(15, TimeUnit.MINUTES)
                    .addTag(WATCHDOG_TAG).build()
            )
        } catch (_: Exception) {}
    }

    private fun cancelWatchdog() {
        try { WorkManager.getInstance(applicationContext).cancelAllWorkByTag(WATCHDOG_TAG) }
        catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        NotificationChannel(CHANNEL_ID, getString(R.string.channel_service_name),
            NotificationManager.IMPORTANCE_MIN).apply {
            description         = getString(R.string.channel_service_desc)
            setShowBadge(false); enableLights(false); enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }.also { mgr.createNotificationChannel(it) }
    }

    private fun buildNotification(): Notification {
        val openPi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopPi = PendingIntent.getService(this, 1,
            Intent(this, SmsService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_service_title))
            .setContentText(getString(R.string.notif_service_text))
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_delete, getString(R.string.notif_stop), stopPi)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true).setSilent(true).setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
