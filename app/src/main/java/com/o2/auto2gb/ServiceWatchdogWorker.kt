package com.o2.auto2gb

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Runs every 15 minutes via WorkManager (minimum allowed interval).
 * If SmsService should be running but isn't → restart it.
 * This is the final safety net after START_STICKY + onTaskRemoved + BootReceiver.
 */
class ServiceWatchdogWorker(
    private val ctx: Context,
    params: WorkerParameters
) : Worker(ctx, params) {

    override fun doWork(): Result {
        if (!AppPrefs.isServiceEnabled(ctx)) return Result.success()
        if (isServiceRunning()) return Result.success()

        try {
            val intent = Intent(ctx, SmsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        } catch (_: Exception) {}

        return Result.success()
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean = try {
        // getRunningServices() is deprecated but still returns own app's services correctly
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.getRunningServices(50).any {
            it.service.className == SmsService::class.java.name
        }
    } catch (_: Exception) { false }
}
