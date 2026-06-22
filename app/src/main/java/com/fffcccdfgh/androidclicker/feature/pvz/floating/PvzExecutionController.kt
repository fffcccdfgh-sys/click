package com.fffcccdfgh.androidclicker.feature.pvz.floating

import android.app.Service
import android.graphics.Color
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.fffcccdfgh.androidclicker.R
import com.fffcccdfgh.androidclicker.core.execution.ActionSequenceExecutor
import com.fffcccdfgh.androidclicker.core.execution.ActionStep
import com.fffcccdfgh.androidclicker.core.execution.ControlZoneChecker
import com.fffcccdfgh.androidclicker.core.execution.ExecutionStopButtonOverlay
import com.fffcccdfgh.androidclicker.feature.pvz.calibration.PvzCalibrationLuaBindings

class PvzExecutionController(
    private val service: Service,
    private val windowManagerProvider: () -> WindowManager?,
    private val floatingViewProvider: () -> View?,
    private val loadCurrentProgramCode: () -> String,
    private val hideEditor: () -> Unit,
    private val hideCalibrationPanel: () -> Unit,
    private val hideCalibrationPickerOverlay: () -> Unit,
    private val hideSaveConfirmPanel: () -> Unit,
    private val disableOverlayFocus: () -> Unit,
    private val setFloatingTouchThrough: (Boolean) -> Unit,
    private val controlZonePaddingPx: () -> Int
) {
    private var executionStopButton: ExecutionStopButtonOverlay? = null
    private var stopButtonPositioning = false

    fun executeCurrentProgram() {
        hideStopButtonPositionEditor()
        hideEditor()
        hideCalibrationPanel()
        hideCalibrationPickerOverlay()
        hideSaveConfirmPanel()
        disableOverlayFocus()

        val code = loadCurrentProgramCode()
        if (code.isBlank()) {
            Toast.makeText(service, R.string.pvz_program_empty, Toast.LENGTH_SHORT).show()
            return
        }

        ActionSequenceExecutor.loopCount = 1
        ActionSequenceExecutor.loopEnabled = false
        ActionSequenceExecutor.loopGapMs = 0L

        ActionSequenceExecutor.onStarted = {
            Log.d(STOP_DEBUG_TAG, "PvzExecutionController.onStarted fired")
            setFloatingTouchThrough(true)
            showExecutionStopButton()
            updateStartStopButtons(true)
        }
        ActionSequenceExecutor.onFinished = {
            Log.d(STOP_DEBUG_TAG, "PvzExecutionController.onFinished fired")
            hideExecutionStopButton()
            setFloatingTouchThrough(false)
            updateStartStopButtons(false)
        }
        ActionSequenceExecutor.onStopped = {
            Log.d(STOP_DEBUG_TAG, "PvzExecutionController.onStopped fired")
            hideExecutionStopButton()
            setFloatingTouchThrough(false)
            updateStartStopButtons(false)
        }

        val paddingPx = controlZonePaddingPx()
        ActionSequenceExecutor.execute(
            service,
            listOf(
                ActionStep(
                    type = ActionStep.TYPE_PROGRAM,
                    code = code,
                    delayBeforeMs = 1L,
                    repeatCount = 1
                )
            ),
            canDispatchAction = { action ->
                !ControlZoneChecker.isActionInAnyZone(action, paddingPx)
            },
            onBlocked = {
                Toast.makeText(service, R.string.action_overlaps_control_stopped, Toast.LENGTH_SHORT).show()
            },
            extraLuaGlobalRegistrars = listOf(PvzCalibrationLuaBindings)
        )
    }

    fun stopExecution() {
        ActionSequenceExecutor.stop()
    }

    fun toggleStopButtonPositioning() {
        if (ActionSequenceExecutor.isRunning) return
        if (stopButtonPositioning) {
            hideStopButtonPositionEditor()
        } else {
            showStopButtonPositionEditor()
        }
    }

    fun showStopButtonPositionEditor() {
        val wm = windowManagerProvider() ?: return
        hideEditor()
        hideCalibrationPanel()
        if (executionStopButton == null) {
            executionStopButton = ExecutionStopButtonOverlay(
                context = service,
                windowManager = wm,
                zoneKey = STOP_BUTTON_ZONE_KEY,
                onStop = { stopExecution() }
            )
        }
        executionStopButton?.showForPositioning()
        stopButtonPositioning = true
        updateStopPositionButton()
    }

    fun hideStopButtonPositionEditor() {
        if (!stopButtonPositioning) return
        if (!ActionSequenceExecutor.isRunning) {
            hideExecutionStopButton()
        }
        stopButtonPositioning = false
        updateStopPositionButton()
    }

    fun updateStopPositionButton() {
        val view = floatingViewProvider() ?: return
        val button = view.findViewById<TextView>(R.id.pvzStopPositionButton)
        button.text = if (stopButtonPositioning) {
            service.getString(R.string.stop_position_done)
        } else {
            service.getString(R.string.stop_position_action)
        }
    }

    fun showExecutionStopButton() {
        val wm = windowManagerProvider() ?: return
        if (executionStopButton == null) {
            executionStopButton = ExecutionStopButtonOverlay(
                context = service,
                windowManager = wm,
                zoneKey = STOP_BUTTON_ZONE_KEY,
                onStop = { stopExecution() }
            )
        }
        executionStopButton?.show()
    }

    fun hideExecutionStopButton() {
        executionStopButton?.hide()
    }

    fun updateStartStopButtons(running: Boolean) {
        val view = floatingViewProvider() ?: return
        val startButton = view.findViewById<TextView>(R.id.pvzStartButton)
        val collapsedStartButton = view.findViewById<TextView>(R.id.pvzCollapsedStartButton)
        if (running) {
            startButton.text = service.getString(R.string.stop_action)
            startButton.setTextColor(Color.WHITE)
            startButton.background = service.getDrawable(R.drawable.floating_pill_danger)
            collapsedStartButton.text = service.getString(R.string.stop_action)
            collapsedStartButton.setTextColor(Color.WHITE)
            collapsedStartButton.background = service.getDrawable(R.drawable.floating_pill_danger)
        } else {
            startButton.text = service.getString(R.string.start_action)
            startButton.setTextColor(Color.WHITE)
            startButton.background = service.getDrawable(R.drawable.floating_pill_primary)
            collapsedStartButton.text = service.getString(R.string.start_action)
            collapsedStartButton.setTextColor(Color.WHITE)
            collapsedStartButton.background = service.getDrawable(R.drawable.floating_pill_primary)
        }
    }

    private companion object {
        const val STOP_DEBUG_TAG = "ClickerStopDebug"
        const val STOP_BUTTON_ZONE_KEY = "pvz_execution_stop_button"
    }
}
