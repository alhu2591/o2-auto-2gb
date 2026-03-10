package com.o2.auto2gb

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent state — survives process kill, reboot, app update.
 * Uses deviceProtectedStorage so it works before screen unlock (directBootAware).
 */
object AppPrefs {

    private const val FILE = "o2_prefs"
    private const val KEY_SERVICE_ENABLED = "service_enabled"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"

    // Accepts any context — always uses applicationContext + device protected storage
    private fun prefs(ctx: Context): SharedPreferences {
        val appCtx = ctx.applicationContext
        // Device-protected storage: readable before first unlock (needed for directBootAware)
        val storageCtx = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            appCtx.createDeviceProtectedStorageContext()
        } else {
            appCtx
        }
        return storageCtx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    fun isServiceEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SERVICE_ENABLED, false)

    fun setServiceEnabled(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()

    fun isOnboardingDone(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone(ctx: Context, done: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
}
