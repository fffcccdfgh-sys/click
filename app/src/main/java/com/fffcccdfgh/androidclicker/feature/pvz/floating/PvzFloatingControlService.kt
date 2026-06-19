package com.fffcccdfgh.androidclicker.feature.pvz.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import com.fffcccdfgh.androidclicker.R
import com.fffcccdfgh.androidclicker.core.execution.ActionSequenceExecutor
import com.fffcccdfgh.androidclicker.core.execution.ControlZoneChecker
import com.fffcccdfgh.androidclicker.feature.pvz.PvzGameScriptActivity

class PvzFloatingControlService : Service() {
    private val stopDebugTag = "ClickerStopDebug"

    private var windowManager: WindowManager? = null
    private lateinit var scriptSessionController: PvzScriptSessionController
    private lateinit var usbSyncController: PvzUsbSyncController
    private lateinit var executionController: PvzExecutionController
    private lateinit var calibrationFlowController: PvzCalibrationFlowController
    private lateinit var floatingWindowController: PvzFloatingWindowController
    private lateinit var programEditorController: PvzProgramEditorController
    private lateinit var calibrationPanelController: PvzCalibrationPanelController

    override fun onCreate() {
        super.onCreate()
        scriptSessionController = PvzScriptSessionController(
            context = this,
            updateTitle = { floatingWindowController.updateFloatingTitle() }
        )
        calibrationFlowController = PvzCalibrationFlowController(
            service = this,
            windowManagerProvider = { windowManager },
            overlayType = { floatingWindowController.overlayType() },
            refreshCalibrationPanelStatuses = { calibrationPanelController.refreshCalibrationPanelStatuses() }
        )
        floatingWindowController = PvzFloatingWindowController(
            service = this,
            windowManagerProvider = { windowManager },
            currentProgramScreenSize = { calibrationFlowController.currentProgramScreenSize() },
            currentTitleProvider = {
                scriptSessionController.getCurrentScriptName()
                    ?: getString(R.string.pvz_default_script_name)
            },
            executeCurrentProgram = { executionController.executeCurrentProgram() },
            showEditor = { programEditorController.showProgramEditor() },
            toggleCalibrationPanel = { calibrationPanelController.toggleCalibrationPanel() },
            showSavePanel = { programEditorController.showSaveConfirmPanel() },
            hideSavePanel = { programEditorController.hideSaveConfirmPanel() },
            toggleStopButtonPositioning = { executionController.toggleStopButtonPositioning() },
            updateStopPositionButton = { executionController.updateStopPositionButton() },
            stopExecution = { executionController.stopExecution() },
            stopService = { stopSelf() }
        )
        programEditorController = PvzProgramEditorController(
            service = this,
            scriptSessionController = scriptSessionController,
            windowManagerProvider = { windowManager },
            hideCalibrationPanel = { calibrationPanelController.hideCalibrationPanel() },
            hideCalibrationPickerOverlayCallback = { reveal -> calibrationFlowController.hideCalibrationPickerOverlay(reveal) },
            currentProgramScreenSize = { calibrationFlowController.currentProgramScreenSize() },
            createFullScreenPickerParams = { calibrationFlowController.createFullScreenPickerParams() },
            overlayType = { floatingWindowController.overlayType() },
            dpCallback = { value -> floatingWindowController.dp(value) },
            roundedRect = { fill, stroke, radius -> floatingWindowController.roundedRect(fill, stroke, radius) },
            bindPanelDragCallback = { handle, panel, params -> floatingWindowController.bindPanelDrag(handle, panel, params) },
            showPanelCallback = { panel, params, zoneKey, viewProvider, paramsProvider ->
                floatingWindowController.showPanel(panel, params, zoneKey, viewProvider, paramsProvider)
            },
            removePanelCallback = { view, zoneKey, onRemoved -> floatingWindowController.removePanel(view, zoneKey, onRemoved) }
        )
        calibrationPanelController = PvzCalibrationPanelController(
            service = this,
            windowManagerProvider = { windowManager },
            calibrationFlowController = calibrationFlowController,
            overlayType = { floatingWindowController.overlayType() },
            dp = { value -> floatingWindowController.dp(value) },
            bindPanelDragCallback = { handle, panel, params -> floatingWindowController.bindPanelDrag(handle, panel, params) },
            showPanelCallback = { panel, params, zoneKey, viewProvider, paramsProvider ->
                floatingWindowController.showPanel(panel, params, zoneKey, viewProvider, paramsProvider)
            },
            removePanelCallback = { view, zoneKey, onRemoved -> floatingWindowController.removePanel(view, zoneKey, onRemoved) }
        )
        executionController = PvzExecutionController(
            service = this,
            windowManagerProvider = { windowManager },
            floatingViewProvider = { floatingWindowController.floatingView },
            loadCurrentProgramCode = { scriptSessionController.loadCurrentProgramCode() },
            hideEditor = { programEditorController.hideEditor() },
            hideCalibrationPanel = { calibrationPanelController.hideCalibrationPanel() },
            hideCalibrationPickerOverlay = {
                programEditorController.removePvzProgramAssistOverlay()
                calibrationFlowController.hideCalibrationPickerOverlay(revealOverlays = true)
            },
            hideSaveConfirmPanel = { programEditorController.hideSaveConfirmPanel() },
            disableOverlayFocus = { floatingWindowController.disableOverlayFocus() },
            setFloatingTouchThrough = { enabled -> floatingWindowController.setFloatingTouchThrough(enabled) },
            controlZonePaddingPx = { ControlZoneChecker.dpToPx(resources.displayMetrics.density) }
        )
        usbSyncController = PvzUsbSyncController(
            service = this,
            windowManagerProvider = { windowManager },
            saveProgram = { name, code -> scriptSessionController.saveCurrentProgramCode(name, code) },
            updateTitle = { floatingWindowController.updateFloatingTitle() },
            currentScreenSize = { calibrationFlowController.currentProgramScreenSize() },
            overlayType = { floatingWindowController.overlayType() },
            dp = { value -> floatingWindowController.dp(value) },
            bindPanelDrag = { handle, panel, params -> floatingWindowController.bindPanelDrag(handle, panel, params) },
            showPanel = { panel, params, zoneKey, viewProvider, paramsProvider ->
                floatingWindowController.showPanel(panel, params, zoneKey, viewProvider, paramsProvider)
            },
            removePanel = { view, zoneKey, onRemoved -> floatingWindowController.removePanel(view, zoneKey, onRemoved) },
            editorCodeInputProvider = { programEditorController.editorCodeInput() }
        )
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_EXECUTION) {
            Log.d(stopDebugTag, "PvzFloatingControlService notification stop clicked")
            executionController.stopExecution()
            return START_NOT_STICKY
        }
        val openEditorAfterStart = intent?.action == ACTION_OPEN_EDITOR

