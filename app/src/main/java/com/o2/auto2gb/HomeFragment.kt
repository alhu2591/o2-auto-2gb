package com.o2.auto2gb

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.o2.auto2gb.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private var listenerEnabled = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set switch state without triggering listener
        listenerEnabled = false
        val running = isServiceActuallyRunning()
        b.switchService.isChecked = running
        updateStatus(running)
        listenerEnabled = true

        b.switchService.setOnCheckedChangeListener { _, checked ->
            if (!listenerEnabled) return@setOnCheckedChangeListener

            if (checked) {
                if (!hasRequiredPermissions()) {
                    listenerEnabled = false
                    b.switchService.isChecked = false
                    listenerEnabled = true
                    Toast.makeText(requireContext(),
                        "Please grant SMS permissions first", Toast.LENGTH_LONG).show()
                    return@setOnCheckedChangeListener
                }
                val ok = startServiceSafe()
                if (!ok) {
                    listenerEnabled = false
                    b.switchService.isChecked = false
                    listenerEnabled = true
                }
                updateStatus(ok)
            } else {
                stopServiceSafe()
                updateStatus(false)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-sync UI with actual service state on every resume
        val running = isServiceActuallyRunning()
        listenerEnabled = false
        b.switchService.isChecked = running
        listenerEnabled = true
        updateStatus(running)
    }

    // ── Checks if service process is actually alive ──────────────────────────
    @Suppress("DEPRECATION")
    private fun isServiceActuallyRunning(): Boolean {
        return try {
            val am = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.getRunningServices(50).any {
                it.service.className == SmsService::class.java.name
            }
        } catch (_: Exception) {
            AppPrefs.isServiceEnabled  // fallback to pref
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val ctx = requireContext()
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun startServiceSafe(): Boolean {
        return try {
            val ctx = requireContext()
            val intent = Intent(ctx, SmsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ctx.startForegroundService(intent)
            else
                ctx.startService(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun stopServiceSafe() {
        try {
            AppPrefs.isServiceEnabled = false
            requireContext().stopService(Intent(requireContext(), SmsService::class.java))
            // Cancel watchdog too
            androidx.work.WorkManager.getInstance(requireContext())
                .cancelAllWorkByTag(SmsService.WATCHDOG_TAG)
        } catch (_: Exception) {}
    }

    private fun updateStatus(active: Boolean) {
        val ctx = requireContext()
        if (active) {
            b.tvStatus.text = getString(R.string.service_active)
            b.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.on_success))
            b.cardStatus.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.success_container))
            b.statusDot.setBackgroundResource(R.drawable.dot_status)
        } else {
            b.tvStatus.text = getString(R.string.service_inactive)
            b.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.on_surface_variant))
            b.cardStatus.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surface_variant))
            b.statusDot.setBackgroundColor(ContextCompat.getColor(ctx, R.color.on_surface_variant))
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

object SmsServiceState { var isRunning = false }
