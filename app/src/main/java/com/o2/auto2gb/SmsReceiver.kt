package com.o2.auto2gb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Triggered by Android for every incoming SMS — even when app is dead.
 * directBootAware=true → fires before screen unlock after reboot.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        /**
         * All known O2 Germany sender IDs.
         * O2 sends from numeric short codes AND alphanumeric IDs
         * depending on message type and carrier routing.
         */
        private val TARGET_SENDERS = setOf(
            // Numeric (standard)
            "80112", "+4980112", "4980112",
            // Alphanumeric (O2 Germany)
            "O2", "O2online", "O2Germany",
            "o2", "o2online",
            // Short codes used in some regions
            "18081", "18082"
        )

        private const val TRIGGER_WORD  = "weiter"
        private const val REPLY_MESSAGE = "Weiter"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // Short WakeLock — just enough to enqueue WorkManager job
        val wl = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "O2Auto2GB::RxLock")
            ?.also { it.acquire(10_000L) }

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            for (sms in messages) {
                val sender = sms.displayOriginatingAddress ?: continue
                val body   = sms.displayMessageBody       ?: continue

                val senderMatch = TARGET_SENDERS.any { sender.contains(it, ignoreCase = true) }
                val bodyMatch   = body.contains(TRIGGER_WORD, ignoreCase = true)

                if (senderMatch && bodyMatch) {
                    // Block other apps from showing this message
                    try { abortBroadcast() } catch (_: Exception) {}

                    // Duplicate check — if we already replied in last 30s, skip
                    if (!AppPrefs.shouldReplyTo(context, sender)) continue

                    enqueueReply(context, sender)
                }
            }
        } finally {
            try { if (wl?.isHeld == true) wl.release() } catch (_: Exception) {}
        }
    }

    private fun enqueueReply(context: Context, phone: String) {
        val data = Data.Builder()
            .putString("phoneNumber", phone)
            .putString("message", REPLY_MESSAGE)
            .build()

        val request = OneTimeWorkRequestBuilder<SmsReplyWorker>()
            .setInputData(data)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag("sms_reply")
            .build()

        try {
            WorkManager.getInstance(context).enqueue(request)
        } catch (_: Exception) {
            // Last resort fallback
            try { SmsReplyWorker.sendSms(phone, REPLY_MESSAGE, context) } catch (_: Exception) {}
        }
    }
}
