package com.o2.auto2gb

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.o2.auto2gb.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var b: ActivityOnboardingBinding
    private var currentStep = 0

    data class Step(
        val titleRes: Int, val subRes: Int, val descRes: Int,
        val iconRes: Int, val permission: String?, val labelRes: Int
    )

    private val steps by lazy {
        listOf(
            Step(R.string.onb_step1_title, R.string.onb_step1_sub, R.string.onb_step1_desc,
                R.drawable.ic_onb_sms,
                Manifest.permission.RECEIVE_SMS, R.string.step_sms),
            Step(R.string.onb_step2_title, R.string.onb_step2_sub, R.string.onb_step2_desc,
                R.drawable.ic_onb_sms,
                Manifest.permission.SEND_SMS, R.string.step_send),
            Step(R.string.onb_step3_title, R.string.onb_step3_sub, R.string.onb_step3_desc,
                R.drawable.ic_onb_sms,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    Manifest.permission.POST_NOTIFICATIONS else null,
                R.string.step_notif)
        )
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // After permission dialog, move to next step
        goNext()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Skip onboarding if already done
        val prefs = getSharedPreferences("o2_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("onboarding_done", false)) {
            startMain(); return
        }

        b = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnBack.setOnClickListener {
            if (currentStep > 0) { currentStep--; showStep() }
        }

        b.btnGrant.setOnClickListener { handleGrantClick() }

        showStep()
    }

    private fun showStep() {
        val step = steps[currentStep]

        // Back button visibility
        b.btnBack.visibility = if (currentStep > 0) View.VISIBLE else View.GONE

        // Step dots
        updateDots()

        // Content
        b.tvTitle.text = getString(step.titleRes)
        b.tvSubtitle.text = getString(step.subRes)
        b.tvDesc.text = Html.fromHtml(getString(step.descRes), Html.FROM_HTML_MODE_LEGACY)
        b.ivStepIcon.setImageResource(step.iconRes)

        // Step label
        b.tvStepLabel.text = getString(R.string.step_of,
            currentStep + 1, steps.size, getString(step.labelRes))

        // Button text
        val perm = step.permission
        val alreadyGranted = perm == null ||
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

        b.btnGrant.text = when {
            alreadyGranted && currentStep == steps.size - 1 -> getString(R.string.btn_finish)
            alreadyGranted -> getString(R.string.btn_next)
            else -> getString(R.string.btn_grant)
        }
    }

    private fun updateDots() {
        val dots = listOf(b.dot1, b.dot2, b.dot3)
        dots.forEachIndexed { i, dot ->
            dot.setBackgroundResource(
                if (i <= currentStep) R.drawable.step_dot_active else R.drawable.step_dot_inactive
            )
        }
    }

    private fun handleGrantClick() {
        val step = steps[currentStep]
        val perm = step.permission

        if (perm != null && ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            // Also request READ_SMS with RECEIVE_SMS on step 0
            val toRequest = if (currentStep == 0)
                arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
            else arrayOf(perm)
            permLauncher.launch(toRequest)
        } else {
            goNext()
        }
    }

    private fun goNext() {
        if (currentStep < steps.size - 1) {
            currentStep++
            showStep()
        } else {
            // All done
            getSharedPreferences("o2_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("onboarding_done", true).apply()
            startMain()
        }
    }

    private fun startMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
