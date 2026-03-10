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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var switchListening = false

    companion object {
        private const val TRIGGER = "weiter"
        private const val REPLY   = "Weiter"
        private const val TARGET  = "+4980112"
        private val TIME_FMT = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val DATE_FMT = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.chipVersion.text = "v${BuildConfig.VERSION_NAME}"

        setupServiceCard()
        setupSimulation()
        promptBatteryOptimization()
    }

    override fun onResume() {
        super.onResume()
        syncServiceState()
        refreshStats()
        refreshBatteryStatus()
    }

    // ── Service Card ──────────────────────────────────────────────────────────
    private fun setupServiceCard() {
        syncServiceState()
        b.switchService.setOnCheckedChangeListener { _, checked ->
            if (!switchListening) return@setOnCheckedChangeListener
            if (checked) {
                if (!hasSmsPermissions()) {
                    setSwitch(false)
                    Toast.makeText(this, "Grant SMS permissions first", Toast.LENGTH_LONG).show()
                    return@setOnCheckedChangeListener
                }
                AppPrefs.setServiceEnabled(this, true)
                tryStartService()
            } else {
                stopEverything()
            }
            updateStatusUi()
        }
    }

    private fun syncServiceState() {
        setSwitch(AppPrefs.isServiceEnabled(this))
        updateStatusUi()
    }

    private fun setSwitch(on: Boolean) {
        switchListening = false
        b.switchService.isChecked = on
        switchListening = true
    }

    private fun updateStatusUi() {
        val enabled = AppPrefs.isServiceEnabled(this)
        val hasNotif = SmsService.hasNotificationPermission(this)
        val svcRunning = isServiceRunning()

        if (!enabled) {
            // Inactive
            b.cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant))
            b.statusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.on_surface_variant))
            b.tvStatus.text = getString(R.string.service_inactive)
            b.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant))
            b.chipMode.visibility = View.GONE
            return
        }

        // Active
        b.cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.success_container))
        b.statusDot.setBackgroundResource(R.drawable.dot_status)
        b.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.on_success))
        b.chipMode.visibility = View.VISIBLE

        if (!hasNotif) {
            b.tvStatus.text = getString(R.string.service_active_receiver_only)
            b.chipMode.text = getString(R.string.mode_sms_only)
            b.chipMode.setTextColor(ContextCompat.getColor(this, R.color.warning))
            b.chipMode.setChipBackgroundColorResource(R.color.surface_variant)
        } else {
            b.tvStatus.text = getString(R.string.service_active)
            b.chipMode.text = getString(R.string.mode_full)
            b.chipMode.setTextColor(ContextCompat.getColor(this, R.color.on_success))
            b.chipMode.setChipBackgroundColorResource(R.color.success_container)
        }
    }

    // ── Stats & Battery ───────────────────────────────────────────────────────
    private fun refreshStats() {
        val total = AppPrefs.getTotalReplies(this)
        b.chipTotalReplies.text = total.toString()

        val lastTime = AppPrefs.getLastReplyTime(this)
        b.chipLastReply.text = if (lastTime == 0L) {
            getString(R.string.stat_never)
        } else {
            // If today: "14:32", else "09 Mar, 14:32"
            val now = System.currentTimeMillis()
            val midnight = now - (now % 86_400_000L)
            if (lastTime >= midnight) TIME_FMT.format(Date(lastTime))
            else DATE_FMT.format(Date(lastTime))
        }
        b.chipLastReply.setTextColor(ContextCompat.getColor(this,
            if (lastTime == 0L) R.color.on_surface_variant else R.color.primary))
        b.chipLastReply.setChipBackgroundColorResource(
            if (lastTime == 0L) R.color.surface_variant else R.color.chip_keyword)
    }

    private fun refreshBatteryStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            b.rowBattery.visibility = View.GONE
            return
        }
        val pm = getSystemService(PowerManager::class.java)
        val exempt = pm?.isIgnoringBatteryOptimizations(packageName) ?: true

        b.chipBattery.text = getString(
            if (exempt) R.string.stat_battery_ok else R.string.stat_battery_warn_tap)
        b.chipBattery.setTextColor(ContextCompat.getColor(this,
            if (exempt) R.color.on_success else R.color.warning))
        b.chipBattery.setChipBackgroundColorResource(
            if (exempt) R.color.success_container else R.color.surface_variant)

        b.rowBattery.setOnClickListener {
            if (!exempt) openBatterySettings()
        }
    }

    private fun promptBatteryOptimization() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        val prefs = getSharedPreferences("o2_ui", Context.MODE_PRIVATE)
        if (prefs.getBoolean("battery_prompted", false)) return
        prefs.edit().putBoolean("battery_prompted", true).apply()
        openBatterySettings()
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            ).apply { data = Uri.parse("package:$packageName") })
        } catch (_: Exception) {}
    }

    // ── Simulation ────────────────────────────────────────────────────────────
    private fun setupSimulation() {
        checkMatch(b.etTrigger.text?.toString() ?: "")

        b.etTrigger.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { checkMatch(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        b.btnTestNow.setOnClickListener {
            val input = b.etTrigger.text?.toString()?.trim() ?: ""
            if (input.isEmpty()) {
                Toast.makeText(this, getString(R.string.toast_no_input), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val matches = input.contains(TRIGGER, ignoreCase = true)
            if (!matches) {
                addLogEntry(input, matched = false, sent = false)
                Toast.makeText(this, getString(R.string.toast_no_match), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val sent = trySendSms(TARGET, REPLY)
            addLogEntry(input, matched = true, sent = sent)
            Toast.makeText(this,
                if (sent) getString(R.string.toast_sent) else getString(R.string.toast_simulated),
                Toast.LENGTH_LONG).show()
        }

        b.btnClearLogs.setOnClickListener {
            b.logContainer.removeAllViews()
            b.cardEmptyLog.visibility = View.VISIBLE
        }
    }

    private fun checkMatch(text: String) {
        val matches = text.contains(TRIGGER, ignoreCase = true)
        b.cardMatchIndicator.visibility = View.VISIBLE
        if (matches) {
            b.cardMatchIndicator.setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.success_container))
            b.ivMatchIcon.setImageResource(R.drawable.ic_check_circle)
            b.ivMatchIcon.setColorFilter(ContextCompat.getColor(this, R.color.on_success))
            b.tvMatchText.text = getString(R.string.rule_matches)
            b.tvMatchText.setTextColor(ContextCompat.getColor(this, R.color.on_success))
        } else {
            b.cardMatchIndicator.setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.surface_variant))
            b.ivMatchIcon.setImageResource(R.drawable.ic_error_circle)
            b.ivMatchIcon.setColorFilter(ContextCompat.getColor(this, R.color.on_surface_variant))
            b.tvMatchText.text = getString(R.string.no_match)
            b.tvMatchText.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant))
        }
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

    private fun addLogEntry(incoming: String, matched: Boolean, sent: Boolean) {
        b.cardEmptyLog.visibility = View.GONE
        val row = ItemLogBinding.inflate(layoutInflater, b.logContainer, false)
        row.tvIncoming.text = incoming
        row.tvTime.text = TIME_FMT.format(Date())
        row.chipSuccess.visibility = View.VISIBLE

        when {
            sent -> {
                row.tvOutgoing.text = REPLY
                row.chipSuccess.text = getString(R.string.success_label)
                row.chipSuccess.setTextColor(ContextCompat.getColor(this, R.color.on_success))
                row.chipSuccess.setChipBackgroundColorResource(R.color.success_container)
            }
            matched -> {
                row.tvOutgoing.text = REPLY
                row.chipSuccess.text = "SIMULATED"
                row.chipSuccess.setTextColor(ContextCompat.getColor(this, R.color.primary))
                row.chipSuccess.setChipBackgroundColorResource(R.color.chip_keyword)
            }
            else -> {
                row.tvOutgoing.text = "—"
                row.chipSuccess.text = "NO MATCH"
                row.chipSuccess.setTextColor(ContextCompat.getColor(this, R.color.warning))
                row.chipSuccess.setChipBackgroundColorResource(R.color.surface_variant)
            }
        }
        b.logContainer.addView(row.root, 0)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun hasSmsPermissions() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun isServiceRunning() = try {
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(50).any { it.service.className == SmsService::class.java.name }
    } catch (_: Exception) { false }

    private fun tryStartService() = try {
        val i = Intent(this, SmsService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
    } catch (_: Exception) {}

    private fun stopEverything() {
        AppPrefs.setServiceEnabled(this, false)
        try { stopService(Intent(this, SmsService::class.java)) } catch (_: Exception) {}
        try { androidx.work.WorkManager.getInstance(this).cancelAllWorkByTag(SmsService.WATCHDOG_TAG) }
        catch (_: Exception) {}
    }
}