        startForeground(NOTIFICATION_ID, createNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingWindowController.hideFloatingControl()
        programEditorController.hideEditor()
        programEditorController.removePvzProgramAssistOverlay()
        calibrationPanelController.hideCalibrationPanel()
        calibrationFlowController.hideCalibrationPickerOverlay(revealOverlays = true)
        executionController.hideExecutionStopButton()
        floatingWindowController.showFloatingControl()
        if (openEditorAfterStart) {
            programEditorController.showProgramEditor()
        }
        usbSyncController.start()
        isRunning = true

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        floatingWindowController.updateFloatingControlSizeForCurrentDisplay()
        programEditorController.updateProgramEditorSizeForCurrentDisplay()
        programEditorController.updateProgramTemplatePanelSizeForCurrentDisplay()
        calibrationPanelController.updateCalibrationPanelSizeForCurrentDisplay()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(stopDebugTag, "PvzFloatingControlService.onDestroy")
        if (ActionSequenceExecutor.isRunning) {
            executionController.stopExecution()
        }
        programEditorController.hideEditor()
        programEditorController.removePvzProgramAssistOverlay()
        calibrationPanelController.hideCalibrationPanel()
        calibrationFlowController.hideCalibrationPickerOverlay(revealOverlays = true)
        usbSyncController.hideConfirmPanel()
        executionController.hideExecutionStopButton()
        floatingWindowController.hideFloatingControl()
        usbSyncController.stop()
        isRunning = false
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.pvz_floating_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val openIntent = Intent(this, PvzGameScriptActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, PvzFloatingControlService::class.java).apply {
            action = ACTION_STOP_EXECUTION
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.pvz_floating_notification_title))
            .setContentText(getString(R.string.pvz_floating_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.notification_stop), pendingStop)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "pvz_floating_control_channel"
        const val NOTIFICATION_ID = 4
        const val ACTION_STOP_EXECUTION = "com.fffcccdfgh.androidclicker.PVZ_STOP_EXECUTION"
        const val ACTION_OPEN_EDITOR = "com.fffcccdfgh.androidclicker.PVZ_OPEN_EDITOR"
        const val PREFS_NAME = PvzScriptSessionController.PREFS_NAME
        const val KEY_PROGRAM_CODE = PvzScriptSessionController.KEY_PROGRAM_CODE
        const val KEY_SCRIPT_NAME = PvzScriptSessionController.KEY_SCRIPT_NAME
        var isRunning = false
            private set
    }
}
