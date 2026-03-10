package com.o2.auto2gb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CoroutineWorker (replaces blocking Worker):
 * - Runs on IO dispatcher — no ANR risk
 * - Registers sent/delivered PendingIntents for delivery confirmation
 * - Checks duplicate guard before sending
 * - Records reply in AppPrefs for stats + duplicate prevention
 * - Retries up to 3 times with exponential backoff on failure
 */
class SmsReplyWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val phone   = inputData.getString("phoneNumber") ?: return@withContext Result.failure()
        val message = inputData.getString("message")     ?: return@withContext Result.failure()

        // ── Duplicate guard ────────────────────────────────────────────────
        if (!AppPrefs.shouldReplyTo(ctx, phone)) {
            // Already replied to this number recently — skip silently
            return@withContext Result.success()
        }

        return@withContext try {
            sendSms(phone, message, ctx)
            AppPrefs.recordReply(ctx, phone)
            // Show success notification (improvement #5)
            NotificationHelper.showReplySuccess(ctx, phone, message)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        /**
         * Send SMS with delivery confirmation PendingIntents.
         * sentIntent   → fires when SMS leaves device (carrier accepted it)
         * deliveredIntent → fires when SMS reaches recipient's phone
         */
        fun sendSms(phone: String, message: String, context: Context) {
            val mgr: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                context.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()

            val sentIntent = PendingIntent.getBroadcast(
                context, 0,
                Intent(SmsStatusReceiver.ACTION_SENT).apply {
                    setPackage(context.packageName)
                    putExtra(SmsStatusReceiver.EXTRA_PHONE, phone)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val deliveredIntent = PendingIntent.getBroadcast(
                context, 1,
                Intent(SmsStatusReceiver.ACTION_DELIVERED).apply {
                    setPackage(context.packageName)
                    putExtra(SmsStatusReceiver.EXTRA_PHONE, phone)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            mgr.sendTextMessage(phone, null, message, sentIntent, deliveredIntent)
        }
    }
}
