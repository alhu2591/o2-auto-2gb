package com.o2.auto2gb

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log

/**
 * Receives delivery confirmations for sent SMS messages.
 * Registered dynamically (not in Manifest) — lifecycle tied to the send operation.
 */
class SmsStatusReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SENT      = "com.o2.auto2gb.SMS_SENT"
        const val ACTION_DELIVERED = "com.o2.auto2gb.SMS_DELIVERED"
        const val EXTRA_PHONE      = "phone"
        const val TAG              = "O2AutoSMS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val phone = intent.getStringExtra(EXTRA_PHONE) ?: "unknown"
        when (intent.action) {
            ACTION_SENT -> {
                val statusText = when (resultCode) {
                    Activity.RESULT_OK                         -> "✓ Sent"
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE    -> "✗ Generic failure"
                    SmsManager.RESULT_ERROR_NO_SERVICE         -> "✗ No service"
                    SmsManager.RESULT_ERROR_NULL_PDU           -> "✗ Null PDU"
                    SmsManager.RESULT_ERROR_RADIO_OFF          -> "✗ Radio off"
                    else                                       -> "✗ Unknown error ($resultCode)"
                }
                Log.i(TAG, "SMS to $phone: $statusText")
                // Persist last send result for UI
                AppPrefs.setLastSendResult(context, resultCode == Activity.RESULT_OK)
            }
            ACTION_DELIVERED -> {
                Log.i(TAG, "SMS delivered to $phone confirmed by carrier")
            }
        }
    }
}
