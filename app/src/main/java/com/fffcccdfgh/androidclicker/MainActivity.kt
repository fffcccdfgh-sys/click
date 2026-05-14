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
    private lateinit var myScriptsButton: Button

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
        myScriptsButton = findViewById(R.id.myScriptsButton)

        openSettingsButton.setOnClickListener {
            openAccessibilitySettings()
        }

        openOverlaySettingsButton.setOnClickListener {
            openOverlaySettings()
        }

        toggleFloatingButton.setOnClickListener {
            toggleFloatingControl()
        }

        myScriptsButton.setOnClickListener {
            startActivity(Intent(this, ScriptListActivity::class.java))
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
        stopService(Intent(this, FloatingControlService::class.java))
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit()
            .putString("action_sequence", ActionStep.listToJson(emptyList()))
            .remove("current_editing_script_name")
            .putInt("loop_count", 1)
            .putLong("loop_gap_ms", 0L)
            .putBoolean("loop_settings_saved", true)
            .apply()
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
    }
}
