package com.fffcccdfgh.androidclicker

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var overlayStatusText: TextView
    private lateinit var openSettingsButton: Button
    private lateinit var openOverlaySettingsButton: Button
    private lateinit var toggleFloatingButton: Button
    private lateinit var currentActionText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        statusText = findViewById(R.id.statusText)
        overlayStatusText = findViewById(R.id.overlayStatusText)
        openSettingsButton = findViewById(R.id.openSettingsButton)
        openOverlaySettingsButton = findViewById(R.id.openOverlaySettingsButton)
        toggleFloatingButton = findViewById(R.id.toggleFloatingButton)
        currentActionText = findViewById(R.id.currentActionText)

        openSettingsButton.setOnClickListener {
            openAccessibilitySettings()
        }

        openOverlaySettingsButton.setOnClickListener {
            openOverlaySettings()
        }

        toggleFloatingButton.setOnClickListener {
            toggleFloatingControl()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        updateAccessibilityStatus()
        updateOverlayStatus()
        updateFloatingButton()
        updateCurrentActionDisplay()
    }

    private fun updateAccessibilityStatus() {
        val enabled = isAccessibilityServiceEnabled()
        statusText.text = if (enabled) {
            getString(R.string.status_enabled)
        } else {
            getString(R.string.status_disabled)
        }
    }

    private fun updateOverlayStatus() {
        val granted = isOverlayPermissionGranted()
        overlayStatusText.text = if (granted) {
            getString(R.string.overlay_status_granted)
        } else {
            getString(R.string.overlay_status_denied)
        }
        openOverlaySettingsButton.visibility = if (granted) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun updateFloatingButton() {
        val serviceRunning = FloatingControlService.isRunning
        toggleFloatingButton.isEnabled = isOverlayPermissionGranted()
        toggleFloatingButton.text = if (serviceRunning) {
            getString(R.string.stop_floating_control)
        } else {
            getString(R.string.start_floating_control)
        }
    }

    private fun updateCurrentActionDisplay() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val actionType = prefs.getInt(KEY_ACTION_TYPE, ACTION_TYPE_NONE)

        currentActionText.text = when (actionType) {
            ACTION_TYPE_TAP -> {
                val x = prefs.getInt(KEY_TAP_X, NO_COORDINATE)
                val y = prefs.getInt(KEY_TAP_Y, NO_COORDINATE)
                if (x != NO_COORDINATE && y != NO_COORDINATE) {
                    getString(R.string.current_action_tap, x, y)
                } else {
                    getString(R.string.current_action_none)
                }
            }
            ACTION_TYPE_SWIPE -> {
                val sx = prefs.getInt(KEY_SWIPE_START_X, NO_COORDINATE)
                val sy = prefs.getInt(KEY_SWIPE_START_Y, NO_COORDINATE)
                val ex = prefs.getInt(KEY_SWIPE_END_X, NO_COORDINATE)
                val ey = prefs.getInt(KEY_SWIPE_END_Y, NO_COORDINATE)
                if (sx != NO_COORDINATE && sy != NO_COORDINATE && ex != NO_COORDINATE && ey != NO_COORDINATE) {
                    getString(R.string.current_action_swipe, sx, sy, ex, ey)
                } else {
                    getString(R.string.current_action_none)
                }
            }
            else -> getString(R.string.current_action_none)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = "$packageName/${ClickAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expectedComponent, ignoreCase = true) }
    }

    private fun isOverlayPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
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

    private fun toggleFloatingControl() {
        if (FloatingControlService.isRunning) {
            stopFloatingService()
        } else {
            startFloatingService()
        }
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingControlService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        toggleFloatingButton.text = getString(R.string.stop_floating_control)
    }

    private fun stopFloatingService() {
        val intent = Intent(this, FloatingControlService::class.java)
        stopService(intent)
        toggleFloatingButton.text = getString(R.string.start_floating_control)
    }

    companion object {
        private const val PREFS_NAME = "tap_config"
        const val KEY_ACTION_TYPE = "action_type"
        const val ACTION_TYPE_NONE = 0
        const val ACTION_TYPE_TAP = 1
        const val ACTION_TYPE_SWIPE = 2
        const val KEY_TAP_X = "tap_x"
        const val KEY_TAP_Y = "tap_y"
        const val KEY_SWIPE_START_X = "swipe_start_x"
        const val KEY_SWIPE_START_Y = "swipe_start_y"
        const val KEY_SWIPE_END_X = "swipe_end_x"
        const val KEY_SWIPE_END_Y = "swipe_end_y"
        const val NO_COORDINATE = -1
    }
}
