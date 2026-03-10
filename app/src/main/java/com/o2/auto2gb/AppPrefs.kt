package com.o2.auto2gb

import android.content.Context
import android.content.SharedPreferences

/**
 * Single source of truth for app persistent state.
 * Replaces in-memory SmsServiceState which resets on process kill.
 */
object AppPrefs {
    private const val FILE = "o2_prefs"
    private const val KEY_SERVICE_ENABLED = "service_enabled"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var isServiceEnabled: Boolean
        get() = _ctx?.let { prefs(it).getBoolean(KEY_SERVICE_ENABLED, false) } ?: false
        set(v) { _ctx?.let { prefs(it).edit().putBoolean(KEY_SERVICE_ENABLED, v).apply() } }

    var isOnboardingDone: Boolean
        get() = _ctx?.let { prefs(it).getBoolean(KEY_ONBOARDING_DONE, false) } ?: false
        set(v) { _ctx?.let { prefs(it).edit().putBoolean(KEY_ONBOARDING_DONE, v).apply() } }

    private var _ctx: Context? = null
    fun init(ctx: Context) { _ctx = ctx.applicationContext }
}
