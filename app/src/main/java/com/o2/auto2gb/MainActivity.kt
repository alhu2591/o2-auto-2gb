package com.o2.auto2gb

import android.Manifest
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
        private const val TARGET_PHONE = "+4980112"
        private const val REPLY_MSG = "Weiter"
    }

    private val allPerms: Array<String> get() {
        val list = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        return list.toTypedArray()
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Just update UI - DO NOT start service here (would crash on Android 12+)
        updateUI()
        if (allGranted()) {
            snack(getString(R.string.snack_all_granted))
        } else {
            snack(getString(R.string.snack_need_perms))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrantPermissions.setOnClickListener {
            val missing = allPerms.filter { !isGranted(it) }.toTypedArray()
            if (missing.isNotEmpty()) permLauncher.launch(missing)
        }

        binding.btnTestSms.setOnClickListener {
            if (!isGranted(Manifest.permission.SEND_SMS)) {
                snack(getString(R.string.snack_need_send_perm))
                return@setOnClickListener
            }
            val data = Data.Builder()
                .putString("phoneNumber", TARGET_PHONE)
                .putString("message", REPLY_MSG)
                .build()
            WorkManager.getInstance(this)
                .enqueue(OneTimeWorkRequestBuilder<SmsReplyWorker>().setInputData(data).build())
            snack(getString(R.string.snack_test_sent))
        }

        binding.btnStartService.setOnClickListener {
            if (!allGranted()) {
                snack(getString(R.string.snack_grant_first))
                return@setOnClickListener
            }
            // Safe: called from foreground (user tap)
            startServiceSafe()
            snack(getString(R.string.snack_service_started))
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val smsOk   = isGranted(Manifest.permission.RECEIVE_SMS) && isGranted(Manifest.permission.READ_SMS)
        val sendOk  = isGranted(Manifest.permission.SEND_SMS)
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                          isGranted(Manifest.permission.POST_NOTIFICATIONS)
                      else true

        setRow(binding.tvPermSms,   binding.icPermSms,   smsOk)
        setRow(binding.tvPermSend,  binding.icPermSend,  sendOk)
        setRow(binding.tvPermNotif, binding.icPermNotif, notifOk)

        val all = allGranted()
        binding.btnGrantPermissions.visibility = if (all) View.GONE else View.VISIBLE

        if (all) {
            binding.tvStatus.text = getString(R.string.status_active)
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.success))
            binding.statusDot.setBackgroundResource(R.drawable.dot_status)
        } else {
            binding.tvStatus.text = getString(R.string.status_inactive)
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.warning))
            binding.statusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.warning))
        }
    }

    private fun setRow(tv: TextView, iv: ImageView, ok: Boolean) {
        tv.text = if (ok) "✓" else "✗"
        tv.setTextColor(ContextCompat.getColor(this, if (ok) R.color.success else R.color.error))
        iv.setImageResource(if (ok) R.drawable.ic_check_circle else R.drawable.ic_error_circle)
    }

    private fun allGranted() = allPerms.all { isGranted(it) }
    private fun isGranted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun startServiceSafe() {
        try {
            val intent = Intent(this, SmsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            // Service already running or not available
        }
    }

    private fun snack(msg: String) {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.surface_variant))
            .setTextColor(ContextCompat.getColor(this, R.color.on_surface))
            .show()
    }
}
