package com.elderguard.safe

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import android.app.AlertDialog
import android.content.Context
import android.view.WindowManager

class AdSkipAccessibilityService : AccessibilityService() {

    private var lastEventType: Int = -1
    private var lastEventTime: Long = 0L
    private var lastPackageName: CharSequence? = null

    companion object {
        private const val TAG = "AdSkipService"
        private const val CLICK_DELAY_MS = 500L // Delay to prevent rapid clicks
        private val AD_KEYWORDS = listOf("跳過", "Skip", "廣告", "AD", "Close", "關閉", "安裝", "下載")
        private val BLOCK_PACKAGES = listOf("com.android.vending", "com.google.android.browser", "com.android.chrome")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!SettingsManager.isAccessibilityEnabled) {
            return
        }
        event?.apply {
            Log.d(TAG, "Event: ${AccessibilityEvent.eventTypeToString(eventType)}, Package: $packageName, Class: $className")

            // Prevent rapid processing of similar events
            if (eventType == lastEventType && System.currentTimeMillis() - lastEventTime < CLICK_DELAY_MS) {
                return
            }
            lastEventType = eventType
            lastEventTime = System.currentTimeMillis()
            lastPackageName = packageName

            when (eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    val rootNode = rootInActiveWindow
                    rootNode?.let { node ->
                        // Check for ad skipping buttons
                        findAndClickAdButtons(node)

                        // Check for mis-touch prevention (e.g., navigating to Play Store)
                        if (packageName != null && BLOCK_PACKAGES.contains(packageName.toString())) {
                            Log.w(TAG, "Detected navigation to blocked package: $packageName. Preventing mis-touch.")
                            // Optionally show a warning or navigate back
                            showWarningDialog("偵測到危險下載，已幫您攔截！")
                            performGlobalAction(GLOBAL_ACTION_BACK)
                        }
                    }
                }
            }
        }
    }

    private fun findAndClickAdButtons(node: AccessibilityNodeInfo) {
        for (keyword in AD_KEYWORDS) {
            val nodes = node.findAccessibilityNodeInfosByText(keyword)
            if (nodes != null && nodes.isNotEmpty()) {
                for (n in nodes) {
                    if (n.isClickable && n.isVisibleToUser) {
                        Log.i(TAG, "Clicking ad-related button with text: $keyword")
                        n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return // Clicked one, no need to find more for this event
                    }
                }
            }
        }

        // Additionally, check for common button IDs if text search is not enough
        // This requires knowing specific app IDs, which is harder to generalize.
        // Example: val skipButton = node.findAccessibilityNodeInfosByViewId("com.youtube.android:id/skip_ad_button")
        // if (skipButton != null && skipButton.isNotEmpty()) { ... }
    }

    private fun showWarningDialog(message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(message)
            .setPositiveButton("確定") { dialog, _ ->
                dialog.dismiss()
            }
        val dialog = builder.create()
        // Ensure the dialog can be shown over other apps
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        dialog.show()
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted.")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
            // Set specific package names to listen to, or leave null for all apps
            // packageNames = arrayOf("com.google.android.youtube", "com.android.vending", "com.android.chrome")
        }
        this.serviceInfo = info
        Log.i(TAG, "Accessibility service connected.")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Accessibility service destroyed.")
    }
}
