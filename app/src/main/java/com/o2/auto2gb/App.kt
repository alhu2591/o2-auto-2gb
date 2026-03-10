package com.o2.auto2gb

import android.app.Application
import androidx.work.Configuration

class App : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        AppPrefs.init(this)
    }

    // WorkManager custom config for reliability
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.ERROR)
            .build()
}
