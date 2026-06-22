package com.elderguard.safe

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {

    private const val PREFS_NAME = "ElderGuardPrefs"
    private const val KEY_VPN_ENABLED = "vpn_enabled"
    private const val KEY_ACCESSIBILITY_ENABLED = "accessibility_enabled"
    private const val KEY_NOTIFICATION_ENABLED = "notification_enabled"

    private lateinit var sharedPreferences: SharedPreferences

    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var isVpnEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_VPN_ENABLED, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_VPN_ENABLED, value).apply()

    var isAccessibilityEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_ACCESSIBILITY_ENABLED, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_ACCESSIBILITY_ENABLED, value).apply()

    var isNotificationEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_NOTIFICATION_ENABLED, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_NOTIFICATION_ENABLED, value).apply()
}
