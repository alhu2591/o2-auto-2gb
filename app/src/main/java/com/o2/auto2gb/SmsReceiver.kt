package com.o2.auto2gb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * يُستدعى مباشرة من نظام Android لكل SMS وارد.
 * يعمل حتى لو كانت العملية مُغلقة — Android يُوقظها تلقائياً.
 * لا يحتاج foreground service ولا إشعارات.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        // أرقام O2 الألمانية للـ 2GB SMS
        private val TARGET_SENDERS = setOf("+4980112", "80112", "+4980112")
        private const val TRIGGER_WORD  = "weiter"
        private const val REPLY_MESSAGE = "Weiter"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        for (sms in messages) {
            val sender = sms.displayOriginatingAddress ?: continue
            val body   = sms.displayMessageBody       ?: continue

            val senderMatch = TARGET_SENDERS.any { sender.contains(it, ignoreCase = true) }
            val bodyMatch   = body.contains(TRIGGER_WORD, ignoreCase = true)

            if (senderMatch && bodyMatch) {
                // اعترض الـ broadcast لمنع ظهور إشعار SMS في تطبيقات الرسائل
                try { abortBroadcast() } catch (_: Exception) { }

                // أرسل الرد عبر WorkManager — موثوق ولا يحتاج foreground
                val data = Data.Builder()
                    .putString("phoneNumber", sender)
                    .putString("message", REPLY_MESSAGE)
                    .build()

                WorkManager.getInstance(context)
                    .enqueue(
                        OneTimeWorkRequestBuilder<SmsReplyWorker>()
                            .setInputData(data)
                            .build()
                    )
            }
        }
    }
}
