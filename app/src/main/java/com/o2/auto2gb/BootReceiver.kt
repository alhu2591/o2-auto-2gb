package com.o2.auto2gb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * directBootAware=true → fires on LOCKED_BOOT_COMPLETED (before PIN/pattern unlock).
 * AppPrefs uses deviceProtectedStorage so it's readable at this point.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,         // directBoot: before unlock
            "android.intent.action.QUICKBOOT_POWERON",   // HTC/OnePlus
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED            // after app update
        )
        if (intent.action !in validActions) return

        // Only restart if user had it enabled before reboot
        if (!AppPrefs.isServiceEnabled(context)) return

        try {
            val svc = Intent(context, SmsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc)
            } else {
                context.startService(svc)
            }
        } catch (_: Exception) {}
    }
}
