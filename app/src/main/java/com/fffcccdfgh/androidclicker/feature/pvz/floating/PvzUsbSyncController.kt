package com.fffcccdfgh.androidclicker.feature.pvz.floating

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.fffcccdfgh.androidclicker.R
import com.fffcccdfgh.androidclicker.core.overlay.FloatingWindowLayoutPolicy
import com.fffcccdfgh.androidclicker.core.overlay.FloatingWindowSize
import com.fffcccdfgh.androidclicker.core.program.ProgramScreenSize
import com.fffcccdfgh.androidclicker.feature.pvz.PvzUsbSyncUpdate
import com.fffcccdfgh.androidclicker.feature.pvz.PvzUsbSyncWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PvzUsbSyncController(
    private val service: Service,
    private val windowManagerProvider: () -> WindowManager?,
    private val saveProgram: (String, String) -> Unit,
    private val updateTitle: () -> Unit,
    private val currentScreenSize: () -> ProgramScreenSize,
    private val overlayType: () -> Int,
    private val dp: (Float) -> Int,
    private val bindPanelDrag: (View, View, WindowManager.LayoutParams) -> Unit,
    private val showPanel: (
        View,
        WindowManager.LayoutParams,
        String,
        () -> View?,
        () -> WindowManager.LayoutParams?
    ) -> Unit,
    private val removePanel: (View?, String, () -> Unit) -> Unit,
    private val editorCodeInputProvider: () -> EditText?
) {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val usbSyncWatcher by lazy { PvzUsbSyncWatcher(service) }
    private var usbSyncJob: Job? = null
    private var pendingUsbSyncUpdate: PvzUsbSyncUpdate? = null
    private var lastSeenUsbSyncSignature: String? = null
    private var confirmPanelView: View? = null
    private var confirmPanelParams: WindowManager.LayoutParams? = null

    fun start() {
        if (usbSyncJob?.isActive == true) return
        lastSeenUsbSyncSignature = loadLastSeenUsbSyncSignature()
        usbSyncJob = serviceScope.launch {
            while (isActive) {
                val update = withContext(Dispatchers.IO) {
                    usbSyncWatcher.readLatestUpdate()
                }
                if (update != null && update.signature != lastSeenUsbSyncSignature) {
                    lastSeenUsbSyncSignature = update.signature
                    saveLastSeenUsbSyncSignature(update.signature)
                    showOrUpdateUsbSyncConfirmPanel(update)
                }
                delay(USB_SYNC_POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        usbSyncJob?.cancel()
        usbSyncJob = null
    }

    @SuppressLint("InflateParams")
    fun showOrUpdateUsbSyncConfirmPanel(update: PvzUsbSyncUpdate) {
        pendingUsbSyncUpdate = update
        val existing = confirmPanelView
        if (existing != null) {
            updateUsbSyncConfirmPanelMessage(existing, update)
            return
        }

        val panel = LayoutInflater.from(service).inflate(R.layout.pvz_usb_sync_confirm_panel, null)
        val params = createUsbSyncConfirmPanelParams()
        confirmPanelView = panel
        confirmPanelParams = params
        updateUsbSyncConfirmPanelMessage(panel, update)
        bindPanelDrag(panel.findViewById(R.id.pvzUsbSyncPanelHeader), panel, params)

        panel.findViewById<View>(R.id.pvzUsbSyncOverwriteButton).setOnClickListener {
            applyPendingUsbSyncUpdate()
        }
        panel.findViewById<View>(R.id.pvzUsbSyncCancelButton).setOnClickListener {
            pendingUsbSyncUpdate = null
            hideConfirmPanel()
        }

        showPanel(
            panel,
            params,
            USB_SYNC_CONFIRM_ZONE_KEY,
            { confirmPanelView },
            { confirmPanelParams }
        )
    }

    fun updateUsbSyncConfirmPanelMessage(panel: View, update: PvzUsbSyncUpdate) {
        panel.findViewById<TextView>(R.id.pvzUsbSyncPanelMessage).text =
            service.getString(R.string.pvz_usb_sync_message_named, update.scriptName)
    }

    fun createUsbSyncConfirmPanelParams(): WindowManager.LayoutParams {
        val screen = currentScreenSize()
        val panelSize = FloatingWindowSize(dp(300f), dp(136f))
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            FloatingWindowLayoutPolicy.applyCenteredPosition(this, screen.width, screen.height, panelSize)
        }
    }

    fun applyPendingUsbSyncUpdate() {
        val update = pendingUsbSyncUpdate ?: return
        saveProgram(update.scriptName, update.code)
        updateTitle()
        editorCodeInputProvider()?.let { input ->
            input.setText(update.code)
            input.setSelection(update.code.length.coerceIn(0, input.text?.length ?: 0))
        }
        pendingUsbSyncUpdate = null
        hideConfirmPanel()
        Toast.makeText(service, R.string.pvz_usb_sync_applied, Toast.LENGTH_SHORT).show()
    }

    fun hideConfirmPanel() {
        removePanel(confirmPanelView, USB_SYNC_CONFIRM_ZONE_KEY) {
            confirmPanelView = null
        }
        confirmPanelParams = null
    }

    fun loadLastSeenUsbSyncSignature(): String? {
        return service.getSharedPreferences(PvzScriptSessionController.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PvzScriptSessionController.KEY_USB_SYNC_SIGNATURE, null)
    }

    fun saveLastSeenUsbSyncSignature(signature: String) {
        service.getSharedPreferences(PvzScriptSessionController.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PvzScriptSessionController.KEY_USB_SYNC_SIGNATURE, signature)
            .apply()
    }

    private companion object {
        const val USB_SYNC_CONFIRM_ZONE_KEY = "pvz_usb_sync_confirm_panel"
        const val USB_SYNC_POLL_INTERVAL_MS = 1000L
    }
}
