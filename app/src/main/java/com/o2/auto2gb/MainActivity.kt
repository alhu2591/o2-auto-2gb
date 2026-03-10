package com.o2.auto2gb

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.o2.auto2gb.databinding.ActivityMainBinding
import com.o2.auto2gb.databinding.ItemLogBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var listenerEnabled = false

    companion object {
        private const val TRIGGER = "weiter"
        private const val REPLY   = "Weiter"
        private const val TARGET  = "+4980112"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        setupServiceSwitch()
        setupTest()
    }

    override fun onResume() {
        super.onResume()
        syncSwitch()
    }

    // ── Service Switch ────────────────────────────────────────────────────────
    private fun setupServiceSwitch() {
        syncSwitch()

        b.switchService.setOnCheckedChangeListener { _, checked ->
            if (!listenerEnabled) return@setOnCheckedChangeListener
            if (checked) {
                if (!hasPermissions()) {
                    setSwitch(false)
                    Toast.makeText(this, "Grant SMS permissions first", Toast.LENGTH_LONG).show()
                    return@setOnCheckedChangeListener
                }
                val ok = startServiceSafe()
                if (!ok) setSwitch(false) else updateStatus(true)
            } else {
                stopServiceSafe()
                updateStatus(false)
            }
        }
    }

    private fun syncSwitch() {
        val running = isServiceRunning()
        setSwitch(running)
        updateStatus(running)
    }

    private fun setSwitch(on: Boolean) {
        listenerEnabled = false
        b.switchService.isChecked = on
        listenerEnabled = true
    }

    private fun updateStatus(active: Boolean) {
        if (active) {
            b.tvStatus.text = getString(R.string.service_active)
            b.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.on_success))
            b.cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.success_container))
            b.statusDot.setBackgroundResource(R.drawable.dot_status)
        } else {
            b.tvStatus.text = getString(R.string.service_inactive)
            b.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant))
            b.cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant))
            b.statusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.on_surface_variant))
        }
    }

    // ── Test Section ──────────────────────────────────────────────────────────
    private fun setupTest() {
        // Pre-fill with trigger word and show match indicator
        checkMatch(b.etTrigger.text?.toString() ?: "")

        b.etTrigger.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { checkMatch(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        b.btnTestNow.setOnClickListener {
            val input = b.etTrigger.text?.toString()?.trim() ?: ""
            if (input.isEmpty()) {
                Toast.makeText(this, "Enter a trigger message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val matches = input.contains(TRIGGER, ignoreCase = true)
            if (!matches) {
                Toast.makeText(this, "No rule matches this trigger", Toast.LENGTH_SHORT).show()
                addLogEntry(input, null)
                return@setOnClickListener
            }

            // Try to send real SMS
            val sent = trySendSms(TARGET, REPLY)
            addLogEntry(input, if (sent) REPLY else null)

            if (sent) {
                Toast.makeText(this, "✓ Reply sent to $TARGET", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "SMS permission missing — simulation only", Toast.LENGTH_LONG).show()
            }
        }

        b.btnClearLogs.setOnClickListener {
            b.logContainer.removeAllViews()
            b.cardEmptyLog.visibility = View.VISIBLE
        }
    }

    private fun checkMatch(text: String) {
        val matches = text.contains(TRIGGER, ignoreCase = true)
        b.cardMatchIndicator.visibility = if (matches) View.VISIBLE else View.GONE
    }

    private fun trySendSms(phone: String, message: String): Boolean {
        if (!hasPermissions()) return false
        return try {
            val mgr: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION") SmsManager.getDefault()
            }
            mgr.sendTextMessage(phone, null, message, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun addLogEntry(incoming: String, outgoing: String?) {
        b.cardEmptyLog.visibility = View.GONE
        val logB = ItemLogBinding.inflate(layoutInflater, b.logContainer, false)
        logB.tvIncoming.text = incoming
        logB.tvTime.text = getString(R.string.just_now)

        if (outgoing != null) {
            logB.tvOutgoing.text = outgoing
            logB.chipSuccess.visibility = View.VISIBLE
        } else {
            // No match — hide outgoing message bubble
            logB.tvOutgoing.text = "—"
            logB.chipSuccess.text = "NO MATCH"
            logB.chipSuccess.setTextColor(ContextCompat.getColor(this, R.color.warning))
            logB.chipSuccess.setChipBackgroundColorResource(R.color.surface_variant)
        }

        b.logContainer.addView(logB.root, 0)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun hasPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean = try {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.getRunningServices(50).any { it.service.className == SmsService::class.java.name }
    } catch (_: Exception) { AppPrefs.isServiceEnabled }

    private fun startServiceSafe(): Boolean = try {
        val i = Intent(this, SmsService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i)
        else startService(i)
        true
    } catch (e: Exception) {
        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        false
    }

    private fun stopServiceSafe() {
        try {
            AppPrefs.isServiceEnabled = false
            stopService(Intent(this, SmsService::class.java))
            androidx.work.WorkManager.getInstance(this).cancelAllWorkByTag(SmsService.WATCHDOG_TAG)
        } catch (_: Exception) {}
    }
}
