package com.o2.auto2gb

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.snackbar.Snackbar
import com.o2.auto2gb.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val PREFS = "o2_prefs"
        private const val KEY_DONE = "setup_done"
        private const val TARGET_PHONE = "+4980112"
        private const val REPLY_MSG = "Weiter"
    }

    private val requiredPermissions: List<String> get() = buildList {
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updateUI()
        if (allGranted()) {
            startSmsService()
            showSnackbar("✓ Alle Berechtigungen erteilt — Service läuft!")
            getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_DONE, true).apply()
        } else {
            showSnackbar("Bitte alle Berechtigungen erteilen")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupButtons()
        updateUI()
        if (allGranted()) startSmsService()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun setupButtons() {
        binding.btnGrantPermissions.setOnClickListener {
            val missing = requiredPermissions.filter { !isGranted(it) }
            permissionLauncher.launch(missing.toTypedArray())
        }

        binding.btnTestSms.setOnClickListener {
            if (!isGranted(Manifest.permission.SEND_SMS)) {
                showSnackbar("⚠ Bitte zuerst SMS-Berechtigung erteilen")
                return@setOnClickListener
            }
            val data = Data.Builder()
                .putString("phoneNumber", TARGET_PHONE)
                .putString("message", REPLY_MSG)
                .build()
            WorkManager.getInstance(this)
                .enqueue(OneTimeWorkRequestBuilder<SmsReplyWorker>().setInputData(data).build())
            showSnackbar("📤 Test-SMS wird gesendet an $TARGET_PHONE")
        }

        binding.btnStartService.setOnClickListener {
            if (!allGranted()) {
                showSnackbar("⚠ Zuerst alle Berechtigungen erteilen")
                return@setOnClickListener
            }
            startSmsService()
            showSnackbar("▶ Service gestartet")
        }
    }

    private fun updateUI() {
        val smsOk  = isGranted(Manifest.permission.RECEIVE_SMS) && isGranted(Manifest.permission.READ_SMS)
        val sendOk = isGranted(Manifest.permission.SEND_SMS)
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            isGranted(Manifest.permission.POST_NOTIFICATIONS) else true

        setPermRow(binding.tvPermSms,   binding.icPermSms,   smsOk)
        setPermRow(binding.tvPermSend,  binding.icPermSend,  sendOk)
        setPermRow(binding.tvPermNotif, binding.icPermNotif, notifOk)

        val all = allGranted()
        binding.btnGrantPermissions.visibility = if (all) View.GONE else View.VISIBLE

        if (all) {
            binding.tvStatus.text = "Service aktiv — überwacht O2 SMS"
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.success))
        } else {
            binding.tvStatus.text = "Berechtigungen erforderlich"
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.warning))
        }
    }

    private fun setPermRow(tv: TextView, iv: ImageView, granted: Boolean) {
        if (granted) {
            tv.text = "✓"
            tv.setTextColor(ContextCompat.getColor(this, R.color.success))
            iv.setImageResource(R.drawable.ic_check_circle)
        } else {
            tv.text = "✗"
            tv.setTextColor(ContextCompat.getColor(this, R.color.error))
            iv.setImageResource(R.drawable.ic_error_circle)
        }
    }

    private fun allGranted() = requiredPermissions.all { isGranted(it) }

    private fun isGranted(perm: String) =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    private fun startSmsService() {
        val intent = Intent(this, SmsService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun showSnackbar(msg: String) {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.surface_variant))
            .setTextColor(ContextCompat.getColor(this, R.color.on_surface))
            .show()
    }
}
