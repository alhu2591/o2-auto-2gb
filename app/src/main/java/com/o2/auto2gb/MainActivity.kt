package com.o2.auto2gb

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
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
        promptBatteryOptimization()
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
                if (!hasSmsPermissions()) {
                    setSwitch(false)
                    Toast.makeText(this, "Grant SMS permissions first", Toast.LENGTH_LONG).show()
                    return@setOnCheckedChangeListener
                }
                // Enable regardless of notification permission —
                // SmsReceiver works even without Foreground Service
                AppPrefs.setServiceEnabled(this, true)
                // Try to start service (will skip gracefully if notifications blocked)
                tryStartService()
                updateStatus()
            } else {
                stopEverything()
                updateStatus()
            }
        }
    }

    private fun syncSwitch() {
        val enabled = AppPrefs.isServiceEnabled(this)
        setSwitch(enabled)
        updateStatus()
    }

    private fun setSwitch(on: Boolean) {
        listenerEnabled = false
        b.switchService.isChecked = on
        listenerEnabled = true
    }

    /**
     * Status reflects three states:
     * 1. Disabled — nothing running
     * 2. Receiver-only — no notification permission, SmsReceiver active
     * 3. Full service — foreground service + SmsReceiver active
     */
    private fun updateStatus() {
        val enabled = AppPrefs.isServiceEnabled(this)
        if (!enabled) {
            b.tvStatus.text = getString(R.string.service_inactive)
            b.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant))
            b.cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant))
            b.statusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.on_surface_variant))
            return
        }

        val hasNotifPerm = SmsService.hasNotificationPermission(this)
        val serviceRunning = isServiceRunning()

        when {
            serviceRunning -> {
                // Full mode: foreground service active
                b.tvStatus.text = getString(R.string.service_active)
                b.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.on_success))
                b.cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.success_container))
                b.statusDot.setBackgroundResource(R.drawable.dot_status)
            }
            !hasNotifPerm -> {
                // Receiver-only mode: works fine, just no persistent notification
                b.tvStatus.text = getString(R.string.service_active_receiver_only)
                b.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.on_success))
                b.cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.success_container))
                b.statusDot.setBackgroundResource(R.drawable.dot_status)
            }
            else -> {
                // Should be running but isn't yet (just toggled)
                b.tvStatus.text = getString(R.string.service_active)
                b.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.on_success))
                b.cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.success_container))
                b.statusDot.setBackgroundResource(R.drawable.dot_status)
            }
        }
    }

    // ── Battery Optimization ──────────────────────────────────────────────────
    private fun promptBatteryOptimization() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        val prefs = getSharedPreferences("o2_ui", Context.MODE_PRIVATE)
        if (prefs.getBoolean("battery_prompted", false)) return
        prefs.edit().putBoolean("battery_prompted", true).apply()
        try {
            startActivity(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (_: Exception) {}
    }

    // ── Test Section ──────────────────────────────────────────────────────────
    private fun setupTest() {
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
                addLogEntry(input, null)
                Toast.makeText(this, "No rule matches this trigger", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val sent = trySendSms(TARGET, REPLY)
            addLogEntry(input, if (sent) REPLY else null)
            Toast.makeText(this,
                if (sent) "✓ Sent \"$REPLY\" to $TARGET"
                else "Simulated — grant SEND_SMS permission to send real SMS",
                Toast.LENGTH_LONG).show()
        }
        b.btnClearLogs.setOnClickListener {
            b.logContainer.removeAllViews()
            b.cardEmptyLog.visibility = View.VISIBLE
        }
    }

    private fun checkMatch(text: String) {
        b.cardMatchIndicator.visibility =
            if (text.contains(TRIGGER, ignoreCase = true)) View.VISIBLE else View.GONE
    }

    private fun trySendSms(phone: String, message: String): Boolean {
        if (!hasSmsPermissions()) return false
        return try {
            val mgr: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            mgr.sendTextMessage(phone, null, message, null, null)
            true
        } catch (_: Exception) { false }
    }

    private fun addLogEntry(incoming: String, outgoing: String?) {
        b.cardEmptyLog.visibility = View.GONE
        val row = ItemLogBinding.inflate(layoutInflater, b.logContainer, false)
        row.tvIncoming.text = incoming
        row.tvTime.text     = getString(R.string.just_now)
        if (outgoing != null) {
            row.tvOutgoing.text = outgoing
            row.chipSuccess.visibility = View.VISIBLE
        } else {
            row.tvOutgoing.text = "—"
            row.chipSuccess.visibility = View.VISIBLE
            row.chipSuccess.text = "NO MATCH"
            row.chipSuccess.setTextColor(ContextCompat.getColor(this, R.color.warning))
            row.chipSuccess.setChipBackgroundColorResource(R.color.surface_variant)
        }
        b.logContainer.addView(row.root, 0)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun hasSmsPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean = try {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.getRunningServices(50).any { it.service.className == SmsService::class.java.name }
    } catch (_: Exception) { false }

    private fun tryStartService() {
        try {
            val i = Intent(this, SmsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i)
            else startService(i)
        } catch (_: Exception) {}
    }

    private fun stopEverything() {
        try {
            AppPrefs.setServiceEnabled(this, false)
            stopService(Intent(this, SmsService::class.java))
            androidx.work.WorkManager.getInstance(this).cancelAllWorkByTag(SmsService.WATCHDOG_TAG)
        } catch (_: Exception) {}
    }
}
