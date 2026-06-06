package com.fffcccdfgh.androidclicker

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class PvzGameScriptActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pvz_game_script)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.pvzGameScriptRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<TextView>(R.id.pvzRunButton).setOnClickListener {
            startPvzFloatingControl()
        }
        findViewById<TextView>(R.id.pvzOpenScriptButton).setOnClickListener {
            startActivity(Intent(this, PvzScriptListActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateCalibrationStatuses()
    }

    private fun startPvzFloatingControl() {
        getSharedPreferences(PvzFloatingControlService.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .remove(PvzFloatingControlService.KEY_PROGRAM_CODE)
            .remove(PvzFloatingControlService.KEY_SCRIPT_NAME)
            .apply()
        val intent = Intent(this, PvzFloatingControlService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun updateCalibrationStatuses() {
        updateCalibrationStatus(R.id.pvzPlantSlotsCalibrationStatus, PvzCalibrationStorage.PLANT_SLOTS)
        updateCalibrationStatus(R.id.pvzBoardCalibrationStatus, PvzCalibrationStorage.BOARD)
        updateCalibrationStatus(R.id.pvzSunCalibrationStatus, PvzCalibrationStorage.SUN)
        updateCalibrationStatus(R.id.pvzPlantFoodCalibrationStatus, PvzCalibrationStorage.PLANT_FOOD)
        updateCalibrationStatus(R.id.pvzArtifactCalibrationStatus, PvzCalibrationStorage.ARTIFACT)
        updateCalibrationStatus(R.id.pvzCucumberCalibrationStatus, PvzCalibrationStorage.CUCUMBER)
        updateCalibrationStatus(R.id.pvzRechargeCalibrationStatus, PvzCalibrationStorage.RECHARGE)
        updateCalibrationStatus(R.id.pvzCardsCalibrationStatus, PvzCalibrationStorage.CARDS)
        updateCalibrationStatus(R.id.pvzOtherCalibrationStatus, PvzCalibrationStorage.OTHER)
        updateCalibrationStatus(R.id.pvzEndlessSupplyCalibrationStatus, PvzCalibrationStorage.ENDLESS_SUPPLY)
    }

    private fun updateCalibrationStatus(viewId: Int, key: String) {
        val statusView = findViewById<TextView>(viewId)
        val calibrated = PvzCalibrationStorage.isCalibrated(this, key)
        statusView.text = getString(
            if (calibrated) {
                R.string.pvz_calibration_calibrated
            } else {
                R.string.pvz_calibration_uncalibrated
            }
        )
        statusView.setTextColor(
            if (calibrated) {
                0xFF16A34A.toInt()
            } else {
                0xFF2563EB.toInt()
            }
        )
        statusView.background = ContextCompat.getDrawable(
            this,
            if (calibrated) {
                R.drawable.main_permission_done_bg
            } else {
                R.drawable.main_permission_action_bg
            }
        )
    }
}
