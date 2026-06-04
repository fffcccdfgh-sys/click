package com.fffcccdfgh.androidclicker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan

class FloatingControlService : Service() {
    private val tag = "FloatingControlService"
    private val stopDebugTag = "ClickerStopDebug"

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var floatingParams: WindowManager.LayoutParams? = null
    private var addPanelView: View? = null
    private var addPanelParams: WindowManager.LayoutParams? = null
    private var actionListPanelView: View? = null
    private var actionListPanelParams: WindowManager.LayoutParams? = null
    private var loopPanelView: View? = null
    private var loopPanelParams: WindowManager.LayoutParams? = null
    private var programTemplatePanelView: View? = null
    private var programTemplatePanelParams: WindowManager.LayoutParams? = null
    private var floatingTouchThrough = false
    private var executionStopButton: ExecutionStopButtonOverlay? = null
    private var stopButtonPositioning = false
    private var pickerView: View? = null
    private var pickerMode: Int = PICKER_TAP_POINT
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var pendingStartX = 0
    private var pendingStartY = 0
    private var actionListVisible = false
    private val positionMarkerViews = mutableListOf<View>()
    private val markerBindings = mutableMapOf<View, MarkerBinding>()
    private var positionVisible = false
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartParamX = 0
    private var dragStartParamY = 0
    private var screenWidthPx = 0
    private var screenHeightPx = 0
    private var settingsActionIndex = -1
    private var pendingInsertIndex: Int? = null
    private var condEditType: String? = null
    private var condEditInvert: Boolean = false
    private var condEditText: String? = null
    private var condEditOcrFilter: String = OcrFilterMode.DEFAULT
    private var condEditUseArea: Boolean = false
    private var condEditLeft: Int? = null
    private var condEditTop: Int? = null
    private var condEditRight: Int? = null
    private var condEditBottom: Int? = null
    private var condEditColorHex: String? = null
    private var condEditColorTolerance: Int? = null
    private var condEditColorX: Int? = null
    private var condEditColorY: Int? = null
    private var conditionOcrPrefillRunning: Boolean = false
    private var conditionOcrPrefillGeneration: Int = 0
    private var pendingProgramDraftCode: String? = null
    private var pendingProgramDraftCursor: Int = 0

