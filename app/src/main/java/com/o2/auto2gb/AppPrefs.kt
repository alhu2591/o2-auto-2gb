package com.o2.auto2gb

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent state — survives process kill, reboot, app update.
 * Uses deviceProtectedStorage: readable before screen unlock (directBootAware).
 */
object AppPrefs {

    private const val FILE                  = "o2_prefs"
    private const val KEY_SERVICE_ENABLED   = "service_enabled"
    private const val KEY_ONBOARDING_DONE   = "onboarding_done"
    private const val KEY_LAST_REPLY_TIME   = "last_reply_time"   // duplicate prevention
    private const val KEY_LAST_REPLY_PHONE  = "last_reply_phone"  // duplicate prevention
    private const val KEY_LAST_SEND_OK      = "last_send_ok"      // delivery confirmation
    private const val KEY_TOTAL_REPLIES     = "total_replies"     // stats counter

    // Min gap between replies to the SAME number (prevents double-reply on split SMS)
    const val MIN_REPLY_GAP_MS = 30_000L  // 30 seconds

    private fun prefs(ctx: Context): SharedPreferences {
        val appCtx = ctx.applicationContext
        val storageCtx = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
            appCtx.createDeviceProtectedStorageContext()
        else appCtx
        return storageCtx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    // ── Service state ─────────────────────────────────────────────────────────
    fun isServiceEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SERVICE_ENABLED, false)

    fun setServiceEnabled(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_SERVICE_ENABLED, v).apply()

    // ── Onboarding ────────────────────────────────────────────────────────────
    fun isOnboardingDone(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_ONBOARDING_DONE, v).apply()

    // ── Duplicate prevention ──────────────────────────────────────────────────
    /**
     * Returns true if we should send a reply (not a duplicate).
     * Prevents replying twice to the same number within MIN_REPLY_GAP_MS.
     * Thread-safe via SharedPreferences.Editor commit order.
     */
    fun shouldReplyTo(ctx: Context, phone: String): Boolean {
        val p = prefs(ctx)
        val lastPhone = p.getString(KEY_LAST_REPLY_PHONE, "")
        val lastTime  = p.getLong(KEY_LAST_REPLY_TIME, 0L)
        val now       = System.currentTimeMillis()
        val tooSoon   = phone == lastPhone && (now - lastTime) < MIN_REPLY_GAP_MS
        return !tooSoon
    }

    fun recordReply(ctx: Context, phone: String) {
        prefs(ctx).edit()
            .putString(KEY_LAST_REPLY_PHONE, phone)
            .putLong(KEY_LAST_REPLY_TIME, System.currentTimeMillis())
            .putInt(KEY_TOTAL_REPLIES, getTotalReplies(ctx) + 1)
            .apply()
    }

    // ── Delivery confirmation ─────────────────────────────────────────────────
    fun setLastSendResult(ctx: Context, success: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_LAST_SEND_OK, success).apply()

    fun getLastSendResult(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_LAST_SEND_OK, false)

    // ── Stats ─────────────────────────────────────────────────────────────────
    fun getTotalReplies(ctx: Context): Int =
        prefs(ctx).getInt(KEY_TOTAL_REPLIES, 0)
}
