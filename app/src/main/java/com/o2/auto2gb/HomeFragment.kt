package com.o2.auto2gb

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        b.switchService.setOnCheckedChangeListener { _, checked ->
            if (checked) startService() else stopService()
            updateStatus(checked)
        }

        // Detect if service is running
        val running = SmsServiceState.isRunning
        b.switchService.isChecked = running
        updateStatus(running)
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

    private fun startService() {
        try {
            val i = Intent(requireContext(), SmsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                requireContext().startForegroundService(i)
            else requireContext().startService(i)
            SmsServiceState.isRunning = true
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun stopService() {
        requireContext().stopService(Intent(requireContext(), SmsService::class.java))
        SmsServiceState.isRunning = false
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

object SmsServiceState { var isRunning = false }