    private enum class MarkerPointType { TAP, SWIPE_START, SWIPE_END, WAIT, PROGRAM }
    private data class MarkerBinding(val actionIndex: Int, val pointType: MarkerPointType)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_EXECUTION) {
            Log.d(stopDebugTag, "FloatingControlService notification stop clicked isRunning=${ActionSequenceExecutor.isRunning}")
            ActionSequenceExecutor.stop()
            return START_NOT_STICKY
        }

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView?.let { view ->
            try { windowManager?.removeView(view) } catch (_: Exception) {}
        }
        floatingView = null
        floatingParams = null
        hidePositionMarkers()
        hidePickerOverlay()
        showFloatingControl()
        isRunning = true
        broadcastRunningState()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(stopDebugTag, "FloatingControlService.onDestroy isRunning=${ActionSequenceExecutor.isRunning}")
        if (ActionSequenceExecutor.isRunning) {
            Log.d(stopDebugTag, "FloatingControlService.onDestroy stopping executor")
            ActionSequenceExecutor.stop()
        }
        hidePositionMarkers()
        hidePickerOverlay()
        hideExecutionStopButton()
        hideFloatingControl()
        isRunning = false
        broadcastRunningState()
    }

    private fun broadcastRunningState() {
        sendBroadcast(Intent(ACTION_STATE_CHANGED).apply {
            setPackage(packageName)
            putExtra(EXTRA_IS_RUNNING, isRunning)
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.floating_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, FloatingControlService::class.java).apply {
            action = ACTION_STOP_EXECUTION
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.floating_notification_title))
            .setContentText(getString(R.string.floating_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.notification_stop), pendingStop)
            .build()
    }

    @SuppressLint("InflateParams")
    private fun showFloatingControl() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.floating_control, null)
        floatingView = view

        val wm = windowManager ?: return
        val display = ScreenCaptureDisplayReader.current(this)
        screenWidthPx = display.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        screenHeightPx = display.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        floatingParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        view.findViewById<View>(R.id.dragHandle).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = floatingParams?.x ?: 0
                    initialY = floatingParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    floatingParams?.let { lp ->
                        lp.x = initialX + (event.rawX - initialTouchX).toInt()
                        lp.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(view, lp)
                        if (actionListVisible) updateActionListViewport(loadSequence().size)
                    }
                    true
                }
                else -> false
            }
        }

        bindStartStopTouch(view.findViewById(R.id.startButton)) {
            hideAllBeforeRun()
            executeSequence()
        }

        view.findViewById<View>(R.id.loopButton).setOnClickListener {
            toggleLoopSettingsPanel()
        }

        view.findViewById<View>(R.id.stopPositionButton).setOnClickListener {
            toggleStopButtonPositioning()
        }

        view.findViewById<View>(R.id.addButton).setOnClickListener {
            toggleAddMenu()
        }

        view.findViewById<View>(R.id.listButton).setOnClickListener {
            toggleActionList()
        }

        view.findViewById<View>(R.id.positionButton).setOnClickListener {
            togglePositionMarkers()
        }

        view.findViewById<View>(R.id.saveButton).setOnClickListener {
            val sequence = loadSequence()
            if (sequence.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_action_to_save), Toast.LENGTH_SHORT).show()
            } else {
                hideFloatingPanels()
                val confirmPanel = view.findViewById<View>(R.id.saveConfirmPanel)
                val nameInput = view.findViewById<EditText>(R.id.saveNameInput)
                val editingName = getEditingScriptName()
                nameInput.setText(editingName ?: ScriptStorage.nextAutoName(this))
                confirmPanel.visibility = View.VISIBLE
                enableOverlayFocus()
            }
        }

        view.findViewById<View>(R.id.saveConfirmYes).setOnClickListener {
            val nameInput = view.findViewById<EditText>(R.id.saveNameInput)
            val name = nameInput.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, getString(R.string.script_name_empty), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val editingName = getEditingScriptName()
            if (name != editingName && ScriptStorage.getScript(this, name) != null) {
                Toast.makeText(this, getString(R.string.script_name_exists), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val sequence = loadSequence()
            ScriptStorage.saveNamedScript(
                this,
                name,
                sequence,
                getLoopCount(this),
                getLoopGapMs(this)
            )
            setEditingScriptName(name)
            val msg = when {
                editingName != null && name == editingName -> getString(R.string.script_updated)
                editingName != null -> getString(R.string.script_saved_as)
                else -> getString(R.string.script_saved)
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            sendBroadcast(Intent(ScriptStorage.ACTION_SCRIPTS_CHANGED))
            view.findViewById<View>(R.id.saveConfirmPanel).visibility = View.GONE
            disableOverlayFocus()
        }

        view.findViewById<View>(R.id.saveConfirmNo).setOnClickListener {
            view.findViewById<View>(R.id.saveConfirmPanel).visibility = View.GONE
            disableOverlayFocus()
        }

        view.findViewById<View>(R.id.closeButton).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (ActionSequenceExecutor.isRunning) {
                        ActionSequenceExecutor.stop()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    stopSelf()
                    Toast.makeText(this, getString(R.string.floating_stopped), Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }

        view.findViewById<View>(R.id.foldButton).setOnClickListener {
            collapseFloatingControl()
        }

        // Collapsed mode controls
        bindStartStopTouch(view.findViewById(R.id.collapsedStartButton)) {
            hideAllBeforeRun()
            executeSequence()
        }

        view.findViewById<View>(R.id.collapsedCloseButton).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (ActionSequenceExecutor.isRunning) {
                        ActionSequenceExecutor.stop()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    stopSelf()
                    Toast.makeText(this, getString(R.string.floating_stopped), Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }

        view.findViewById<View>(R.id.collapsedExpandButton).setOnClickListener {
            expandFloatingControl()
        }

        val collapsedTitle = view.findViewById<TextView>(R.id.collapsedTitle)
        collapsedTitle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = floatingParams?.x ?: 0
                    initialY = floatingParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    floatingParams?.let { lp ->
                        lp.x = initialX + (event.rawX - initialTouchX).toInt()
                        lp.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(view, lp)
                    }
                    true
                }
                else -> false
            }
        }

        loadLoopSettings()
        updateLoopUI()
        updateStopPositionButton()

        updateFloatingTitle()

        wm.addView(view, floatingParams)
        ControlZoneChecker.register("floating_control") { getControlZoneRect() }

        ScreenshotHider.register("floating_control",
            hide = {
                floatingView?.visibility = View.INVISIBLE
                hidePositionMarkersForScreenshot()
            },
            reveal = {
                floatingView?.visibility = View.VISIBLE
                if (positionVisible) {
                    showPositionMarkers()
                }
            }
        )
    }

    private fun bindStartStopTouch(button: View, onStart: () -> Unit) {
        var stopTriggered = false
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (ActionSequenceExecutor.isRunning) {
                        stopTriggered = true
                        ActionSequenceExecutor.stop()
                    } else {
                        stopTriggered = false
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (stopTriggered) {
                        stopTriggered = false
                    } else if (!ActionSequenceExecutor.isRunning) {
                        onStart()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    stopTriggered = false
                    true
                }
                else -> false
            }
        }
    }

    private fun getControlZoneRect(): Rect? {
        if (floatingTouchThrough) return null
        val view = floatingView ?: return null
        val params = floatingParams ?: return null
        if (view.width <= 0 || view.height <= 0) return null
        return Rect(params.x, params.y, params.x + view.width, params.y + view.height)
    }

    private fun showExecutionStopButton() {
        val wm = windowManager ?: return
        if (executionStopButton == null) {
            executionStopButton = ExecutionStopButtonOverlay(
                context = this,
                windowManager = wm,
                zoneKey = "floating_execution_stop_button",
                onStop = { ActionSequenceExecutor.stop() }
            )
        }
        executionStopButton?.show()
    }

    private fun hideExecutionStopButton() {
        executionStopButton?.hide()
    }

    private fun toggleStopButtonPositioning() {
        if (ActionSequenceExecutor.isRunning) return
        if (stopButtonPositioning) {
            hideStopButtonPositionEditor()
        } else {
            showStopButtonPositionEditor()
        }
    }

    private fun showStopButtonPositionEditor() {
        val wm = windowManager ?: return
        hideExpandedPanels()
        if (executionStopButton == null) {
            executionStopButton = ExecutionStopButtonOverlay(
                context = this,
                windowManager = wm,
                zoneKey = "floating_execution_stop_button",
                onStop = { ActionSequenceExecutor.stop() }
            )
        }
        executionStopButton?.showForPositioning()
        stopButtonPositioning = true
        updateStopPositionButton()
    }

    private fun hideStopButtonPositionEditor() {
        if (!stopButtonPositioning) return
        if (!ActionSequenceExecutor.isRunning) {
            hideExecutionStopButton()
        }
        stopButtonPositioning = false
        updateStopPositionButton()
    }

    private fun setFloatingTouchThrough(enabled: Boolean) {
        floatingTouchThrough = enabled
        val params = floatingParams ?: return
        params.flags = if (enabled) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        params.alpha = if (enabled) {
            ExecutionOverlayWindowPolicy.TOUCH_THROUGH_ALPHA
        } else {
            ExecutionOverlayWindowPolicy.NORMAL_ALPHA
        }
        val view = floatingView ?: return
        if (view.isAttachedToWindow) {
            windowManager?.updateViewLayout(view, params)
        }
    }

    private fun enableOverlayFocus() {
        updateFloatingFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
    }

    private fun disableOverlayFocus() {
        updateFloatingFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    private fun updateFloatingFlags(flags: Int) {
        val params = floatingParams ?: return
        params.flags = flags
        if (!floatingTouchThrough) {
            params.alpha = ExecutionOverlayWindowPolicy.NORMAL_ALPHA
        }
        val view = floatingView ?: return
        if (view.isAttachedToWindow) {
            windowManager?.updateViewLayout(view, params)
        }
    }

    private fun collapseFloatingControl() {
        val view = floatingView ?: return
        view.findViewById<View>(R.id.saveConfirmPanel).visibility = View.GONE
        disableOverlayFocus()
        view.findViewById<View>(R.id.expandedControls).visibility = View.GONE
        view.findViewById<View>(R.id.collapsedControls).visibility = View.VISIBLE
        updateFloatingTitle()
    }

    private fun expandFloatingControl() {
        val view = floatingView ?: return
        view.findViewById<View>(R.id.collapsedControls).visibility = View.GONE
        view.findViewById<View>(R.id.expandedControls).visibility = View.VISIBLE
    }

    private fun hideAllPanels() {
        hideExpandedPanels()
        hidePositionMarkers()
        positionVisible = false
        updatePositionButton()
        pendingInsertIndex = null
    }

    private fun updateFloatingTitle() {
        val view = floatingView ?: return
        val name = getEditingScriptName()
        val fullTitle = if (name.isNullOrBlank()) getString(R.string.floating_control_label) else name
        val shortTitle = if (name.isNullOrBlank()) getString(R.string.floating_control_label) else name.take(3)
        view.findViewById<TextView>(R.id.dragHandle).text = fullTitle
        view.findViewById<TextView>(R.id.collapsedTitle).text = shortTitle
    }

    private fun hideFloatingControl() {
        hideExecutionStopButton()
        setFloatingTouchThrough(false)
        hideFloatingPanels()
        val view = floatingView
        val wm = windowManager
        if (view != null && wm != null) {
            try {
                wm.removeView(view)
            } catch (_: Exception) {
            }
        }
        ScreenshotHider.unregister("floating_control")
        floatingView = null
        floatingParams = null
        ControlZoneChecker.unregister("floating_control")
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun createFullScreenPickerParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun createPanelParams(offsetX: Int, offsetY: Int): WindowManager.LayoutParams {
        val baseX = floatingParams?.x ?: 100
        val baseY = floatingParams?.y ?: 300
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (baseX + dp(offsetX)).coerceAtLeast(0)
            y = (baseY + dp(offsetY)).coerceAtLeast(0)
        }
    }

    private fun dp(value: Int): Int = dp(value.toFloat())

    private fun dp(value: Float): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun roundedRect(fillColor: Int, strokeColor: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            setStroke(dp(1f), strokeColor)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun bindPanelDrag(handle: View, panel: View, params: WindowManager.LayoutParams) {
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(panel, params)
                    if (panel === actionListPanelView) updateActionListViewport(loadSequence().size)
                    true
                }
                else -> false
            }
        }
    }

    private fun removePanel(
        view: View?,
        zoneKey: String,
        onRemoved: () -> Unit
    ) {
        val wm = windowManager
        if (view != null && wm != null) {
            try {
                wm.removeView(view)
            } catch (_: Exception) {
            }
        }
        ScreenshotHider.unregister(zoneKey)
        ControlZoneChecker.unregister(zoneKey)
        onRemoved()
    }

    private fun panelZoneRect(view: View?, params: WindowManager.LayoutParams?): Rect? {
        if (floatingTouchThrough) return null
        if (view == null || params == null) return null
        if (view.width <= 0 || view.height <= 0) return null
        return Rect(params.x, params.y, params.x + view.width, params.y + view.height)
    }

    private fun hideFloatingPanels() {
        hideAddPanel()
        hideActionList()
        hideLoopSettingsPanel()
        hideProgramTemplatePanel()
    }

    private fun toggleAddMenu() {
        if (addPanelView != null) {
            hideAddPanel()
        } else {
            showAddPanel()
        }
    }

    @SuppressLint("InflateParams")
    private fun showAddPanel() {
        val wm = windowManager ?: return
        if (addPanelView != null) return
        floatingView?.findViewById<View>(R.id.saveConfirmPanel)?.visibility = View.GONE
        hideLoopSettingsPanel()
        disableOverlayFocus()

        val panel = LayoutInflater.from(this).inflate(R.layout.floating_add_panel, null)
        val params = addPanelParams ?: createPanelParams(0, 88)
        addPanelView = panel
        addPanelParams = params

        bindPanelDrag(panel.findViewById(R.id.addPanelDragHandle), panel, params)
        panel.findViewById<View>(R.id.tapOption).setOnClickListener {
            hideAddPanel()
            showPickerOverlay(PICKER_TAP_POINT)
        }
        panel.findViewById<View>(R.id.swipeOption).setOnClickListener {
            hideAddPanel()
            showPickerOverlay(PICKER_SWIPE_START)
        }
        panel.findViewById<View>(R.id.waitOption).setOnClickListener {
            hideAddPanel()
            showWaitDurationPicker()
        }
        panel.findViewById<View>(R.id.programOption).setOnClickListener {
            hideAddPanel()
            showProgramEditor()
        }

        wm.addView(panel, params)
        ControlZoneChecker.register("floating_add_panel") { panelZoneRect(addPanelView, addPanelParams) }
        ScreenshotHider.register("floating_add_panel",
            hide = { addPanelView?.visibility = View.INVISIBLE },
            reveal = { addPanelView?.visibility = View.VISIBLE }
        )
    }

    private fun hideAddPanel() {
        removePanel(addPanelView, "floating_add_panel") {
            addPanelView = null
        }
    }

    private fun toggleActionList() {
        if (actionListVisible) {
            hideActionList()
        } else {
            showActionList()
        }
    }

    @SuppressLint("InflateParams")
    private fun showActionList() {
        val wm = windowManager ?: return
        if (actionListPanelView == null) {
            val panel = LayoutInflater.from(this).inflate(R.layout.floating_action_list_panel, null)
            val params = actionListPanelParams ?: createPanelParams(0, 88)
            actionListPanelView = panel
            actionListPanelParams = params
            bindPanelDrag(panel.findViewById(R.id.actionListPanelHeader), panel, params)
            panel.findViewById<View>(R.id.actionListCloseButton).setOnClickListener {
                hideActionList()
            }
            bindActionListScrollBar(panel)
            wm.addView(panel, params)
            ControlZoneChecker.register("floating_action_list_panel") {
                panelZoneRect(actionListPanelView, actionListPanelParams)
            }
            ScreenshotHider.register("floating_action_list_panel",
                hide = { actionListPanelView?.visibility = View.INVISIBLE },
                reveal = { actionListPanelView?.visibility = View.VISIBLE }
            )
        }
        floatingView?.findViewById<View>(R.id.saveConfirmPanel)?.visibility = View.GONE
        hideLoopSettingsPanel()
        disableOverlayFocus()
        actionListVisible = true
        renderActionList()
    }

    private fun hideActionList() {
        actionListVisible = false
        removePanel(actionListPanelView, "floating_action_list_panel") {
            actionListPanelView = null
        }
    }

    private fun renderActionList() {
        val view = actionListPanelView ?: return
        val sequence = loadSequence()
        val container = view.findViewById<LinearLayout>(R.id.actionListContainer)
        container.removeAllViews()

        val header = view.findViewById<TextView>(R.id.actionListHeader)
        val rowWidth = dp(ACTION_LIST_PANEL_WIDTH_DP)

        if (sequence.isEmpty()) {
            header.text = getString(R.string.action_list_empty)
            container.addView(createActionListEmptyRow(rowWidth))
            updateActionListViewport(0)
            return
        }

        header.text = getString(R.string.action_list_title, sequence.size)

        for ((i, action) in sequence.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    rowWidth,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                background = roundedRect(0xFF1E293B.toInt(), 0xFF334155.toInt(), 12f)
                setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
                if (i > 0) {
                    (layoutParams as LinearLayout.LayoutParams).topMargin = dp(8f)
                }
            }

            val baseText = when (action.type) {
                ActionStep.TYPE_TAP -> getString(
                    R.string.action_list_tap_short,
                    i + 1,
                    formatStoredPercent(action.x!!),
                    formatStoredPercent(action.y!!)
                )
                ActionStep.TYPE_SWIPE -> getString(
                    R.string.action_list_swipe_short,
                    i + 1,
                    formatStoredPercent(action.startX!!),
                    formatStoredPercent(action.startY!!),
                    formatStoredPercent(action.endX!!),
                    formatStoredPercent(action.endY!!)
                )
                ActionStep.TYPE_WAIT -> getString(
                    R.string.action_list_wait_short,
                    i + 1, (action.durationMs ?: 0L) / 1000.0
                )
                ActionStep.TYPE_PROGRAM -> getString(
                    R.string.action_list_program_short,
                    i + 1
                )
                else -> ""
            }
            val descText = if (action.conditionType != null) {
                val condSuffix = if (action.conditionUseArea == true) {
                    getString(R.string.action_list_has_condition_area)
                } else {
                    getString(R.string.action_list_has_condition)
                }
                "$baseText  $condSuffix"
            } else {
                baseText
            }
            val desc = TextView(this).apply {
                text = descText
                setTextColor(0xFFF8FAFC.toInt())
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            val stepIndex = i
            val buttonRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START
                setPadding(0, dp(8f), 0, 0)
            }

            val settingsBtn = createActionListButton(
                getString(R.string.settings_action_button),
                0xFF22C55E.toInt(),
                0x3322C55E
            ) {
                showActionSettings(stepIndex)
            }
            val deleteBtn = createActionListButton(
                getString(R.string.delete_action),
                0xFFFB7185.toInt(),
                0x33FB7185
            ) {
                deleteActionAt(stepIndex)
            }
            val insertBeforeBtn = createActionListButton(
                getString(R.string.insert_before),
                0xFF60A5FA.toInt(),
                0x3360A5FA
            ) {
                pendingInsertIndex = stepIndex
                showInsertTypeMenu()
            }
            val insertAfterBtn = createActionListButton(
                getString(R.string.insert_after),
                0xFF60A5FA.toInt(),
                0x3360A5FA
            ) {
                pendingInsertIndex = stepIndex + 1
                showInsertTypeMenu()
            }

            buttonRow.addView(settingsBtn)
            buttonRow.addView(deleteBtn)
            buttonRow.addView(insertBeforeBtn)
            buttonRow.addView(insertAfterBtn)
            row.addView(desc)
            row.addView(buttonRow)
            container.addView(row)
        }
        updateActionListViewport(sequence.size)
    }

    private fun createActionListEmptyRow(rowWidth: Int): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(rowWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
            background = roundedRect(0xFF1E293B.toInt(), 0xFF334155.toInt(), 12f)
            setPadding(dp(10f), dp(12f), dp(10f), dp(12f))
            text = getString(R.string.action_list_empty)
            setTextColor(0xFFCBD5E1.toInt())
            textSize = 13f
        }
    }

    private fun createActionListButton(
        label: String,
        textColor: Int,
        fillColor: Int,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(58f), dp(30f)).apply {
                marginEnd = dp(6f)
            }
            background = roundedRect(fillColor, textColor, 10f)
            gravity = Gravity.CENTER
            text = label
            setTextColor(textColor)
            textSize = 12f
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun bindActionListScrollBar(view: View) {
        val scroll = view.findViewById<ScrollView>(R.id.actionListScroll)
        val scrollBar = view.findViewById<ProgramTemplateMenuScrollBar>(R.id.actionListScrollBar)
        scrollBar.attachTo(scroll)
    }

    private fun updateActionListViewport(itemCount: Int) {
        val view = actionListPanelView ?: return
        val scroll = view.findViewById<ScrollView>(R.id.actionListScroll)
        val scrollBar = view.findViewById<View>(R.id.actionListScrollBar)

        if (itemCount <= 0) {
            scroll.layoutParams = scroll.layoutParams.apply {
                height = dp(ACTION_LIST_MIN_HEIGHT_DP)
            }
            scroll.scrollTo(0, 0)
            scrollBar.visibility = View.GONE
            return
        }

        val density = resources.displayMetrics.density
        val rowHeightPx = (ACTION_LIST_ROW_HEIGHT_DP * density + 0.5f).toInt()
        val maxRowsHeight = ProgramTemplateMenuLayout.popupHeight(
            itemCount = itemCount,
            itemHeightPx = rowHeightPx,
            verticalPaddingPx = 0,
            maxVisibleRows = ACTION_LIST_MAX_VISIBLE_ROWS
        )
        val screenHeight = (screenHeightPx.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels)
        val panelY = (actionListPanelParams?.y ?: floatingParams?.y ?: 0).coerceAtLeast(0)
        val reservedHeight = (
            ACTION_LIST_RESERVED_HEIGHT_DP * density +
                ACTION_LIST_BOTTOM_MARGIN_DP * density +
                0.5f
            ).toInt()
        val maxByScreen = (screenHeight - panelY - reservedHeight)
            .coerceAtLeast((ACTION_LIST_MIN_HEIGHT_DP * density + 0.5f).toInt())
        val minHeight = (ACTION_LIST_MIN_HEIGHT_DP * density + 0.5f).toInt()
        val viewportHeight = maxRowsHeight
            .coerceAtLeast(minHeight)
            .coerceAtMost(maxByScreen)

        scroll.layoutParams = scroll.layoutParams.apply {
            height = viewportHeight
        }

        val likelyNeedsScroll = itemCount > ACTION_LIST_MAX_VISIBLE_ROWS || maxRowsHeight > maxByScreen
        scrollBar.visibility = if (likelyNeedsScroll) View.VISIBLE else View.GONE
        scroll.post {
            val contentHeight = scroll.getChildAt(0)?.height ?: 0
            scrollBar.visibility = if (contentHeight > scroll.height) View.VISIBLE else View.GONE
            scrollBar.invalidate()
        }
    }

    private fun deleteActionAt(index: Int) {
        val sequence = loadSequence().toMutableList()
        if (index < 0 || index >= sequence.size) return
        sequence.removeAt(index)
        saveSequence(sequence)
        renderActionList()
        refreshMarkers()
    }

    private fun showPickerOverlay(mode: Int, onPointPicked: ((Int, Int) -> Unit)? = null) {
        val wm = windowManager ?: return
        if (pickerView != null) return

        pickerMode = mode

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.activity_position_picker, null)
        pickerView = view

        val params = createFullScreenPickerParams()

        val instructionRes = when (mode) {
            PICKER_TAP_POINT -> R.string.pick_position_instruction
            PICKER_SWIPE_START -> R.string.pick_swipe_start_instruction
            PICKER_SWIPE_END -> R.string.pick_swipe_end_instruction
            else -> R.string.pick_position_instruction
        }
        view.findViewById<TextView>(R.id.pickerInstruction).setText(instructionRes)

        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                val message: String

                if (onPointPicked != null) {
                    hidePickerOverlay()
                    onPointPicked(x, y)
                    return@setOnTouchListener true
                }

                when (pickerMode) {
                    PICKER_TAP_POINT -> {
                        val storedX = pixelXToStoredPercent(x)
                        val storedY = pixelYToStoredPercent(y)
                        val action = ActionStep(type = ActionStep.TYPE_TAP, x = storedX, y = storedY)
                        appendToSequence(action)
                        message = getString(
                            R.string.action_added_tap,
                            formatStoredPercent(storedX),
                            formatStoredPercent(storedY)
                        )
                    }
                    PICKER_SWIPE_START -> {
                        pendingStartX = pixelXToStoredPercent(x)
                        pendingStartY = pixelYToStoredPercent(y)
                        hidePickerOverlay()
                        Toast.makeText(
                            this,
                            getString(
                                R.string.swipe_start_set,
                                formatStoredPercent(pendingStartX),
                                formatStoredPercent(pendingStartY)
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                        showPickerOverlay(PICKER_SWIPE_END)
                        return@setOnTouchListener true
                    }
                    PICKER_SWIPE_END -> {
                        val action = ActionStep(
                            type = ActionStep.TYPE_SWIPE,
                            startX = pendingStartX,
                            startY = pendingStartY,
                            endX = pixelXToStoredPercent(x),
                            endY = pixelYToStoredPercent(y)
                        )
                        appendToSequence(action)
                        message = getString(R.string.action_added_swipe)
                    }
                    else -> {
                        hidePickerOverlay()
                        return@setOnTouchListener true
                    }
                }
                hidePickerOverlay()
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                true
            } else {
                false
            }
        }

        wm.addView(view, params)
    }

    private fun hidePickerOverlay() {
        hideProgramTemplatePanel()
        val wm = windowManager
        val view = pickerView ?: return
        try {
            wm?.removeView(view)
        } catch (_: Exception) {
        }
        ScreenshotHider.unregister("condition_picker")
        pickerView = null
    }

    private fun togglePositionMarkers() {
        positionVisible = !positionVisible
        if (positionVisible) {
            val view = floatingView
            if (view != null) {
                view.findViewById<View>(R.id.saveConfirmPanel).visibility = View.GONE
                hideFloatingPanels()
                disableOverlayFocus()
            }
            showPositionMarkers()
        } else {
            hidePositionMarkers()
        }
        updatePositionButton()
    }

    private fun showPositionMarkers() {
        val wm = windowManager ?: return
        if (positionMarkerViews.isNotEmpty()) return

        val sequence = loadSequence()
        val density = resources.displayMetrics.density
        val markerSizePx = (48 * density).toInt()
        val labelHeightPx = (18 * density).toInt()
        val strokePx = (2.5f * density).toInt()
        val screen = programScreenSize()
        screenWidthPx = screen.width
        screenHeightPx = screen.height

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val winHeight = markerSizePx + labelHeightPx
        var waitCount = 0

        for ((i, action) in sequence.withIndex()) {
            val stepNum = i + 1
            when (action.type) {
                ActionStep.TYPE_TAP -> {
                    val x = storedPercentXToPointPx(action.x ?: continue)
                    val y = storedPercentYToPointPx(action.y ?: continue)
                    val circle = createCircleContent(markerSizePx, strokePx,
                        0x664CAF50.toInt(), 0xFF4CAF50.toInt(),
                        getString(R.string.tap_action), null)
                    val marker = createMarkerView(circle, stepNum, labelHeightPx)
                    val params = createMarkerParams(type, markerSizePx, winHeight, x, y,
                        screenWidthPx, screenHeightPx)
                    markerBindings[marker] = MarkerBinding(i, MarkerPointType.TAP)
                    marker.setOnTouchListener { v, event -> handleMarkerDrag(v, event, markerSizePx) }
                    wm.addView(marker, params)
                    positionMarkerViews.add(marker)
                }
                ActionStep.TYPE_SWIPE -> {
                    val sx = storedPercentXToPointPx(action.startX ?: continue)
                    val sy = storedPercentYToPointPx(action.startY ?: continue)
                    val ex = storedPercentXToPointPx(action.endX ?: continue)
                    val ey = storedPercentYToPointPx(action.endY ?: continue)

                    // Start marker (blue, "滑动" + "始")
                    val startCircle = createCircleContent(markerSizePx, strokePx,
                        0x662196F3.toInt(), 0xFF2196F3.toInt(),
                        getString(R.string.swipe_action), "始")
                    val startMarker = createMarkerView(startCircle, stepNum, labelHeightPx)
                    val startParams = createMarkerParams(type, markerSizePx, winHeight, sx, sy,
                        screenWidthPx, screenHeightPx)
                    markerBindings[startMarker] = MarkerBinding(i, MarkerPointType.SWIPE_START)
                    startMarker.setOnTouchListener { v, event -> handleMarkerDrag(v, event, markerSizePx) }
                    wm.addView(startMarker, startParams)
                    positionMarkerViews.add(startMarker)

                    // End marker (orange, "滑动" + "末")
                    val endCircle = createCircleContent(markerSizePx, strokePx,
                        0x66FF9800.toInt(), 0xFFFF9800.toInt(),
                        getString(R.string.swipe_action), "末")
                    val endMarker = createMarkerView(endCircle, stepNum, labelHeightPx)
                    val endParams = createMarkerParams(type, markerSizePx, winHeight, ex, ey,
                        screenWidthPx, screenHeightPx)
                    markerBindings[endMarker] = MarkerBinding(i, MarkerPointType.SWIPE_END)
                    endMarker.setOnTouchListener { v, event -> handleMarkerDrag(v, event, markerSizePx) }
                    wm.addView(endMarker, endParams)
                    positionMarkerViews.add(endMarker)
                }
                ActionStep.TYPE_WAIT -> {
                    val waitX = action.markerX ?: ((16 * density).toInt())
                    val waitY = action.markerY
                        ?: ((80 * density).toInt() + waitCount * (56 * density).toInt())
                    val circle = createCircleContent(markerSizePx, strokePx,
                        0x669E9E9E.toInt(), 0xFF9E9E9E.toInt(),
                        getString(R.string.marker_blank), null)
                    val marker = createMarkerView(circle, stepNum, labelHeightPx)
                    val params = createMarkerParams(type, markerSizePx, winHeight, waitX, waitY,
                        screenWidthPx, screenHeightPx)
                    markerBindings[marker] = MarkerBinding(i, MarkerPointType.WAIT)
                    marker.setOnTouchListener { v, event -> handleMarkerDrag(v, event, markerSizePx) }
                    wm.addView(marker, params)
                    positionMarkerViews.add(marker)
                    waitCount++
                }
                ActionStep.TYPE_PROGRAM -> {
                    val progX = action.markerX ?: ((16 * density).toInt())
                    val progY = action.markerY
                        ?: ((80 * density).toInt() + waitCount * (56 * density).toInt())
                    val circle = createCircleContent(markerSizePx, strokePx,
                        0x669C27B0.toInt(), 0xFF9C27B0.toInt(),
                        "\u7F16\u7A0B", null)  // 编程
                    val marker = createMarkerView(circle, stepNum, labelHeightPx)
                    val params = createMarkerParams(type, markerSizePx, winHeight, progX, progY,
                        screenWidthPx, screenHeightPx)
                    markerBindings[marker] = MarkerBinding(i, MarkerPointType.PROGRAM)
                    marker.setOnTouchListener { v, event -> handleMarkerDrag(v, event, markerSizePx) }
                    wm.addView(marker, params)
                    positionMarkerViews.add(marker)
                    waitCount++
                }
            }
        }
    }

    private fun createCircleContent(
        circleSizePx: Int, strokePx: Int,
        fillColor: Int, strokeColor: Int,
        line1: String, line2: String?
    ): View {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
            setStroke(strokePx, strokeColor)
        }
        if (line2 == null) {
            return TextView(this).apply {
                text = line1
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = bg
                layoutParams = LinearLayout.LayoutParams(circleSizePx, circleSizePx)
            }
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = bg
            layoutParams = LinearLayout.LayoutParams(circleSizePx, circleSizePx)
        }
        container.addView(TextView(this).apply {
            text = line1
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
        container.addView(TextView(this).apply {
            text = line2
            textSize = 8f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#CCCCCC"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
        return container
    }

    private fun createMarkerView(
        circleView: View, stepNum: Int, labelHeightPx: Int
    ): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        container.addView(circleView)
        val label = TextView(this).apply {
            text = "第${stepNum}步"
            textSize = 9f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#CCCCCC"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                labelHeightPx
            )
        }
        container.addView(label)
        return container
    }

    private fun createMarkerParams(
        type: Int, widthPx: Int, heightPx: Int,
        coordX: Int, coordY: Int,
        screenW: Int, screenH: Int
    ): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            widthPx, heightPx, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            x = (coordX - widthPx / 2).coerceIn(0, screenW - widthPx)
            y = (coordY - widthPx / 2).coerceIn(0, screenH - heightPx)
        }
    }

    private fun hidePositionMarkers() {
        val wm = windowManager ?: return
        for (view in positionMarkerViews) {
            try {
                wm.removeView(view)
            } catch (_: Exception) {
            }
        }
        positionMarkerViews.clear()
        markerBindings.clear()
    }

    private fun refreshMarkers() {
        if (!positionVisible) return
        hidePositionMarkers()
        showPositionMarkers()
    }

    private fun handleMarkerDrag(view: View, event: MotionEvent, circleSizePx: Int): Boolean {
        val binding = markerBindings[view] ?: return false
        val wm = windowManager ?: return false
        val params = (view.layoutParams as? WindowManager.LayoutParams) ?: return false

        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                dragStartParamX = params.x
                dragStartParamY = params.y
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - dragStartRawX).toInt()
                val dy = (event.rawY - dragStartRawY).toInt()
                params.x = (dragStartParamX + dx).coerceIn(0, screenWidthPx - params.width)
                params.y = (dragStartParamY + dy).coerceIn(0, screenHeightPx - params.height)
                wm.updateViewLayout(view, params)
                true
            }
            MotionEvent.ACTION_UP -> {
                val dx = Math.abs(event.rawX - dragStartRawX)
                val dy = Math.abs(event.rawY - dragStartRawY)
                val dragThreshold = DRAG_THRESHOLD_DP * resources.displayMetrics.density
                if (dx < dragThreshold && dy < dragThreshold) {
                    showActionSettings(binding.actionIndex)
                } else {
                    val newCenterX = params.x + params.width / 2
                    val newCenterY = params.y + circleSizePx / 2
                    updateActionCoordinate(binding, newCenterX, newCenterY)
                }
                true
            }
            else -> false
        }
    }

    private fun updateActionCoordinate(binding: MarkerBinding, newX: Int, newY: Int) {
        val sequence = loadSequence().toMutableList()
        if (binding.actionIndex >= sequence.size) return

        val oldAction = sequence[binding.actionIndex]
        val storedX = pixelXToStoredPercent(newX)
        val storedY = pixelYToStoredPercent(newY)
        val updatedAction = when (binding.pointType) {
            MarkerPointType.TAP -> oldAction.copy(x = storedX, y = storedY)
            MarkerPointType.SWIPE_START -> oldAction.copy(startX = storedX, startY = storedY)
            MarkerPointType.SWIPE_END -> oldAction.copy(endX = storedX, endY = storedY)
            MarkerPointType.WAIT -> oldAction.copy(markerX = newX, markerY = newY)
            MarkerPointType.PROGRAM -> oldAction.copy(markerX = newX, markerY = newY)
        }
        sequence[binding.actionIndex] = updatedAction
        saveSequence(sequence)
        if (actionListVisible) renderActionList()
    }

    private fun updatePositionButton() {
        val view = floatingView ?: return
        val btn = view.findViewById<TextView>(R.id.positionButton)
        if (positionVisible) {
            btn.text = getString(R.string.position_hide)
            btn.setTextColor(0xFF4CAF50.toInt())
        } else {
            btn.text = getString(R.string.position_show)
            btn.setTextColor(Color.WHITE)
        }
    }

    private fun updateStopPositionButton() {
        val view = floatingView ?: return
        val btn = view.findViewById<TextView>(R.id.stopPositionButton)
        if (stopButtonPositioning) {
            btn.text = getString(R.string.stop_position_done)
            btn.setTextColor(0xFF4CAF50.toInt())
        } else {
            btn.text = getString(R.string.stop_position_action)
            btn.setTextColor(Color.WHITE)
        }
    }

    private fun hideExpandedPanels() {
        hideStopButtonPositionEditor()
        val view = floatingView ?: return
        hideFloatingPanels()
        view.findViewById<View>(R.id.saveConfirmPanel).visibility = View.GONE
        disableOverlayFocus()
        hidePickerOverlay()
    }

    private fun hideAllBeforeRun() {
        pendingInsertIndex = null
        hideExpandedPanels()
        hidePositionMarkersForExecution()
    }

    private fun hidePositionMarkersForExecution() {
        if (!positionVisible) return
        hidePositionMarkers()
        positionVisible = false
        updatePositionButton()
    }

    private fun showInsertTypeMenu() {
        val wm = windowManager ?: return
        if (pickerView != null) hidePickerOverlay()

        val inflater = LayoutInflater.from(this)
        val picker = inflater.inflate(R.layout.duration_picker, null)
        pickerView = picker

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        ((picker as? LinearLayout)?.getChildAt(0) as? TextView)?.text = getString(R.string.insert_action_type)
        val container = picker.findViewById<LinearLayout>(R.id.durationContainer)
        container.removeAllViews()

        val options = listOf(
            ActionStep.TYPE_TAP to getString(R.string.tap_action),
            ActionStep.TYPE_SWIPE to getString(R.string.swipe_action),
            ActionStep.TYPE_WAIT to getString(R.string.wait_action),
            ActionStep.TYPE_PROGRAM to getString(R.string.program_action)
        )

        for ((actionType, label) in options) {
            val option = TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(32, 16, 32, 16)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    hidePickerOverlay()
                    when (actionType) {
                        ActionStep.TYPE_TAP -> showPickerOverlay(PICKER_TAP_POINT)
                        ActionStep.TYPE_SWIPE -> showPickerOverlay(PICKER_SWIPE_START)
                        ActionStep.TYPE_WAIT -> showWaitDurationPicker()
                        ActionStep.TYPE_PROGRAM -> showProgramEditor()
                    }
                }
            }
            container.addView(option)
        }

        val cancel = TextView(this).apply {
            text = getString(R.string.cancel)
            setTextColor(0xFFFF8888.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(32, 16, 32, 16)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                hidePickerOverlay()
            }
        }
        container.addView(cancel)

        wm.addView(picker, params)
    }

    private fun showWaitDurationPicker() {
        val view = floatingView ?: return
        val wm = windowManager ?: return
        if (pickerView != null) return

        val inflater = LayoutInflater.from(this)
        val picker = inflater.inflate(R.layout.duration_picker, null)
        pickerView = picker

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val durations = listOf(500L to R.string.wait_duration_05s, 1000L to R.string.wait_duration_1s, 2000L to R.string.wait_duration_2s, 5000L to R.string.wait_duration_5s)
        val container = picker.findViewById<LinearLayout>(R.id.durationContainer)

        for ((durationMs, labelRes) in durations) {
            val option = TextView(this).apply {
                text = getString(labelRes)
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(32, 16, 32, 16)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val action = ActionStep(type = ActionStep.TYPE_WAIT, durationMs = durationMs)
                    appendToSequence(action)
                    Toast.makeText(
                        this@FloatingControlService,
                        getString(R.string.action_added_wait, durationMs / 1000.0),
                        Toast.LENGTH_SHORT
                    ).show()
                    hidePickerOverlay()
                }
            }
            container.addView(option)
        }

        wm.addView(picker, params)
    }

    private fun showProgramEditor() {
        val wm = windowManager ?: return
        if (pickerView != null) hidePickerOverlay()

        val inflater = LayoutInflater.from(this)
        val editor = inflater.inflate(R.layout.program_editor, null)
        pickerView = editor

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val currentDisplay = ScreenCaptureDisplayReader.current(this)
        val editorSize = ProgramEditorWindowPolicy.windowSizeForCurrentDisplay(
            displayWidthPx = currentDisplay.width,
            displayHeightPx = currentDisplay.height,
            resourceWidthPx = resources.displayMetrics.widthPixels,
            resourceHeightPx = resources.displayMetrics.heightPixels,
            density = resources.displayMetrics.density
        )

        val params = WindowManager.LayoutParams(
            editorSize.width,
            editorSize.height,
            type,
            ProgramEditorWindowPolicy.FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = ProgramEditorWindowPolicy.SOFT_INPUT_MODE
        }

        val codeInput = editor.findViewById<EditText>(R.id.programCodeInput)
        val codePanel = editor.findViewById<View>(R.id.programCodePanel)
        codePanel.layoutParams = codePanel.layoutParams.apply {
            height = ProgramEditorWindowPolicy.codePanelHeight(
                editorWidthPx = editorSize.width,
                editorHeightPx = editorSize.height,
                density = resources.displayMetrics.density
            )
        }
        configureProgramCodeInput(codeInput)
        bindProgramTextToolbar(editor, codeInput)
        val codeScrollBar = editor.findViewById<ProgramCodeScrollBar>(R.id.programCodeScrollBar)
        codeScrollBar.attachTo(codeInput)
        val lineNumbers = editor.findViewById<ProgramLineNumberView>(R.id.programLineNumbers)
        lineNumbers.attachTo(codeInput)
        codeInput.isVerticalScrollBarEnabled = false
        val restoredDraft = pendingProgramDraftCode
        if (restoredDraft != null) {
            codeInput.setText(restoredDraft)
            codeInput.setSelection(pendingProgramDraftCursor.coerceIn(0, restoredDraft.length))
            pendingProgramDraftCode = null
            pendingProgramDraftCursor = 0
        }

        // Drag support
        editor.findViewById<View>(R.id.programEditorTitle).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    wm.updateViewLayout(editor, params)
                    true
                }
                else -> false
            }
        }

        if (restoredDraft == null && settingsActionIndex != -1) {
            val sequence = loadSequence()
            if (settingsActionIndex < sequence.size) {
                val action = sequence[settingsActionIndex]
                codeInput.setText(action.code ?: "")
            }
        }

        editor.findViewById<View>(R.id.templateButton).setOnClickListener {
            showProgramTemplatePanel(codeInput)
        }

        editor.findViewById<View>(R.id.programPickTapButton).setOnClickListener {
            startProgramPointAssist(codeInput) { x, y ->
                val screen = programScreenSize()
                ProgramLuaAssist.tapSnippet(x, y, screen.width, screen.height)
            }
        }

        editor.findViewById<View>(R.id.programPickCoordButton).setOnClickListener {
            startProgramPointAssist(codeInput) { x, y ->
                val screen = programScreenSize()
                ProgramLuaAssist.coordinateSnippet(x, y, screen.width, screen.height)
            }
        }

        editor.findViewById<View>(R.id.programPickSwipeButton).setOnClickListener {
            startProgramSwipeAssist(codeInput)
        }

        editor.findViewById<View>(R.id.programPickAreaButton).setOnClickListener {
            saveProgramDraftForAssist(codeInput)
            hidePickerOverlay()
            showAreaPicker(
                onAreaSelected = { area ->
                    val screen = programScreenSize()
                    restoreProgramEditorWithSnippet(
                        ProgramLuaAssist.textAreaSnippet(
                            area.left,
                            area.top,
                            area.right,
                            area.bottom,
                            screen.width,
                            screen.height
                        )
                    )
                },
                onCancelled = {
                    restoreProgramEditorWithSnippet(null)
                }
            )
        }

        editor.findViewById<View>(R.id.programPickColorButton).setOnClickListener {
            saveProgramDraftForAssist(codeInput)
            hidePickerOverlay()
            showColorPickerOverlay(
                onColorSelected = { x, y, hex ->
                    val screen = programScreenSize()
                    restoreProgramEditorWithSnippet(
                        ProgramLuaAssist.colorSnippet(hex, x, y, screen.width, screen.height)
                    )
                },
                onCancelled = {
                    restoreProgramEditorWithSnippet(null)
                }
            )
        }

        editor.findViewById<View>(R.id.testParseButton).setOnClickListener {
            val code = codeInput.text.toString()
            try {
                val globals = org.luaj.vm2.lib.jse.JsePlatform.standardGlobals()
                globals.load(code, "test")
                Toast.makeText(this, getString(R.string.program_parse_success), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, e.message ?: getString(R.string.program_parse_failed), Toast.LENGTH_SHORT).show()
            }
        }

        editor.findViewById<View>(R.id.programCloseButton).setOnClickListener {
            hidePickerOverlay()
            settingsActionIndex = -1
        }

        editor.findViewById<View>(R.id.programSaveButton).setOnClickListener {
            val code = codeInput.text.toString()
            if (code.isBlank()) {
                Toast.makeText(this, getString(R.string.program_code_empty), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                val globals = org.luaj.vm2.lib.jse.JsePlatform.standardGlobals()
                globals.load(code, "test")
            } catch (e: Exception) {
                Toast.makeText(this, e.message ?: getString(R.string.program_parse_failed), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val delayBefore = 1L
            val repeatCount = 1

            val action = ActionStep(
                type = ActionStep.TYPE_PROGRAM,
                code = code,
                delayBeforeMs = delayBefore,
                repeatCount = repeatCount
            )

            if (settingsActionIndex != -1) {
                val sequence = loadSequence().toMutableList()
                if (settingsActionIndex in sequence.indices) {
                    val oldAction = sequence[settingsActionIndex]
                    sequence[settingsActionIndex] = oldAction.copy(
                        code = code,
                        delayBeforeMs = delayBefore,
                        repeatCount = repeatCount
                    )
                    saveSequence(sequence)
                    if (actionListVisible) renderActionList()
                    refreshMarkers()
                }
                settingsActionIndex = -1
            } else {
                appendToSequence(action)
            }

            hidePickerOverlay()
            Toast.makeText(this, getString(R.string.program_action_saved), Toast.LENGTH_SHORT).show()
        }

        wm.addView(editor, params)
        focusProgramCodeInput(codeInput)
    }

    private fun configureProgramCodeInput(codeInput: EditText) {
        codeInput.isFocusable = true
        codeInput.isFocusableInTouchMode = true
        codeInput.isLongClickable = true
        codeInput.setTextIsSelectable(true)
        codeInput.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        codeInput.setSelectAllOnFocus(false)
    }

    private fun focusProgramCodeInput(codeInput: EditText) {
        codeInput.post {
            codeInput.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(codeInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun bindProgramTextToolbar(editor: View, codeInput: EditText) {
        editor.findViewById<View>(R.id.programSelectAllButton).setOnClickListener {
            codeInput.requestFocus()
            codeInput.selectAll()
        }

        editor.findViewById<View>(R.id.programCopyButton).setOnClickListener {
            codeInput.requestFocus()
            val selectedText = ProgramEditorTextActions.selectedText(
                code = codeInput.text.toString(),
                selectionStart = codeInput.selectionStart,
                selectionEnd = codeInput.selectionEnd
            )
            if (selectedText == null) {
                Toast.makeText(this, R.string.program_edit_no_selection, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            writeProgramClipboard(selectedText)
            Toast.makeText(this, R.string.program_edit_copied, Toast.LENGTH_SHORT).show()
        }

        editor.findViewById<View>(R.id.programCutButton).setOnClickListener {
            codeInput.requestFocus()
            val code = codeInput.text.toString()
            val selectionStart = codeInput.selectionStart
            val selectionEnd = codeInput.selectionEnd
            val selectedText = ProgramEditorTextActions.selectedText(code, selectionStart, selectionEnd)
            val result = ProgramEditorTextActions.cutSelection(code, selectionStart, selectionEnd)
            if (selectedText == null || result == null) {
                Toast.makeText(this, R.string.program_edit_no_selection, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            writeProgramClipboard(selectedText)
            applyProgramTextResult(codeInput, result)
            Toast.makeText(this, R.string.program_edit_cut_done, Toast.LENGTH_SHORT).show()
        }

        editor.findViewById<View>(R.id.programPasteButton).setOnClickListener {
            codeInput.requestFocus()
            val clipboardText = readProgramClipboardText()
            if (clipboardText.isNullOrEmpty()) {
                Toast.makeText(this, R.string.program_edit_clipboard_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val result = ProgramEditorTextActions.pasteText(
                code = codeInput.text.toString(),
                selectionStart = codeInput.selectionStart,
                selectionEnd = codeInput.selectionEnd,
                pasteText = clipboardText
            )
            applyProgramTextResult(codeInput, result)
            Toast.makeText(this, R.string.program_edit_pasted, Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeProgramClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Lua code", text))
    }

    private fun readProgramClipboardText(): String? {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()
    }

    private fun applyProgramTextResult(codeInput: EditText, result: ProgramEditorTextResult) {
        codeInput.setText(result.code)
        codeInput.setSelection(result.cursor.coerceIn(0, result.code.length))
    }

    @SuppressLint("InflateParams")
    private fun showProgramTemplatePanel(codeInput: EditText) {
        val wm = windowManager ?: return
        if (programTemplatePanelView != null) {
            hideProgramTemplatePanel()
            return
        }

        val panel = LayoutInflater.from(this).inflate(R.layout.floating_program_template_panel, null)
        val params = programTemplatePanelParams ?: createProgramTemplatePanelParams()
        programTemplatePanelView = panel
        programTemplatePanelParams = params

        bindPanelDrag(panel.findViewById(R.id.programTemplateHeader), panel, params)
        panel.findViewById<View>(R.id.programTemplateCloseButton).setOnClickListener {
            hideProgramTemplatePanel()
        }

        val templates = ProgramLuaAssist.quickTemplates()
        val container = panel.findViewById<LinearLayout>(R.id.programTemplateContainer)
        container.removeAllViews()
        val rowWidth = dp(PROGRAM_TEMPLATE_PANEL_WIDTH_DP)
        for ((index, template) in templates.withIndex()) {
            val item = TextView(this).apply {
                text = template.title
                textSize = 14f
                setTextColor(0xFFF8FAFC.toInt())
                gravity = Gravity.CENTER_VERTICAL
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedRect(0xFF1E293B.toInt(), 0xFF334155.toInt(), 12f)
                setPadding(dp(14f), 0, dp(14f), 0)
                layoutParams = LinearLayout.LayoutParams(rowWidth, dp(PROGRAM_TEMPLATE_ROW_HEIGHT_DP)).apply {
                    if (index > 0) topMargin = dp(8f)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    insertProgramSnippet(codeInput, template.snippet)
                    hideProgramTemplatePanel()
                    codeInput.requestFocus()
                }
            }
            container.addView(item)
        }

        val scrollView = panel.findViewById<ScrollView>(R.id.programTemplateScroll)
        val scrollBar = panel.findViewById<ProgramTemplateMenuScrollBar>(R.id.programTemplateScrollBar)
        val menuHeight = ProgramTemplateMenuLayout.popupHeight(
            itemCount = templates.size,
            itemHeightPx = dp(PROGRAM_TEMPLATE_ROW_HEIGHT_DP + 8f),
            verticalPaddingPx = 0,
            maxVisibleRows = PROGRAM_TEMPLATE_MAX_VISIBLE_ROWS
        )
        scrollView.layoutParams = scrollView.layoutParams.apply {
            height = menuHeight
        }
        scrollBar.attachTo(scrollView)

        wm.addView(panel, params)
        ControlZoneChecker.register("floating_program_template_panel") {
            panelZoneRect(programTemplatePanelView, programTemplatePanelParams)
        }
        ScreenshotHider.register("floating_program_template_panel",
            hide = { programTemplatePanelView?.visibility = View.INVISIBLE },
            reveal = { programTemplatePanelView?.visibility = View.VISIBLE }
        )
    }

    private fun createProgramTemplatePanelParams(): WindowManager.LayoutParams {
        val panelWidth = dp(PROGRAM_TEMPLATE_PANEL_WIDTH_DP + 42f)
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((resources.displayMetrics.widthPixels - panelWidth) / 2).coerceAtLeast(0)
            y = (resources.displayMetrics.heightPixels * 0.18f).toInt().coerceAtLeast(0)
        }
    }

    private fun hideProgramTemplatePanel() {
        removePanel(programTemplatePanelView, "floating_program_template_panel") {
            programTemplatePanelView = null
        }
    }

    private fun insertProgramSnippet(codeInput: EditText, snippet: String) {
        val result = ProgramLuaAssist.insertSnippet(
            code = codeInput.text.toString(),
            cursor = codeInput.selectionStart.coerceAtLeast(0),
            snippet = snippet
        )
        codeInput.setText(result.code)
        codeInput.setSelection(result.cursor.coerceIn(0, result.code.length))
    }

    private fun programScreenSize(): ProgramScreenSize {
        val display = ScreenCaptureDisplayReader.current(this)
        val width = display.width
            .takeIf { it > 0 }
            ?: screenWidthPx.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        val height = display.height
            .takeIf { it > 0 }
            ?: screenHeightPx.takeIf { it > 0 }
            ?: resources.displayMetrics.heightPixels
        return ProgramScreenSize(width, height)
    }

    private fun pixelXToStoredPercent(x: Int): Int {
        return ProgramCoordinateAdapter.pointToStoredPercent(x, programScreenSize().width)
    }

    private fun pixelYToStoredPercent(y: Int): Int {
        return ProgramCoordinateAdapter.pointToStoredPercent(y, programScreenSize().height)
    }

    private fun storedPercentXToPointPx(value: Int): Int {
        return ProgramCoordinateAdapter.storedPercentToPointPx(value, programScreenSize().width)
    }

    private fun storedPercentYToPointPx(value: Int): Int {
        return ProgramCoordinateAdapter.storedPercentToPointPx(value, programScreenSize().height)
    }

    private fun storedPercentXToEdgePx(value: Int): Int {
        return ProgramCoordinateAdapter.storedPercentToEdgePx(value, programScreenSize().width)
    }

    private fun storedPercentYToEdgePx(value: Int): Int {
        return ProgramCoordinateAdapter.storedPercentToEdgePx(value, programScreenSize().height)
    }

    private fun formatStoredPercent(value: Int): String {
        return ProgramCoordinateAdapter.formatStoredPercent(value)
    }

    private fun startProgramPointAssist(
        codeInput: EditText,
        snippetBuilder: (Int, Int) -> String
    ) {
        saveProgramDraftForAssist(codeInput)
        hidePickerOverlay()
        showPickerOverlay(PICKER_TAP_POINT) { x, y ->
            restoreProgramEditorWithSnippet(snippetBuilder(x, y))
        }
    }

    private fun startProgramSwipeAssist(codeInput: EditText) {
        saveProgramDraftForAssist(codeInput)
        hidePickerOverlay()
        showPickerOverlay(PICKER_SWIPE_START) { startX, startY ->
            Toast.makeText(this, getString(R.string.swipe_start_set, startX, startY), Toast.LENGTH_SHORT).show()
            showPickerOverlay(PICKER_SWIPE_END) { endX, endY ->
                val screen = programScreenSize()
                restoreProgramEditorWithSnippet(
                    ProgramLuaAssist.swipeSnippet(startX, startY, endX, endY, screen.width, screen.height)
                )
            }
        }
    }

    private fun saveProgramDraftForAssist(codeInput: EditText) {
        pendingProgramDraftCode = codeInput.text.toString()
        pendingProgramDraftCursor = codeInput.selectionStart.coerceAtLeast(0)
    }

    private fun restoreProgramEditorWithSnippet(snippet: String?) {
        if (snippet != null) {
            val result = ProgramLuaAssist.insertSnippet(
                code = pendingProgramDraftCode.orEmpty(),
                cursor = pendingProgramDraftCursor,
                snippet = snippet
            )
            pendingProgramDraftCode = result.code
            pendingProgramDraftCursor = result.cursor
        }
        showProgramEditor()
    }

    private fun loadSequence(): List<ActionStep> {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val json = prefs.getString(KEY_ACTION_SEQUENCE, null) ?: return emptyList()
        return try {
            ActionStep.listFromJson(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveSequence(sequence: List<ActionStep>) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTION_SEQUENCE, ActionStep.listToJson(sequence)).apply()
    }

    private fun clearSequence() {
        saveSequence(emptyList())
        if (actionListVisible) {
            renderActionList()
        }
        refreshMarkers()
    }

    private fun appendToSequence(action: ActionStep) {
        val sequence = loadSequence().toMutableList()
        val insertAt = pendingInsertIndex
        if (insertAt != null && insertAt >= 0 && insertAt <= sequence.size) {
            sequence.add(insertAt, action)
            pendingInsertIndex = null
        } else {
            sequence.add(action)
        }
        saveSequence(sequence)
        if (actionListVisible) {
            renderActionList()
        }
        refreshMarkers()
    }

    private fun executeSequence() {
        val count = getLoopCount(this)
        ActionSequenceExecutor.loopCount = count
        ActionSequenceExecutor.loopEnabled = count != 1
        ActionSequenceExecutor.loopGapMs = getLoopGapMs(this)
        Log.d(
            stopDebugTag,
            "FloatingControlService.executeSequence loopCount=$count loopEnabled=${ActionSequenceExecutor.loopEnabled} loopGapMs=${ActionSequenceExecutor.loopGapMs}"
        )

        ActionSequenceExecutor.onStarted = {
            Log.d(stopDebugTag, "FloatingControlService.onStarted fired")
            setFloatingTouchThrough(true)
            showExecutionStopButton()
            updateStartStopButtons(true)
        }
        ActionSequenceExecutor.onFinished = {
            Log.d(stopDebugTag, "FloatingControlService.onFinished fired")
            hideExecutionStopButton()
            setFloatingTouchThrough(false)
            updateStartStopButtons(false)
        }
        ActionSequenceExecutor.onStopped = {
            Log.d(stopDebugTag, "FloatingControlService.onStopped fired")
            hideExecutionStopButton()
            setFloatingTouchThrough(false)
            updateStartStopButtons(false)
        }

        val sequence = loadSequence()
        Log.d(stopDebugTag, "FloatingControlService.executeSequence sequenceSize=${sequence.size}")

        val density = resources.displayMetrics.density
        val paddingPx = ControlZoneChecker.dpToPx(density)

        ActionSequenceExecutor.execute(
            this,
            sequence,
            canDispatchAction = { action ->
                !ControlZoneChecker.isActionInAnyZone(action, paddingPx)
            },
            onBlocked = {
                Toast.makeText(this, R.string.action_overlaps_control_stopped, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showActionSettings(actionIndex: Int) {
        val wm = windowManager ?: return
        if (pickerView != null) hidePickerOverlay()

        val sequence = loadSequence()
        if (actionIndex < 0 || actionIndex >= sequence.size) return
        val action = sequence[actionIndex]
        settingsActionIndex = actionIndex

        if (action.type == ActionStep.TYPE_PROGRAM) {
            showProgramEditor()
            return
        }

        hideLoopSettingsPanel()

        val inflater = LayoutInflater.from(this)
        val picker = inflater.inflate(R.layout.timing_picker, null)
        pickerView = picker

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val settingsWindowWidth = dp(ACTION_SETTINGS_PANEL_WIDTH_DP)
            .coerceAtMost(resources.displayMetrics.widthPixels - dp(32f))

        val params = WindowManager.LayoutParams(
            settingsWindowWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        picker.findViewById<TextView>(R.id.settingsTitle).text = "\u8BBE\u7F6E"

        picker.findViewById<View>(R.id.settingsHeader).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    wm.updateViewLayout(picker, params)
                    true
                }
                else -> false
            }
        }

        picker.findViewById<View>(R.id.settingsCloseButton).setOnClickListener {
            hidePickerOverlay()
            settingsActionIndex = -1
        }

        val durationInput = picker.findViewById<EditText>(R.id.durationInput)
        val delayBeforeInput = picker.findViewById<EditText>(R.id.delayBeforeInput)
        val repeatCountInput = picker.findViewById<EditText>(R.id.repeatCountInput)

        durationInput.setText((action.durationMs ?: 1L).toString())
        delayBeforeInput.setText((action.delayBeforeMs ?: 1L).toString())
        repeatCountInput.setText((action.repeatCount ?: 1).toString())

        fun saveCurrentSettingsInputs(): Boolean {
            val durationMs = parseMsInput(durationInput.text.toString())
            val delayBeforeMs = parseMsInput(delayBeforeInput.text.toString())
            val repeatCount = parseRepeatCount(repeatCountInput.text.toString())

            if (repeatCount < 0) {
                Toast.makeText(this, getString(R.string.loop_count_negative), Toast.LENGTH_SHORT).show()
                return false
            }

            val updatedSequence = loadSequence().toMutableList()
            if (settingsActionIndex in updatedSequence.indices) {
                val oldAction = updatedSequence[settingsActionIndex]
                updatedSequence[settingsActionIndex] = oldAction.copy(
                    durationMs = durationMs,
                    delayBeforeMs = delayBeforeMs,
                    repeatCount = repeatCount
                )
                saveSequence(updatedSequence)
                if (actionListVisible) renderActionList()
                refreshMarkers()
            }
            return true
        }

        picker.findViewById<View>(R.id.settingsSaveButton).setOnClickListener {
            if (!saveCurrentSettingsInputs()) {
                return@setOnClickListener
            }
            hidePickerOverlay()
            settingsActionIndex = -1
        }

        picker.findViewById<View>(R.id.settingsConditionButton).setOnClickListener {
            if (!saveCurrentSettingsInputs()) {
                return@setOnClickListener
            }
            loadConditionEditState()
            hidePickerOverlay()
            showConditionPicker()
        }

        wm.addView(picker, params)
    }

    private fun loadConditionEditState() {
        val sequence = loadSequence()
        if (settingsActionIndex in sequence.indices) {
            val action = sequence[settingsActionIndex]
            when (action.conditionType) {
                ActionStep.CONDITION_TEXT_NOT_CONTAINS -> {
                    condEditType = ActionStep.CONDITION_TEXT_CONTAINS
                    condEditInvert = true
                }
                ActionStep.CONDITION_COLOR_NOT_MATCH -> {
                    condEditType = ActionStep.CONDITION_COLOR_MATCH
                    condEditInvert = true
                }
                else -> {
                    condEditType = action.conditionType
                    condEditInvert = false
                }
            }
            condEditText = action.conditionText
            condEditOcrFilter = OcrFilterMode.normalize(action.conditionOcrFilter)
            condEditUseArea = action.conditionUseArea == true
            condEditLeft = action.conditionLeft
            condEditTop = action.conditionTop
            condEditRight = action.conditionRight
            condEditBottom = action.conditionBottom
            condEditColorHex = action.conditionColorHex
            condEditColorTolerance = action.conditionColorTolerance
            condEditColorX = action.conditionColorX
            condEditColorY = action.conditionColorY
        }
    }

    private fun showConditionPicker() {
        val wm = windowManager ?: return
        if (pickerView != null) hidePickerOverlay()
        OcrHelper.warmUpAsync()

        val inflater = LayoutInflater.from(this)
        val picker = inflater.inflate(R.layout.condition_picker, null)
        pickerView = picker

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val conditionWindowWidth = (560 * resources.displayMetrics.density).toInt()
            .coerceAtMost(resources.displayMetrics.widthPixels - (32 * resources.displayMetrics.density).toInt())

        val params = WindowManager.LayoutParams(
            conditionWindowWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        picker.findViewById<View>(R.id.conditionHeader).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    wm.updateViewLayout(picker, params)
                    true
                }
                else -> false
            }
        }

        picker.findViewById<View>(R.id.conditionCloseButton).setOnClickListener {
            hidePickerOverlay()
            settingsActionIndex = -1
        }

        val textInput = picker.findViewById<EditText>(R.id.conditionTextInput)
        val textLabel = picker.findViewById<TextView>(R.id.conditionTextLabel)
        val condTypeDropdown = picker.findViewById<TextView>(R.id.condTypeDropdown)
        val invertBtn = picker.findViewById<TextView>(R.id.condInvertBtn)
        val colorPickBtn = picker.findViewById<TextView>(R.id.condColorPickBtn)
        val colorPosRow = picker.findViewById<View>(R.id.conditionColorPosRow)
        val colorPosText = picker.findViewById<TextView>(R.id.condColorPosText)
        val selectAreaBtn = picker.findViewById<TextView>(R.id.condSelectAreaBtn)
        val areaRangeText = picker.findViewById<TextView>(R.id.condAreaRangeText)
        val ocrFilterRow = picker.findViewById<View>(R.id.conditionOcrFilterRow)
        val ocrFilterDropdown = picker.findViewById<TextView>(R.id.condOcrFilterDropdown)
        val colorToleranceRow = picker.findViewById<View>(R.id.conditionColorToleranceRow)
        val colorToleranceInput = picker.findViewById<EditText>(R.id.conditionColorToleranceInput)

        fun condTypeToLabel(type: String?): String = when (type) {
            ActionStep.CONDITION_TEXT_CONTAINS -> getString(R.string.condition_type_text_contains)
            ActionStep.CONDITION_COLOR_MATCH -> getString(R.string.condition_type_color_match)
            else -> getString(R.string.condition_type_none)
        }

        fun ocrFilterToLabel(filter: String?): String = when (OcrFilterMode.normalize(filter)) {
            OcrFilterMode.GRAYSCALE -> getString(R.string.condition_ocr_filter_grayscale)
            OcrFilterMode.THRESHOLD -> getString(R.string.condition_ocr_filter_threshold)
            OcrFilterMode.THRESHOLD_INVERT -> getString(R.string.condition_ocr_filter_threshold_invert)
            else -> getString(R.string.condition_ocr_filter_original)
        }

        fun updateInvertUI() {
            val hasType = condEditType != null
            invertBtn.isEnabled = hasType
            if (condEditInvert) {
                invertBtn.setTextColor(0xFF4CAF50.toInt())
            } else {
                invertBtn.setTextColor(if (hasType) 0xFFCCCCCC.toInt() else 0xFF666666.toInt())
            }
        }

        fun updateColorPosText() {
            val x = condEditColorX
            val y = condEditColorY
            if (x != null && y != null) {
                colorPosText.text = getString(
                    R.string.condition_color_pos_text,
                    formatStoredPercent(x),
                    formatStoredPercent(y)
                )
                colorPosText.setTextColor(0xFF4CAF50.toInt())
            } else {
                colorPosText.text = getString(R.string.condition_color_pos_text_none)
                colorPosText.setTextColor(0xFFAAAAAA.toInt())
            }
        }

        fun buildColorContentLabel(): CharSequence {
            val hex = condEditColorHex
            return if (hex != null) {
                val color = try { Color.parseColor(hex) } catch (_: Exception) { null }
                val prefix = getString(R.string.condition_content_label)  // "条件内容："
                // Two spaces as placeholder for the color swatch
                val full = SpannableStringBuilder(prefix + "  " + hex)
                if (color != null) {
                    val swatch = GradientDrawable().apply {
                        setColor(color)
                        setStroke(1, Color.parseColor("#33000000"))
                        shape = GradientDrawable.RECTANGLE
                    }
                    val size = (12 * resources.displayMetrics.density).toInt()
                    swatch.setBounds(0, 0, size, size)
                    val span = ImageSpan(swatch, ImageSpan.ALIGN_BASELINE)
                    // Place the swatch over the two placeholder spaces
                    full.setSpan(
                        span,
                        prefix.length,
                        prefix.length + 2,
                        Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                    )
                }
                full
            } else {
                getString(R.string.condition_content_label)
            }
        }

        fun updateConditionFormUI() {
            val hasCondition = condEditType != null
            val isColor = condEditType == ActionStep.CONDITION_COLOR_MATCH
            val isText = hasCondition && !isColor
            // Label
            textLabel.visibility = if (hasCondition) View.VISIBLE else View.GONE
            if (isColor) {
                textLabel.text = buildColorContentLabel()
            } else {
                textLabel.text = getString(R.string.condition_text_label)
            }
            // Text-specific controls
            textInput.visibility = if (isText) View.VISIBLE else View.GONE
            if (isText) {
                textInput.hint = getString(
                    if (conditionOcrPrefillRunning) {
                        R.string.condition_ocr_prefill_running
                    } else {
                        R.string.condition_text_hint
                    }
                )
            }
            selectAreaBtn.visibility = if (isText) View.VISIBLE else View.GONE
            ocrFilterRow.visibility = if (isText) View.VISIBLE else View.GONE
            if (isText) {
                ocrFilterDropdown.text = ocrFilterToLabel(condEditOcrFilter)
            }
            // Area range display (text type only, after area is set)
            if (isText && condEditUseArea && condEditLeft != null && condEditRight != null
                && condEditTop != null && condEditBottom != null) {
                areaRangeText.visibility = View.VISIBLE
                areaRangeText.text = getString(
                    R.string.condition_area_range,
                    formatStoredPercent(condEditLeft!!),
                    formatStoredPercent(condEditTop!!),
                    formatStoredPercent(condEditRight!!),
                    formatStoredPercent(condEditBottom!!)
                )
            } else {
                areaRangeText.visibility = View.GONE
            }
            // Color-specific controls
            colorPickBtn.visibility = if (isColor) View.VISIBLE else View.GONE
            colorToleranceRow.visibility = if (isColor) View.VISIBLE else View.GONE
            colorPosRow.visibility = if (isColor) View.VISIBLE else View.GONE
            if (isColor) {
                updateColorPosText()
            }
            updateInvertUI()
        }

        fun updateCondTypeDropdown() {
            condTypeDropdown.text = condTypeToLabel(condEditType)
        }

        fun onCondTypeSelected(index: Int) {
            val newType = when (index) {
                1 -> ActionStep.CONDITION_TEXT_CONTAINS
                2 -> ActionStep.CONDITION_COLOR_MATCH
                else -> null
            }
            // Clear state belonging to the previous type when switching
            if (newType != condEditType) {
                when (newType) {
                    ActionStep.CONDITION_COLOR_MATCH -> {
                        // Switching to color: clear text-related state
                        condEditText = null
                        condEditOcrFilter = OcrFilterMode.DEFAULT
                        condEditUseArea = false
                        condEditLeft = null
                        condEditTop = null
                        condEditRight = null
                        condEditBottom = null
                    }
                    ActionStep.CONDITION_TEXT_CONTAINS -> {
                        // Switching to text: clear color-related state
                        condEditOcrFilter = OcrFilterMode.DEFAULT
                        condEditColorHex = null
                        condEditColorTolerance = null
                        condEditColorX = null
                        condEditColorY = null
                    }
                    null -> {
                        // Switching to unconditional: clear everything
                        condEditInvert = false
                        condEditText = null
                        condEditOcrFilter = OcrFilterMode.DEFAULT
                        condEditUseArea = false
                        condEditLeft = null
                        condEditTop = null
                        condEditRight = null
                        condEditBottom = null
                        condEditColorHex = null
                        condEditColorTolerance = null
                        condEditColorX = null
                        condEditColorY = null
                    }
                }
            }
            condEditType = newType
            // Sync text input to the new type
            textInput.setText(
                if (newType == ActionStep.CONDITION_TEXT_CONTAINS) (condEditText ?: "") else ""
            )
            updateCondTypeDropdown()
            updateConditionFormUI()
        }

        fun showCondTypePopup() {
            val popupContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFF3A3A3A.toInt())
                setPadding(2, 2, 2, 2)
            }

            val options = listOf(
                getString(R.string.condition_type_none),
                getString(R.string.condition_type_text_contains),
                getString(R.string.condition_type_color_match)
            )

            val popup = PopupWindow(
                popupContent,
                condTypeDropdown.width,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true
            )
            popup.setBackgroundDrawable(ColorDrawable(0xFF3A3A3A.toInt()))
            popup.elevation = 12f

            for ((index, option) in options.withIndex()) {
                val item = TextView(this).apply {
                    text = option
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(14, 12, 14, 12)
                    setBackgroundColor(Color.TRANSPARENT)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        onCondTypeSelected(index)
                        popup.dismiss()
                    }
                }
                popupContent.addView(item)

                if (index < options.size - 1) {
                    val divider = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1)
                        setBackgroundColor(0x33FFFFFF.toInt())
                    }
                    popupContent.addView(divider)
                }
            }

            popup.showAsDropDown(condTypeDropdown, 0, 4)
        }

        fun showOcrFilterPopup() {
            val popupContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFF3A3A3A.toInt())
                setPadding(2, 2, 2, 2)
            }

            val options = listOf(
                OcrFilterMode.ORIGINAL to getString(R.string.condition_ocr_filter_original),
                OcrFilterMode.GRAYSCALE to getString(R.string.condition_ocr_filter_grayscale),
                OcrFilterMode.THRESHOLD to getString(R.string.condition_ocr_filter_threshold),
                OcrFilterMode.THRESHOLD_INVERT to getString(R.string.condition_ocr_filter_threshold_invert)
            )

            val popup = PopupWindow(
                popupContent,
                ocrFilterDropdown.width,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true
            )
            popup.setBackgroundDrawable(ColorDrawable(0xFF3A3A3A.toInt()))
            popup.elevation = 12f

            for ((index, option) in options.withIndex()) {
                val item = TextView(this).apply {
                    text = option.second
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(14, 12, 14, 12)
                    setBackgroundColor(Color.TRANSPARENT)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        condEditOcrFilter = option.first
                        ocrFilterDropdown.text = option.second
                        popup.dismiss()
                    }
                }
                popupContent.addView(item)

                if (index < options.size - 1) {
                    val divider = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1)
                        setBackgroundColor(0x33FFFFFF.toInt())
                    }
                    popupContent.addView(divider)
                }
            }

            popup.showAsDropDown(ocrFilterDropdown, 0, 4)
        }

        textInput.setText(condEditText ?: "")
        colorToleranceInput.setText((condEditColorTolerance ?: 10).toString())
        updateCondTypeDropdown()
        updateConditionFormUI()

        condTypeDropdown.setOnClickListener {
            showCondTypePopup()
        }

        ocrFilterDropdown.setOnClickListener {
            showOcrFilterPopup()
        }

        invertBtn.setOnClickListener {
            condEditInvert = !condEditInvert
            updateInvertUI()
        }

        selectAreaBtn.setOnClickListener {
            hidePickerOverlay()
            showAreaPicker()
        }

        colorPickBtn.setOnClickListener {
            if (!ScreenCaptureManager.isReady) {
                Toast.makeText(this, getString(R.string.screen_capture_not_ready), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            wm.removeView(picker)
            ScreenshotHider.unregister("condition_picker")
            pickerView = null
            showColorPickerOverlay()
        }

        picker.findViewById<View>(R.id.condSaveBtn).setOnClickListener {
            val isColor = condEditType == ActionStep.CONDITION_COLOR_MATCH

            if (condEditType != null) {
                if (isColor) {
                    if (condEditColorHex.isNullOrEmpty()) {
                        Toast.makeText(this, getString(R.string.condition_color_empty), Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                } else {
                    val text = textInput.text.toString().trim()
                    if (text.isEmpty()) {
                        Toast.makeText(this, getString(R.string.condition_text_empty), Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    condEditText = text
                }
            }

            if (isColor) {
                val toleranceText = colorToleranceInput.text.toString().trim()
                val tolerance = toleranceText.toIntOrNull() ?: 10
                condEditColorTolerance = tolerance.coerceIn(0, 100)
                if (condEditColorX == null || condEditColorY == null) {
                    Toast.makeText(this, getString(R.string.condition_color_pos_required), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            if (condEditUseArea && condEditType != null) {
                val l = condEditLeft
                val t = condEditTop
                val r = condEditRight
                val b = condEditBottom
                if (l == null || t == null || r == null || b == null || r <= l || b <= t) {
                    Toast.makeText(this, getString(R.string.condition_area_invalid), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            val savedType = when {
                condEditType == null -> null
                condEditType == ActionStep.CONDITION_COLOR_MATCH -> {
                    if (condEditInvert) ActionStep.CONDITION_COLOR_NOT_MATCH else ActionStep.CONDITION_COLOR_MATCH
                }
                condEditInvert -> ActionStep.CONDITION_TEXT_NOT_CONTAINS
                else -> ActionStep.CONDITION_TEXT_CONTAINS
            }

            val isSavedColor = savedType == ActionStep.CONDITION_COLOR_MATCH || savedType == ActionStep.CONDITION_COLOR_NOT_MATCH

            val sequence = loadSequence().toMutableList()
            if (settingsActionIndex in sequence.indices) {
                val oldAction = sequence[settingsActionIndex]
                sequence[settingsActionIndex] = oldAction.copy(
                    conditionType = savedType,
                    conditionText = if (savedType != null && !isSavedColor) condEditText else null,
                    conditionOcrFilter = if (savedType != null && !isSavedColor) {
                        OcrFilterMode.normalize(condEditOcrFilter)
                    } else {
                        null
                    },
                    conditionUseArea = if (savedType != null) condEditUseArea else null,
                    conditionLeft = if (condEditUseArea && savedType != null) condEditLeft else null,
                    conditionTop = if (condEditUseArea && savedType != null) condEditTop else null,
                    conditionRight = if (condEditUseArea && savedType != null) condEditRight else null,
                    conditionBottom = if (condEditUseArea && savedType != null) condEditBottom else null,
                    conditionColorHex = if (isSavedColor) condEditColorHex else null,
                    conditionColorTolerance = if (isSavedColor) condEditColorTolerance else null,
                    conditionColorX = if (isSavedColor) condEditColorX else null,
                    conditionColorY = if (isSavedColor) condEditColorY else null
                )
                saveSequence(sequence)
                if (actionListVisible) renderActionList()
                refreshMarkers()
            }
            hidePickerOverlay()
            settingsActionIndex = -1
            Toast.makeText(this, getString(R.string.condition_saved), Toast.LENGTH_SHORT).show()
        }

        wm.addView(picker, params)
        ScreenshotHider.register(
            "condition_picker",
            hide = {
                if (pickerView === picker) picker.visibility = View.INVISIBLE
            },
            reveal = {
                if (pickerView === picker) picker.visibility = View.VISIBLE
            }
        )
    }

    private fun showAreaPicker(
        onAreaSelected: ((Rect) -> Unit)? = null,
        onCancelled: (() -> Unit)? = null
    ) {
        val wm = windowManager ?: return

        var overlaysHiddenForAreaPicker = true
        ScreenshotHider.hideAll()
        fun revealAreaPickerOverlays() {
            if (overlaysHiddenForAreaPicker) {
                overlaysHiddenForAreaPicker = false
                ScreenshotHider.revealAll()
            }
        }

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.area_picker, null)
        pickerView = view

        val params = createFullScreenPickerParams()

        val selectionView = view.findViewById<AreaSelectionView>(R.id.areaSelectionView)
        val buttonsRow = view.findViewById<View>(R.id.areaPickerButtons)
        val saveBtn = view.findViewById<TextView>(R.id.areaPickerSaveBtn)
        val cancelBtn = view.findViewById<TextView>(R.id.areaPickerCancelBtn)

        // Restore previous area only when editing a normal condition. Program
        // assist should start with a fresh selection.
        if (onAreaSelected == null) {
            selectionView.setInitialRect(
                condEditLeft?.let { storedPercentXToEdgePx(it) },
                condEditTop?.let { storedPercentYToEdgePx(it) },
                condEditRight?.let { storedPercentXToEdgePx(it) },
                condEditBottom?.let { storedPercentYToEdgePx(it) }
            )
        }

        selectionView.onInteractionStarted = {
            buttonsRow.visibility = View.GONE
        }

        selectionView.onInteractionFinished = {
            buttonsRow.visibility = View.VISIBLE
        }

        saveBtn.setOnClickListener {
            val rect = selectionView.getSelectionRect()
            var areaRect: Rect? = null
            var shouldPrefillText = false
            if (rect != null) {
                // Convert view-relative coordinates to screen coordinates,
                // matching getBoundsInScreen() used by the accessibility tree.
                val loc = IntArray(2)
                selectionView.getLocationOnScreen(loc)
                val screenLeft = rect.left + loc[0]
                val screenTop = rect.top + loc[1]
                val screenRight = rect.right + loc[0]
                val screenBottom = rect.bottom + loc[1]
                condEditLeft = pixelXToStoredPercent(screenLeft)
                condEditTop = pixelYToStoredPercent(screenTop)
                condEditRight = pixelXToStoredPercent(screenRight)
                condEditBottom = pixelYToStoredPercent(screenBottom)
                condEditUseArea = true
                areaRect = Rect(screenLeft, screenTop, screenRight, screenBottom)
                shouldPrefillText = condEditType == ActionStep.CONDITION_TEXT_CONTAINS
            }
            selectionView.cleanup()
            wm.removeView(view)
            pickerView = null

            if (onAreaSelected != null) {
                revealAreaPickerOverlays()
                if (areaRect != null) {
                    onAreaSelected(areaRect)
                } else {
                    onCancelled?.invoke()
                }
                return@setOnClickListener
            }

            if (shouldPrefillText && areaRect != null) {
                overlaysHiddenForAreaPicker = false
                prefillTextConditionFromArea(areaRect)
            } else {
                revealAreaPickerOverlays()
                showConditionPicker()
            }
        }

        cancelBtn.setOnClickListener {
            selectionView.cleanup()
            wm.removeView(view)
            pickerView = null
            revealAreaPickerOverlays()
            if (onCancelled != null) {
                onCancelled()
                return@setOnClickListener
            }
            showConditionPicker()
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            pickerView = null
            revealAreaPickerOverlays()
            Log.w(tag, "Failed to show area picker", e)
            if (onCancelled != null) {
                onCancelled()
            } else {
                showConditionPicker()
            }
        }
    }

    private fun prefillTextConditionFromArea(area: Rect) {
        if (!ScreenCaptureManager.isReady) {
            ScreenshotHider.revealAll()
            showConditionPicker()
            Toast.makeText(this, getString(R.string.screen_capture_not_ready), Toast.LENGTH_SHORT).show()
            return
        }

        conditionOcrPrefillGeneration++
        val generation = conditionOcrPrefillGeneration
        val filterMode = OcrFilterMode.normalize(condEditOcrFilter)
        conditionOcrPrefillRunning = true
        condEditText = null
        showConditionPicker()
        ScreenshotHider.hideAll()

        Thread {
            val handler = android.os.Handler(mainLooper)
            val startedAt = SystemClock.uptimeMillis()
            var afterHideDelay = startedAt
            var beforeCapture = startedAt
            var afterCapture = startedAt
            var ocrBitmap: Bitmap? = null
            val ocrText = try {
                Thread.sleep(150)
                afterHideDelay = SystemClock.uptimeMillis()
                ScreenCaptureManager.refreshDisplayMetrics(this)
                ocrBitmap = if (!ScreenCaptureManager.isReady) {
                    null
                } else {
                    beforeCapture = SystemClock.uptimeMillis()
                    OcrHelper.captureAreaBitmap(
                        area = area,
                        screenWidth = ScreenCaptureManager.getCaptureWidth(),
                        screenHeight = ScreenCaptureManager.getCaptureHeight(),
                        timeoutMs = OcrTimingPolicy.PREFILL_CAPTURE_TIMEOUT_MS
                    )
                }
                afterCapture = SystemClock.uptimeMillis()
                handler.post { ScreenshotHider.revealAll() }

                val bitmap = ocrBitmap
                if (bitmap == null) {
                    Log.d(
                        tag,
                        "OCR prefill empty bitmap: hideDelay=${afterHideDelay - startedAt}ms " +
                            "capture=${afterCapture - beforeCapture}ms area=${area.width()}x${area.height()}"
                    )
                    ""
                } else {
                    try {
                        val beforeRecognize = SystemClock.uptimeMillis()
                        val text = OcrHelper.recognizeTextFromBitmap(bitmap, filterMode)
                        val afterRecognize = SystemClock.uptimeMillis()
                        Log.d(
                            tag,
                            "OCR prefill timing: hideDelay=${afterHideDelay - startedAt}ms " +
                                "capture=${afterCapture - beforeCapture}ms " +
                                "recognize=${afterRecognize - beforeRecognize}ms " +
                                "bitmap=${bitmap.width}x${bitmap.height} " +
                                "filter=$filterMode " +
                                "textLength=${text.length}"
                        )
                        text
                    } finally {
                        bitmap.recycle()
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                handler.post { ScreenshotHider.revealAll() }
                ""
            } catch (e: Exception) {
                Log.w(tag, "Failed to prefill text condition from OCR area", e)
                handler.post { ScreenshotHider.revealAll() }
                ""
            }

            handler.post {
                if (generation != conditionOcrPrefillGeneration) return@post
                conditionOcrPrefillRunning = false
                val recognizedText = TextConditionDetector.prefillText(ocrText)
                val textInput = pickerView?.findViewById<EditText>(R.id.conditionTextInput)
                if (condEditType == ActionStep.CONDITION_TEXT_CONTAINS && textInput != null) {
                    textInput.hint = getString(R.string.condition_text_hint)
                    val currentInput = textInput.text?.toString().orEmpty()
                    if (OcrPrefillTextUpdatePolicy.shouldApply(currentInput, recognizedText)) {
                        condEditText = recognizedText
                        textInput.setText(recognizedText)
                        textInput.setSelection(textInput.text?.length ?: 0)
                    }
                }
                if (textInput != null && recognizedText.isEmpty()) {
                    Toast.makeText(this, getString(R.string.condition_ocr_prefill_empty), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showColorPickerOverlay(
        onColorSelected: ((Int, Int, String) -> Unit)? = null,
        onCancelled: (() -> Unit)? = null
    ) {
        val wm = windowManager ?: return

        if (!ScreenCaptureManager.isReady) {
            Toast.makeText(this, getString(R.string.screen_capture_not_ready), Toast.LENGTH_SHORT).show()
            onCancelled?.invoke()
            return
        }
        ScreenCaptureManager.refreshDisplayMetrics(this)
        if (!ScreenCaptureManager.isReady) {
            Toast.makeText(this, getString(R.string.screen_capture_not_ready), Toast.LENGTH_SHORT).show()
            onCancelled?.invoke()
            return
        }

        // Hide all overlay windows so they don't appear in the screenshot
        ScreenshotHider.hideAll()

        val screenW = ScreenCaptureManager.getCaptureWidth()
        val screenH = ScreenCaptureManager.getCaptureHeight()

        // Capture one frame and reuse it for the entire picking session.
        // Wait for the window manager to render a frame without the overlays,
        // then capture on a background thread.
        Thread {
            Thread.sleep(300)
            val image = ScreenCaptureManager.captureFrameSync(COLOR_PICK_CAPTURE_TIMEOUT_MS)
            android.os.Handler(mainLooper).post {
                if (image == null) {
                    ScreenshotHider.revealAll()
                    Toast.makeText(this, getString(R.string.condition_color_pick_failed), Toast.LENGTH_SHORT).show()
                    if (onCancelled != null) {
                        onCancelled()
                        return@post
                    }
                    showConditionPicker()
                    return@post
                }

                val overlay = ColorPickerOverlayView(this)

                val initX = screenW / 2
                val initY = screenH / 2
                val initHex = samplePixelHex(image, initX, initY, screenW, screenH)
                overlay.setInitialPosition(initX, initY, initHex)

                overlay.colorSampler = { x, y ->
                    samplePixelHex(image, x, y, screenW, screenH)
                }

                overlay.onConfirm = { x, y, hex ->
                    try { wm.removeView(overlay) } catch (_: Exception) {}
                    pickerView = null
                    image.close()
                    ScreenshotHider.revealAll()
                    if (onColorSelected != null) {
                        onColorSelected(x, y, hex)
                    } else {
                        condEditColorHex = hex
                        condEditColorX = pixelXToStoredPercent(x)
                        condEditColorY = pixelYToStoredPercent(y)
                        showConditionPicker()
                    }
                }

                overlay.onCancel = {
                    try { wm.removeView(overlay) } catch (_: Exception) {}
                    pickerView = null
                    image.close()
                    ScreenshotHider.revealAll()
                    if (onCancelled != null) {
                        onCancelled()
                    } else {
                        showConditionPicker()
                    }
                }

                pickerView = overlay

                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                )
                params.gravity = Gravity.TOP or Gravity.START
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    params.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                wm.addView(overlay, params)
            }
        }.start()
    }

    private fun samplePixelHex(
        image: android.media.Image,
        screenX: Int, screenY: Int,
        screenW: Int, screenH: Int
    ): String {
        val point = ScreenCapturePointMapper.mapScreenPointToCapturePoint(
            screenX = screenX,
            screenY = screenY,
            screenWidth = screenW,
            screenHeight = screenH,
            captureWidth = image.width,
            captureHeight = image.height
        ) ?: return "#000000"
        val pixel = ScreenCaptureManager.readPixel(image, point.x, point.y)
        val r = android.graphics.Color.red(pixel)
        val g = android.graphics.Color.green(pixel)
        val b = android.graphics.Color.blue(pixel)
        return String.format("#%02X%02X%02X", r, g, b)
    }

    private fun hidePositionMarkersForScreenshot() {
        val wm = windowManager ?: return
        for (view in positionMarkerViews) {
            try {
                wm.removeView(view)
            } catch (_: Exception) {}
        }
        positionMarkerViews.clear()
        markerBindings.clear()
    }

    private fun restoreFloatingUI() {
        floatingView?.visibility = View.VISIBLE
        if (positionVisible) {
            showPositionMarkers()
        }
    }

    private fun parseMsInput(input: String): Long {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return 1L
        return try {
            trimmed.toLong().coerceAtLeast(1L)
        } catch (_: NumberFormatException) {
            1L
        }
    }

    private fun parseRepeatCount(input: String): Int {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return 1
        return try {
            trimmed.toInt().coerceAtLeast(0)
        } catch (_: NumberFormatException) {
            1
        }
    }

    private fun toggleLoopSettingsPanel() {
        if (loopPanelView != null) {
            hideLoopSettingsPanel()
        } else {
            showLoopSettingsPanel()
        }
    }

    @SuppressLint("InflateParams")
    private fun showLoopSettingsPanel() {
        val wm = windowManager ?: return
        if (loopPanelView != null) return
        hideAddPanel()
        floatingView?.findViewById<View>(R.id.saveConfirmPanel)?.visibility = View.GONE

        val panel = LayoutInflater.from(this).inflate(R.layout.floating_loop_panel, null)
        val params = loopPanelParams ?: createPanelParams(220, 88)
        loopPanelView = panel
        loopPanelParams = params
        bindPanelDrag(panel.findViewById(R.id.loopPanelDragHandle), panel, params)
        panel.findViewById<View>(R.id.loopSettingsSave).setOnClickListener {
            saveLoopSettings()
        }
        wm.addView(panel, params)
        ControlZoneChecker.register("floating_loop_panel") { panelZoneRect(loopPanelView, loopPanelParams) }
        ScreenshotHider.register("floating_loop_panel",
            hide = { loopPanelView?.visibility = View.INVISIBLE },
            reveal = { loopPanelView?.visibility = View.VISIBLE }
        )
        loadLoopSettings()
        enableOverlayFocus()
    }

    private fun hideLoopSettingsPanel() {
        removePanel(loopPanelView, "floating_loop_panel") {
            loopPanelView = null
        }
        disableOverlayFocus()
    }

    private fun saveLoopSettings() {
        val view = loopPanelView ?: return
        val countInput = view.findViewById<EditText>(R.id.loopCountInput)
        val gapInput = view.findViewById<EditText>(R.id.loopGapInput)

        val countText = countInput.text.toString().trim()
        val loopCount = if (countText.isEmpty()) {
            1
        } else {
            val parsed = countText.toIntOrNull() ?: 1
            if (parsed < 0) {
                Toast.makeText(this, getString(R.string.loop_count_negative), Toast.LENGTH_SHORT).show()
                return
            }
            parsed
        }

        val gapText = gapInput.text.toString().trim()
        val loopGapMs = if (gapText.isEmpty()) {
            0L
        } else {
            gapText.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_LOOP_COUNT, loopCount)
            .putLong(KEY_LOOP_GAP_MS, loopGapMs)
            .putBoolean(KEY_LOOP_SETTINGS_SAVED, true)
            .apply()

        hideLoopSettingsPanel()
        Toast.makeText(this, getString(R.string.loop_settings_saved), Toast.LENGTH_SHORT).show()
    }

    private fun loadLoopSettings() {
        val savedCount = getLoopCount(this)
        val savedGap = getLoopGapMs(this)

        val view = loopPanelView ?: return
        view.findViewById<EditText>(R.id.loopCountInput).setText(savedCount.toString())
        view.findViewById<EditText>(R.id.loopGapInput).setText(savedGap.toString())
    }

    private fun updateLoopUI() {
        val view = floatingView ?: return
        val loopBtn = view.findViewById<TextView>(R.id.loopButton)
        loopBtn.text = getString(R.string.loop_button)
        loopBtn.setTextColor(0xFFE5E7EB.toInt())
        loopBtn.background = getDrawable(R.drawable.floating_pill_secondary)
        hideLoopSettingsPanel()
        disableOverlayFocus()
    }

    private fun updateStartStopButtons(running: Boolean) {
        val view = floatingView ?: return
        val startBtn = view.findViewById<TextView>(R.id.startButton)
        val collapsedStartBtn = view.findViewById<TextView>(R.id.collapsedStartButton)

        if (running) {
            startBtn.text = getString(R.string.stop_action)
            startBtn.setTextColor(Color.WHITE)
            startBtn.background = getDrawable(R.drawable.floating_pill_danger)
            collapsedStartBtn.text = getString(R.string.stop_action)
            collapsedStartBtn.setTextColor(Color.WHITE)
            collapsedStartBtn.background = getDrawable(R.drawable.floating_pill_danger)
        } else {
            startBtn.text = getString(R.string.start_action)
            startBtn.setTextColor(Color.WHITE)
            startBtn.background = getDrawable(R.drawable.floating_pill_primary)
            collapsedStartBtn.text = getString(R.string.start_action)
            collapsedStartBtn.setTextColor(Color.WHITE)
            collapsedStartBtn.background = getDrawable(R.drawable.floating_pill_primary)
        }
    }

    private fun getEditingScriptName(): String? {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getString(KEY_EDITING_SCRIPT_NAME, null)
    }

    private fun setEditingScriptName(name: String?) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (name != null) {
            prefs.edit().putString(KEY_EDITING_SCRIPT_NAME, name).apply()
        } else {
            prefs.edit().remove(KEY_EDITING_SCRIPT_NAME).apply()
        }
        updateFloatingTitle()
    }

    companion object {
        const val CHANNEL_ID = "floating_control_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP_EXECUTION = "com.fffcccdfgh.androidclicker.STOP_EXECUTION"
        const val ACTION_STATE_CHANGED = "com.fffcccdfgh.androidclicker.FLOATING_STATE_CHANGED"
        const val EXTRA_IS_RUNNING = "is_running"
        var isRunning = false
            private set

        const val PREFS_NAME = "tap_config"
        const val KEY_ACTION_SEQUENCE = "action_sequence"
        const val KEY_EDITING_SCRIPT_NAME = "current_editing_script_name"
        const val KEY_LOOP_ENABLED = "loop_enabled"
        const val KEY_LOOP_COUNT = "loop_count"
        const val KEY_LOOP_GAP_MS = "loop_gap_ms"
        const val KEY_LOOP_SETTINGS_SAVED = "loop_settings_saved"

        fun getLoopCount(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_LOOP_SETTINGS_SAVED, false)) {
                return 1
            }
            return prefs.getInt(KEY_LOOP_COUNT, 1).coerceAtLeast(0)
        }

        fun getLoopGapMs(context: Context): Long {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_LOOP_SETTINGS_SAVED, false)) {
                return 0L
            }
            return prefs.getLong(KEY_LOOP_GAP_MS, 0L).coerceAtLeast(0L)
        }

        private const val PICKER_TAP_POINT = 0
        private const val PICKER_SWIPE_START = 1
        private const val PICKER_SWIPE_END = 2
        private const val DRAG_THRESHOLD_DP = 12f
        private const val COLOR_PICK_CAPTURE_TIMEOUT_MS = 3000L
        private const val ACTION_LIST_ROW_HEIGHT_DP = 44f
        private const val ACTION_LIST_PANEL_WIDTH_DP = 292f
        private const val ACTION_LIST_MAX_VISIBLE_ROWS = 20
        private const val ACTION_LIST_MIN_HEIGHT_DP = 160f
        private const val ACTION_LIST_RESERVED_HEIGHT_DP = 128f
        private const val ACTION_LIST_BOTTOM_MARGIN_DP = 16f
        private const val ACTION_SETTINGS_PANEL_WIDTH_DP = 240f
        private const val PROGRAM_TEMPLATE_PANEL_WIDTH_DP = 292f
        private const val PROGRAM_TEMPLATE_ROW_HEIGHT_DP = 44f
        private const val PROGRAM_TEMPLATE_MAX_VISIBLE_ROWS = 9
    }
}
