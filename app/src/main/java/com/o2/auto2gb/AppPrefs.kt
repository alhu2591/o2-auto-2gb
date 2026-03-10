package com.o2.auto2gb

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent state using deviceProtectedStorage.
 * Readable before screen unlock (directBootAware receivers).
 */
object AppPrefs {

    private const val FILE                 = "o2_prefs"
    private const val KEY_SERVICE_ENABLED  = "service_enabled"
    private const val KEY_ONBOARDING_DONE  = "onboarding_done"
    private const val KEY_LAST_REPLY_TIME  = "last_reply_time"
    private const val KEY_LAST_REPLY_PHONE = "last_reply_phone"
    private const val KEY_LAST_SEND_OK     = "last_send_ok"
    private const val KEY_TOTAL_REPLIES    = "total_replies"

    // 30-second cooldown to prevent replying twice to the same split-SMS
    const val MIN_REPLY_GAP_MS = 30_000L

    private fun prefs(ctx: Context): SharedPreferences {
        val app = ctx.applicationContext
        val storage = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
            app.createDeviceProtectedStorageContext() else app
        return storage.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    // ── Service ───────────────────────────────────────────────────────────────
    fun isServiceEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SERVICE_ENABLED, false)

    fun setServiceEnabled(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_SERVICE_ENABLED, v).apply()

    // ── Onboarding ────────────────────────────────────────────────────────────
    fun isOnboardingDone(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_ONBOARDING_DONE, v).apply()

    // ── Duplicate guard ───────────────────────────────────────────────────────
    fun shouldReplyTo(ctx: Context, phone: String): Boolean {
        val p     = prefs(ctx)
        val last  = p.getString(KEY_LAST_REPLY_PHONE, "")
        val time  = p.getLong(KEY_LAST_REPLY_TIME, 0L)
        val now   = System.currentTimeMillis()
        return !(phone == last && (now - time) < MIN_REPLY_GAP_MS)
    }

    fun recordReply(ctx: Context, phone: String) {
        prefs(ctx).edit()
            .putString(KEY_LAST_REPLY_PHONE, phone)
            .putLong(KEY_LAST_REPLY_TIME, System.currentTimeMillis())
            .putInt(KEY_TOTAL_REPLIES, getTotalReplies(ctx) + 1)
            .apply()
    }

    // ── Last send result ──────────────────────────────────────────────────────
    fun setLastSendResult(ctx: Context, ok: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_LAST_SEND_OK, ok).apply()

    // ── Stats (read from normal storage — UI only) ────────────────────────────
    fun getTotalReplies(ctx: Context): Int =
        prefs(ctx).getInt(KEY_TOTAL_REPLIES, 0)

    fun getLastReplyTime(ctx: Context): Long =
        prefs(ctx).getLong(KEY_LAST_REPLY_TIME, 0L)
}
