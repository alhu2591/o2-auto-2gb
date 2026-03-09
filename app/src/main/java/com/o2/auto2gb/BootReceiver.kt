package com.o2.auto2gb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager

/**
 * يُنبّه WorkManager بعد إعادة تشغيل الجهاز.
 * SmsReceiver يعمل تلقائياً بعد الإقلاع لأنه مُسجَّل في الـ manifest —
 * هذا الـ receiver يضمن فقط أن WorkManager جاهز لأي مهام معلقة.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            // إعادة تهيئة WorkManager — يُكمل أي مهام رد معلقة من قبل الإقلاع
            WorkManager.getInstance(context)
        }
    }
}
