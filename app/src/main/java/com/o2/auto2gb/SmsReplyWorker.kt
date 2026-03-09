package com.o2.auto2gb

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * WorkManager worker — يُرسل SMS الرد بشكل موثوق في الخلفية.
 * يُعيد المحاولة تلقائياً عند الفشل المؤقت (شبكة، SIM غير جاهزة).
 */
class SmsReplyWorker(
    private val appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val phone   = inputData.getString("phoneNumber") ?: return Result.failure()
        val message = inputData.getString("message")     ?: return Result.failure()

        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appContext.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            smsManager.sendTextMessage(phone, null, message, null, null)
            Result.success()
        } catch (e: Exception) {
            // أعد المحاولة حتى 3 مرات إذا فشل مؤقتاً
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
