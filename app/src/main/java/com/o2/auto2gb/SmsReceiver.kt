package com.o2.auto2gb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Works independently from SmsService.
 * Android wakes this receiver even if the app process is dead.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private val TARGET_SENDERS = setOf("+4980112", "80112", "4980112")
        private const val TRIGGER_WORD  = "weiter"
        private const val REPLY_MESSAGE = "Weiter"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        AppPrefs.init(context)

        // Acquire brief wake lock so process doesn't sleep mid-execution
        val wl = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "O2Auto2GB::ReceiverLock")
            ?.also { it.acquire(10_000L) }

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

            for (sms in messages) {
                val sender = sms.displayOriginatingAddress ?: continue
                val body   = sms.displayMessageBody ?: continue

                val senderMatch = TARGET_SENDERS.any { sender.contains(it, ignoreCase = true) }
                val bodyMatch   = body.contains(TRIGGER_WORD, ignoreCase = true)

                if (senderMatch && bodyMatch) {
                    try { abortBroadcast() } catch (_: Exception) {}
                    enqueueReply(context, sender, REPLY_MESSAGE)
                }
            }
        } finally {
            try { if (wl?.isHeld == true) wl.release() } catch (_: Exception) {}
        }
    }

    private fun enqueueReply(context: Context, phone: String, message: String) {
        val data = Data.Builder()
            .putString("phoneNumber", phone)
            .putString("message", message)
            .build()

        // No network constraint needed — SMS works without internet
        val request = OneTimeWorkRequestBuilder<SmsReplyWorker>()
            .setInputData(data)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag("sms_reply")
            .build()

        try {
            WorkManager.getInstance(context).enqueue(request)
        } catch (_: Exception) {
            // Fallback: send directly on receiver thread (risky but last resort)
            SmsReplyWorker.sendSmsDirectly(phone, message, context)
        }
    }
}
