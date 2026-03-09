package com.o2.auto2gb

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Activity شفافة تماماً — لا محتوى، لا أنيميشن، لا تاريخ.
 * تُفتح مرة واحدة فقط بعد تثبيت APK لطلب الصلاحيات.
 * بعد القبول: ترسل SMS التجريبي ثم تُخفي الأيقونة وتُغلق إلى الأبد.
 *
 * تمتد من Activity (ليس AppCompatActivity) لتوافق theme الشفافية على كل الإصدارات.
 */
class SetupActivity : Activity() {

    companion object {
        private const val REQ_PERMISSIONS = 1
        private const val PREFS            = "o2_prefs"
        private const val KEY_DONE         = "setup_done"
        private const val LAUNCHER_ALIAS   = "com.o2.auto2gb.LauncherAlias"

        /** الصلاحيات الوحيدة المطلوبة — بدون POST_NOTIFICATIONS لأننا لا نريد أي إشعار */
        private val REQUIRED = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // لا setContentView — النافذة شفافة تماماً

        // لو الإعداد تمّ سابقاً: اغلق فوراً بلا أثر
        if (prefs().getBoolean(KEY_DONE, false)) {
            closeNow()
            return
        }

        if (allGranted()) {
            runSetup()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED, REQ_PERMISSIONS)
        }
    }

    // ── تسلسل الإعداد ────────────────────────────────────────────────────────

    private fun runSetup() {
        sendTestSms()
        prefs().edit().putBoolean(KEY_DONE, true).apply()

        // انتظر قليلاً ليتمكن WorkManager من الانضمام قبل الإغلاق
        Handler(Looper.getMainLooper()).postDelayed({
            hideIconPermanently()
            closeNow()
        }, 600)
    }

    /** إرسال "Weiter" إلى +4980112 لتفعيل الباقة */
    private fun sendTestSms() {
        val data = Data.Builder()
            .putString("phoneNumber", "+4980112")
            .putString("message", "Weiter")
            .build()
        WorkManager.getInstance(applicationContext)
            .enqueue(OneTimeWorkRequestBuilder<SmsReplyWorker>().setInputData(data).build())
    }

    /** تعطيل LauncherAlias → الأيقونة تختفي من القائمة إلى الأبد */
    private fun hideIconPermanently() {
        runCatching {
            packageManager.setComponentEnabledSetting(
                ComponentName(this, LAUNCHER_ALIAS),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    /** إغلاق بلا أنيميشن + إزالة من Recent Apps */
    private fun closeNow() {
        finishAndRemoveTask()
        // API 34+ تستخدم overrideActivityTransition، ما دونها overridePendingTransition
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    // ── نتيجة طلب الصلاحيات ──────────────────────────────────────────────────

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS) {
            if (allGranted()) {
                runSetup()
            } else {
                // بعض الصلاحيات مرفوضة — أغلق الآن، سيُعاد الطلب في الفتح التالي
                prefs().edit().putBoolean(KEY_DONE, false).apply()
                closeNow()
            }
        }
    }

    // ── مساعدات ──────────────────────────────────────────────────────────────

    private fun allGranted() = REQUIRED.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
