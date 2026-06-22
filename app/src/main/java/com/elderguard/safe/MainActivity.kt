package com.elderguard.safe

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elderguard.safe.ui.theme.ElderGuardTheme
import android.content.ComponentName
import android.text.TextUtils
import android.util.Log
import android.content.Context
import android.app.ActivityManager

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startVpnService()
        } else {
            Log.e("MainActivity", "VPN permission denied")
            SettingsManager.isVpnEnabled = false // Ensure internal state is false if permission denied
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ElderGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        requestVpnPermission = ::requestVpnPermission,
                        startVpnService = ::startVpnService,
                        stopVpnService = ::stopVpnService,
                        requestAccessibilityPermission = ::requestAccessibilityPermission,
                        requestNotificationPermission = ::requestNotificationPermission,
                        isAccessibilityServiceEnabledSystem = ::isAccessibilityServiceEnabledSystem,
                        isNotificationServiceEnabledSystem = ::isNotificationServiceEnabledSystem
                    )
                }
            }
        }
    }

    private fun requestVpnPermission() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, ElderVpnService::class.java)
        startService(intent)
        SettingsManager.isVpnEnabled = true
    }

    private fun stopVpnService() {
        val intent = Intent(this, ElderVpnService::class.java)
        intent.action = "stop"
        startService(intent)
        SettingsManager.isVpnEnabled = false
    }

    // Checks if the system-level Accessibility Service is enabled
    private fun isAccessibilityServiceEnabledSystem(): Boolean {
        val expectedComponentName = ComponentName(this, AdSkipAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(expectedComponentName) == true
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    // Checks if the system-level Notification Listener Service is enabled
    private fun isNotificationServiceEnabledSystem(): Boolean {
        val cn = ComponentName(this, ElderNotificationService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    private fun requestNotificationPermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }
}

@Composable
fun MainScreen(
    requestVpnPermission: () -> Unit,
    startVpnService: () -> Unit,
    stopVpnService: () -> Unit,
    requestAccessibilityPermission: () -> Unit,
    requestNotificationPermission: () -> Unit,
    isAccessibilityServiceEnabledSystem: () -> Boolean,
    isNotificationServiceEnabledSystem: () -> Boolean
) {
    // States for internal app toggles
    var vpnAppEnabled by remember { mutableStateOf(SettingsManager.isVpnEnabled) }
    var accessibilityAppEnabled by remember { mutableStateOf(SettingsManager.isAccessibilityEnabled) }
    var notificationAppEnabled by remember { mutableStateOf(SettingsManager.isNotificationEnabled) }

    // States for system-level permissions
    var accessibilitySystemEnabled by remember { mutableStateOf(isAccessibilityServiceEnabledSystem()) }
    var notificationSystemEnabled by remember { mutableStateOf(isNotificationServiceEnabledSystem()) }

    // Effect to update states when the composable is resumed or dependencies change
    LaunchedEffect(Unit) {
        while(true) {
            vpnAppEnabled = SettingsManager.isVpnEnabled
            accessibilityAppEnabled = SettingsManager.isAccessibilityEnabled
            notificationAppEnabled = SettingsManager.isNotificationEnabled
            accessibilitySystemEnabled = isAccessibilityServiceEnabledSystem()
            notificationSystemEnabled = isNotificationServiceEnabledSystem()
            kotlinx.coroutines.delay(1000L) // Update every second
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "ElderGuard",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // VPN Toggle
        ServiceToggleButton(
            label = "DNS 廣告攔截",
            isEnabled = vpnAppEnabled,
            onClick = {
                if (vpnAppEnabled) {
                    stopVpnService()
                } else {
                    requestVpnPermission()
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Accessibility Toggle
        ServiceToggleButton(
            label = "自動跳過廣告",
            isEnabled = accessibilityAppEnabled && accessibilitySystemEnabled,
            onClick = {
                if (!accessibilitySystemEnabled) {
                    requestAccessibilityPermission()
                } else {
                    SettingsManager.isAccessibilityEnabled = !SettingsManager.isAccessibilityEnabled
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Notification Toggle
        ServiceToggleButton(
            label = "通知與彈窗封鎖",
            isEnabled = notificationAppEnabled && notificationSystemEnabled,
            onClick = {
                if (!notificationSystemEnabled) {
                    requestNotificationPermission()
                } else {
                    SettingsManager.isNotificationEnabled = !SettingsManager.isNotificationEnabled
                }
            }
        )
    }
}

@Composable
fun ServiceToggleButton(label: String, isEnabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(80.dp)
    ) {
        Text(
            text = "$label: ",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Text(
            text = if (isEnabled) "已啟用" else "未啟用",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = if (isEnabled) Color.Green else Color.Red
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    ElderGuardTheme {
        MainScreen(
            requestVpnPermission = { /* Do nothing for preview */ },
            startVpnService = { /* Do nothing for preview */ },
            stopVpnService = { /* Do nothing for preview */ },
            requestAccessibilityPermission = { /* Do nothing for preview */ },
            requestNotificationPermission = { /* Do nothing for preview */ },
            isAccessibilityServiceEnabledSystem = { true },
            isNotificationServiceEnabledSystem = { true }
        )
    }
}
