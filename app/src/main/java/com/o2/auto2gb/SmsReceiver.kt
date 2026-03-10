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
 * Triggered by Android system for every incoming SMS.
 * Works even if app process is completely dead — Android wakes it.
 * directBootAware=true → works before screen unlock after reboot.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private val TARGET_SENDERS = setOf("+4980112", "80112", "4980112")
        private const val TRIGGER_WORD  = "weiter"
        private const val REPLY_MESSAGE = "Weiter"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // Short WakeLock — just long enough to enqueue WorkManager task
        // WorkManager then handles sending on its own thread safely
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
                    // Block other apps (e.g. default SMS app) from showing this message
                    try { abortBroadcast() } catch (_: Exception) {}
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
            // Last resort: send directly (risky on main thread but better than missing reply)
            try { SmsReplyWorker.sendSms(phone, REPLY_MESSAGE, context) } catch (_: Exception) {}
        }
    }
}
