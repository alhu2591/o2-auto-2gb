package com.o2.auto2gb

import android.app.Application
import androidx.work.Configuration

class App : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        // No AppPrefs.init() needed — AppPrefs now takes context directly
    }

    // Provide WorkManager config — prevents double-init conflict with content provider
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.ERROR)
            .build()
}
