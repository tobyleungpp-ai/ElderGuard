package com.elderguard.safe

import android.app.Application

class ElderGuardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsManager.initialize(this)
    }
}
