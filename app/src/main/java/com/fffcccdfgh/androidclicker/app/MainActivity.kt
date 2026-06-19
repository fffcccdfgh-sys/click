package com.fffcccdfgh.androidclicker.app

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.fffcccdfgh.androidclicker.R
import com.fffcccdfgh.androidclicker.core.accessibility.AccessibilityServiceStatus
import com.fffcccdfgh.androidclicker.core.accessibility.ClickAccessibilityService
import com.fffcccdfgh.androidclicker.core.execution.ActionStep
import com.fffcccdfgh.androidclicker.core.ocr.OcrHelper
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenCaptureDisplayReader
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenCaptureManager
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenCapturePermissionActivity
import com.fffcccdfgh.androidclicker.feature.clicker.ScriptListActivity
import com.fffcccdfgh.androidclicker.feature.clicker.floating.FloatingControlService
import com.fffcccdfgh.androidclicker.feature.pvz.PvzGameScriptActivity

class MainActivity : AppCompatActivity() {

    private lateinit var openSettingsButton: TextView
    private lateinit var openOverlaySettingsButton: TextView
    private lateinit var grantScreenCaptureButton: TextView
    private lateinit var toggleFloatingButton: TextView
    private lateinit var gameScriptsButton: TextView
    private lateinit var myScriptsButton: TextView
    private lateinit var deviceResolutionText: TextView
    private var floatingTogglePending = false
    private val floatingStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == FloatingControlService.ACTION_STATE_CHANGED) {
                floatingTogglePending = false
                updateFloatingButtonState()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        OcrHelper.debugContext = applicationContext
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        openSettingsButton = findViewById(R.id.openSettingsButton)
        openOverlaySettingsButton = findViewById(R.id.openOverlaySettingsButton)
        grantScreenCaptureButton = findViewById(R.id.grantScreenCaptureButton)
        toggleFloatingButton = findViewById(R.id.toggleFloatingButton)
        gameScriptsButton = findViewById(R.id.gameScriptsButton)
        myScriptsButton = findViewById(R.id.myScriptsButton)
        deviceResolutionText = findViewById(R.id.deviceResolutionText)

        openSettingsButton.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                openAccessibilitySettings()
            }
        }

        openOverlaySettingsButton.setOnClickListener {
            if (!isOverlayPermissionGranted()) {
                openOverlaySettings()
            }
        }

        toggleFloatingButton.setOnClickListener {
            startFloatingService()
        }

        grantScreenCaptureButton.setOnClickListener {
            if (!ScreenCaptureManager.isReady) {
                startActivity(Intent(this, ScreenCapturePermissionActivity::class.java))
            }
        }

        myScriptsButton.setOnClickListener {
            startActivity(Intent(this, ScriptListActivity::class.java))
        }

        gameScriptsButton.setOnClickListener {
            startActivity(Intent(this, PvzGameScriptActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        restoreAccessibilityIfAllowed()
        val filter = IntentFilter(FloatingControlService.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(floatingStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(floatingStateReceiver, filter)
        }
        updateUI()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(floatingStateReceiver)
        } catch (_: Exception) {
        }
    }

    private fun updateUI() {
        updateAccessibilityStatus()
        updateOverlayStatus()
        updateScreenCaptureStatus()
        updateFloatingButtonState()
        updateDeviceResolution()
    }

    private fun updateAccessibilityStatus() {
        val enabled = isAccessibilityServiceEnabled()
        updatePermissionButton(openSettingsButton, enabled)
    }

    private fun updateOverlayStatus() {
        val granted = isOverlayPermissionGranted()
        updatePermissionButton(openOverlaySettingsButton, granted)
    }

    private fun updateScreenCaptureStatus() {
        updatePermissionButton(grantScreenCaptureButton, ScreenCaptureManager.isReady)
    }

    private fun updateFloatingButtonState() {
        toggleFloatingButton.isEnabled = isOverlayPermissionGranted() && !floatingTogglePending
        toggleFloatingButton.text = getString(R.string.start_floating_control)
    }

    private fun updateDeviceResolution() {
        val info = ScreenCaptureDisplayReader.current(this)
        deviceResolutionText.text = getString(
            R.string.main_device_resolution,
            info.width,
            info.height
        )
    }

    private fun updatePermissionButton(button: TextView, granted: Boolean) {
        button.text = if (granted) {
            getString(R.string.main_permission_granted)
        } else {
            getString(R.string.main_permission_open)
        }
        button.setTextColor(
            if (granted) {
                0xFF16A34A.toInt()
            } else {
                0xFF2563EB.toInt()
            }
        )
        button.background = ContextCompat.getDrawable(
            this,
            if (granted) {
                R.drawable.main_permission_done_bg
            } else {
                R.drawable.main_permission_action_bg
            }
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return AccessibilityServiceStatus.isEnabled(this)
    }

    private fun restoreAccessibilityIfAllowed() {
        try {
            val service = ComponentName(
                packageName,
                ClickAccessibilityService::class.java.name
            ).flattenToString()
            val old = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val services = old.split(":")
                .filter { it.isNotBlank() }
                .toMutableList()

            if (!services.contains(service)) {
                services.add(service)
                Settings.Secure.putString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    services.joinToString(":")
                )
            }

            Settings.Secure.putInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                1
            )
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun isOverlayPermissionGranted(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun startFloatingService() {
        floatingTogglePending = true
        updateFloatingButtonState()
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit()
            .putString("action_sequence", ActionStep.listToJson(emptyList()))
            .remove("current_editing_script_name")
            .putInt("loop_count", 1)
            .putLong("loop_gap_ms", 0L)
            .putBoolean("loop_settings_saved", true)
            .apply()
        val intent = Intent(this, FloatingControlService::class.java)
        startForegroundService(intent)
        scheduleFloatingStateFallbackRefresh()
    }

    private fun scheduleFloatingStateFallbackRefresh() {
        toggleFloatingButton.postDelayed({
            floatingTogglePending = false
            updateFloatingButtonState()
        }, 1200)
    }

    companion object {
        private const val PREFS_NAME = "tap_config"
    }
}
