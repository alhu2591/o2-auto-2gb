package com.o2.auto2gb

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import androidx.work.Worker
import androidx.work.WorkerParameters

class SmsReplyWorker(
    private val ctx: Context,
    params: WorkerParameters
) : Worker(ctx, params) {

    override fun doWork(): Result {
        val phone   = inputData.getString("phoneNumber") ?: return Result.failure()
        val message = inputData.getString("message")     ?: return Result.failure()
        return try {
            sendSms(phone, message, ctx)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        fun sendSms(phone: String, message: String, context: Context) {
            val mgr: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION") SmsManager.getDefault()
            }
            mgr.sendTextMessage(phone, null, message, null, null)
        }
    }
}
