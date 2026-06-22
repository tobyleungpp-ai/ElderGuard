package com.elderguard.safe

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.WindowManager
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.LinearLayout
import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings

class ElderNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "ElderNotificationService"
        private val PROMOTIONAL_KEYWORDS = listOf("廣告", "優惠", "促銷", "賺錢", "紅包", "抽獎", "免費", "領取", "點擊", "下載", "安裝")
        private val WHITELISTED_PACKAGES = listOf("com.tencent.mm", "com.linecorp.mmd", "com.facebook.orca", "com.whatsapp") // WeChat, Line, Messenger, WhatsApp
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (!SettingsManager.isNotificationEnabled) {
            return
        }
        sbn?.let {
            val packageName = it.packageName
            val notification = it.notification
            val title = notification.extras.getString(Notification.EXTRA_TITLE)
            val text = notification.extras.getString(Notification.EXTRA_TEXT)

            Log.d(TAG, "Notification Posted: Package=$packageName, Title=$title, Text=$text")

            // Check if the package is whitelisted for notifications
            if (WHITELISTED_PACKAGES.contains(packageName)) {
                Log.d(TAG, "Whitelisted package notification, not blocking.")
                return
            }

            // Check for promotional keywords in title or text
            val isPromotional = PROMOTIONAL_KEYWORDS.any { keyword ->
                title?.contains(keyword, ignoreCase = true) == true ||
                text?.contains(keyword, ignoreCase = true) == true
            }

            if (isPromotional) {
                Log.i(TAG, "Blocking promotional notification from $packageName: Title=\"$title\", Text=\"$text\"")
                cancelNotification(sbn.key)
            }

            // Overlay Monitor: Check if non-whitelisted app has SYSTEM_ALERT_WINDOW permission
            // Note: Actively terminating an overlay as it appears is complex and often requires root or system privileges.
            // This check focuses on identifying apps that *have* the permission and *might* abuse it.
            if (!WHITELISTED_PACKAGES.contains(packageName) && hasSystemAlertWindowPermission(packageName)) {
                Log.w(TAG, "Non-whitelisted app '$packageName' has SYSTEM_ALERT_WINDOW permission. Potential for abusive overlays.")
                // In a real app, you might want to alert the user or log this for family review.
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let {
            Log.d(TAG, "Notification Removed: Package=${it.packageName}, ID=${it.id}")
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification Listener connected.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification Listener disconnected.")
    }

    private fun hasSystemAlertWindowPermission(packageName: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val packageManager = applicationContext.packageManager
                val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
                val declaredPermissions = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
                declaredPermissions?.any { it == "android.permission.SYSTEM_ALERT_WINDOW" } == true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        } else {
            // SYSTEM_ALERT_WINDOW permission behavior is different on older Android versions
            false
        }
    }
}
