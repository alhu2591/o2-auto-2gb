package com.o2.auto2gb

import android.Manifest
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set initial state without triggering listener
        b.switchService.isChecked = SmsServiceState.isRunning
        updateStatus(SmsServiceState.isRunning)

        b.switchService.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (!hasRequiredPermissions()) {
                    // Revert switch silently
                    b.switchService.isChecked = false
                    Toast.makeText(requireContext(),
                        "Please grant SMS permissions first", Toast.LENGTH_LONG).show()
                    return@setOnCheckedChangeListener
                }
                val success = startServiceSafe()
                if (!success) {
                    b.switchService.isChecked = false
                }
            } else {
                stopServiceSafe()
            }
            updateStatus(b.switchService.isChecked)
        }
    }

    override fun onResume() {
        super.onResume()
        // Sync switch state on resume
        b.switchService.isChecked = SmsServiceState.isRunning
        updateStatus(SmsServiceState.isRunning)
    }

    private fun hasRequiredPermissions(): Boolean {
        val ctx = requireContext()
        val sms = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED
        val send = ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED
        return sms && send
    }

    private fun startServiceSafe(): Boolean {
        return try {
            val ctx = requireContext()
            val intent = Intent(ctx, SmsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
            SmsServiceState.isRunning = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(),
                "Could not start service: ${e.message}", Toast.LENGTH_LONG).show()
            SmsServiceState.isRunning = false
            false
        }
    }

    private fun stopServiceSafe() {
        try {
            requireContext().stopService(Intent(requireContext(), SmsService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        SmsServiceState.isRunning = false
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
