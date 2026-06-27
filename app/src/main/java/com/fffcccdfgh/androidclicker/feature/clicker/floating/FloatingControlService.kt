package com.fffcccdfgh.androidclicker.feature.clicker.floating

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
import android.content.res.Configuration
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
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
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
import com.fffcccdfgh.androidclicker.R
import com.fffcccdfgh.androidclicker.app.MainActivity
import com.fffcccdfgh.androidclicker.core.execution.ActionSequenceExecutor
import com.fffcccdfgh.androidclicker.core.execution.ActionStep
import com.fffcccdfgh.androidclicker.core.execution.ControlZoneChecker
import com.fffcccdfgh.androidclicker.core.execution.ExecutionOverlayWindowPolicy
import com.fffcccdfgh.androidclicker.core.execution.ExecutionStopButtonOverlay
import com.fffcccdfgh.androidclicker.core.ocr.OcrBitmapQuality
import com.fffcccdfgh.androidclicker.core.ocr.OcrDebugConfig
import com.fffcccdfgh.androidclicker.core.ocr.OcrDebugImagePolicy
import com.fffcccdfgh.androidclicker.core.ocr.OcrDebugImageSaver
import com.fffcccdfgh.androidclicker.core.ocr.OcrHelper
import com.fffcccdfgh.androidclicker.core.ocr.OcrPrefillCapturePolicy
import com.fffcccdfgh.androidclicker.core.ocr.OcrPrefillTextUpdatePolicy
import com.fffcccdfgh.androidclicker.core.ocr.OcrScreenCaptureReadinessPolicy
import com.fffcccdfgh.androidclicker.core.ocr.OcrTimingPolicy
import com.fffcccdfgh.androidclicker.core.ocr.TextConditionDetector
import com.fffcccdfgh.androidclicker.core.overlay.FloatingPanelController
import com.fffcccdfgh.androidclicker.core.overlay.FloatingWindowLayoutPolicy
import com.fffcccdfgh.androidclicker.core.overlay.FloatingWindowSize
import com.fffcccdfgh.androidclicker.core.overlay.FloatingWindowSizePolicy
import com.fffcccdfgh.androidclicker.core.picker.ActionMarkersOverlayView
import com.fffcccdfgh.androidclicker.core.picker.AreaSelectionView
import com.fffcccdfgh.androidclicker.core.picker.ColorPickerOverlayView
import com.fffcccdfgh.androidclicker.core.picker.PointPickerOverlayView
import com.fffcccdfgh.androidclicker.core.picker.SwipePickerOverlayView
import com.fffcccdfgh.androidclicker.core.program.ProgramCodeScrollBar
import com.fffcccdfgh.androidclicker.core.program.ProgramCoordinateAdapter
import com.fffcccdfgh.androidclicker.core.program.ProgramEditorTextActions
import com.fffcccdfgh.androidclicker.core.program.ProgramEditorTextResult
import com.fffcccdfgh.androidclicker.core.program.ProgramEditorWindowPolicy
import com.fffcccdfgh.androidclicker.core.program.ProgramLineNumberView
import com.fffcccdfgh.androidclicker.core.program.ProgramLuaAssist
import com.fffcccdfgh.androidclicker.core.program.ProgramScreenSize
import com.fffcccdfgh.androidclicker.core.program.ProgramScreenSizePolicy
import com.fffcccdfgh.androidclicker.core.program.ProgramTemplateMenuLayout
import com.fffcccdfgh.androidclicker.core.program.ProgramTemplateMenuScrollBar
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenCaptureDisplayReader
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenCaptureManager
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenCapturePermissionActivity
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenCapturePointMapper
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenshotHider

class FloatingControlService : Service() {
    private val tag = "FloatingControlService"
    private val stopDebugTag = "ClickerStopDebug"

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var floatingParams: WindowManager.LayoutParams? = null
    private var floatingControlSize: FloatingWindowSize? = null
    private var addPanelView: View? = null
    private var addPanelParams: WindowManager.LayoutParams? = null
    private var actionListPanelView: View? = null
    private var actionListPanelParams: WindowManager.LayoutParams? = null
    private var loopPanelView: View? = null
    private var loopPanelParams: WindowManager.LayoutParams? = null
    private var saveConfirmPanelView: View? = null
    private var saveConfirmPanelParams: WindowManager.LayoutParams? = null
    private var programTemplatePanelView: View? = null
    private var programTemplatePanelParams: WindowManager.LayoutParams? = null
    private var floatingTouchThrough = false
    private var executionStopButton: ExecutionStopButtonOverlay? = null
    private var stopButtonPositioning = false
    private var pickerView: View? = null
    private var pickerHiddenFloatingWasVisible = false
    private var programEditorParams: WindowManager.LayoutParams? = null
    private var pickerMode: Int = PICKER_TAP_POINT
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var pendingStartX = 0
    private var pendingStartY = 0
    private var actionListVisible = false
    private var actionMarkersOverlayView: ActionMarkersOverlayView? = null
    private var positionVisible = false
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartParamX = 0
    private var dragStartParamY = 0
    private var screenWidthPx = 0
    private var screenHeightPx = 0
    private var settingsActionIndex = -1
    private var condEditType: String? = null
    private var condEditInvert: Boolean = false
    private var condEditText: String? = null
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
    private lateinit var scriptController: ClickerScriptController
    private lateinit var executionController: ClickerExecutionController
    private val floatingWindowController by lazy {
        ClickerFloatingWindowController(
            host = this
        )
    }
    private val pickerCoordinator by lazy {
        ClickerPickerCoordinator(host = this)
    }
    private val programEditorController by lazy {
        ClickerProgramEditorController(host = this)
    }
    private val floatingPanelController by lazy {
        FloatingPanelController(
            windowManagerProvider = { windowManager },
            touchThroughProvider = { floatingTouchThrough }
        )
    }

    override fun onCreate() {
        super.onCreate()
        scriptController = ClickerScriptController(this)
        executionController = ClickerExecutionController(
            context = this,
            loadSequence = { scriptController.loadSequence() },
            hideBeforeRun = { hideAllBeforeRun() },
            setFloatingTouchThrough = { enabled -> floatingWindowController.setFloatingTouchThrough(enabled) },
            showExecutionStopButton = { showExecutionStopButton() },
            hideExecutionStopButton = { hideExecutionStopButton() },
            updateStartStopButtons = { running -> updateStartStopButtons(running) },
            controlZonePaddingPx = { ControlZoneChecker.dpToPx(resources.displayMetrics.density) }
        )
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_EXECUTION) {
            Log.d(stopDebugTag, "FloatingControlService notification stop clicked isRunning=${ActionSequenceExecutor.isRunning}")
            executionController.stopExecution()
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
        pickerCoordinator.hidePositionMarkers()
        pickerCoordinator.hidePickerOverlay()
        floatingWindowController.showFloatingControl()
        isRunning = true
        broadcastRunningState()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        floatingWindowController.updateFloatingControlSizeForCurrentDisplay()
        programEditorController.updateProgramEditorSizeForCurrentDisplay()
        programEditorController.updateProgramTemplatePanelSizeForCurrentDisplay()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(stopDebugTag, "FloatingControlService.onDestroy isRunning=${ActionSequenceExecutor.isRunning}")
        if (ActionSequenceExecutor.isRunning) {
            Log.d(stopDebugTag, "FloatingControlService.onDestroy stopping executor")
            executionController.stopExecution()
        }
        pickerCoordinator.hidePositionMarkers()
        pickerCoordinator.hidePickerOverlay()
        hideExecutionStopButton()
        floatingWindowController.hideFloatingControl()
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
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.floating_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
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

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    internal fun refreshCurrentScreenSizeImpl() {
        val display = ScreenCaptureDisplayReader.current(this)
        screenWidthPx = display.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        screenHeightPx = display.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
    }

    internal fun calculateFloatingControlSizeForCurrentDisplayImpl(): FloatingWindowSize {
        refreshCurrentScreenSizeImpl()
        return FloatingWindowSizePolicy.expandedControlSize(screenWidthPx, screenHeightPx)
    }

    internal fun calculateCollapsedControlSizeForCurrentDisplayImpl(): FloatingWindowSize {
        refreshCurrentScreenSizeImpl()
        return FloatingWindowSizePolicy.collapsedControlSize(screenWidthPx, screenHeightPx)
    }

    internal fun updateFloatingControlSizeForCurrentDisplayImpl() {
        val view = floatingView ?: return
        val controlSize = calculateFloatingControlSizeForCurrentDisplayImpl()
        floatingControlSize = controlSize

        val expandedControls = view.findViewById<View>(R.id.expandedControls)
        val collapsedControls = view.findViewById<View>(R.id.collapsedControls)
        val saveConfirmPanel = view.findViewById<View>(R.id.saveConfirmPanel)
        if (saveConfirmPanel.visibility == View.VISIBLE) {
            return
        }

        if (collapsedControls.visibility == View.VISIBLE) {
            applyCollapsedFloatingControlLayoutImpl(view, FloatingWindowSizePolicy.collapsedControlSize(screenWidthPx, screenHeightPx))
            return
        }

        if (expandedControls.visibility != View.VISIBLE) {
            return
        }

        applyExpandedFloatingControlLayoutImpl(view, controlSize)
        floatingParams?.let { lp ->
            lp.width = controlSize.widthPx
            lp.height = controlSize.heightPx
            if (view.isAttachedToWindow) {
                windowManager?.updateViewLayout(view, lp)
            }
        }
    }

    internal fun applyCollapsedFloatingControlLayoutImpl(view: View, controlSize: FloatingWindowSize) {
        val root = view.findViewById<View>(R.id.floatingRoot)
        val collapsedControls = view.findViewById<LinearLayout>(R.id.collapsedControls)
        val collapsedTitle = view.findViewById<TextView>(R.id.collapsedTitle)
        val collapsedButtons = listOf<View>(
            view.findViewById(R.id.collapsedStartButton),
            view.findViewById(R.id.collapsedCloseButton),
            view.findViewById(R.id.collapsedExpandButton)
        )

        collapsedControls.layoutParams = collapsedControls.layoutParams?.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }

        val horizontalPadding = root.paddingLeft + root.paddingRight
        val verticalPadding = root.paddingTop + root.paddingBottom
        val contentWidth = (controlSize.widthPx - horizontalPadding).coerceAtLeast(1)
        val contentHeight = (controlSize.heightPx - verticalPadding).coerceAtLeast(1)
        val gapPx = (contentWidth / 72).coerceIn(dpToPx(2), dpToPx(8))
        val availableWidth = (contentWidth - gapPx * COLLAPSED_CONTROL_GAP_COUNT).coerceAtLeast(1)
        val itemWidth = (availableWidth / COLLAPSED_CONTROL_ITEM_COUNT).coerceAtLeast(1)

        collapsedTitle.minimumWidth = 0
        collapsedTitle.setPadding(0, collapsedTitle.paddingTop, 0, collapsedTitle.paddingBottom)
        collapsedTitle.gravity = Gravity.CENTER
        collapsedTitle.layoutParams = (collapsedTitle.layoutParams as LinearLayout.LayoutParams).apply {
            width = itemWidth
            height = contentHeight
            weight = 0f
            marginStart = 0
            marginEnd = 0
        }

        collapsedButtons.forEachIndexed { index, button ->
            button.minimumWidth = 0
            button.setPadding(0, button.paddingTop, 0, button.paddingBottom)
            button.layoutParams = (button.layoutParams as LinearLayout.LayoutParams).apply {
                width = itemWidth
                height = contentHeight
                weight = 0f
                marginStart = gapPx
                marginEnd = 0
            }
        }

        floatingParams?.let { lp ->
            lp.width = controlSize.widthPx
            lp.height = controlSize.heightPx
            if (view.isAttachedToWindow) {
                windowManager?.updateViewLayout(view, lp)
            }
        }
    }

    internal fun applyExpandedFloatingControlLayoutImpl(view: View, controlSize: FloatingWindowSize) {
        val root = view.findViewById<View>(R.id.floatingRoot)
        val expandedControls = view.findViewById<View>(R.id.expandedControls)
        val dragHandle = view.findViewById<TextView>(R.id.dragHandle)
        val foldButton = view.findViewById<TextView>(R.id.foldButton)
        val closeButton = view.findViewById<TextView>(R.id.closeButton)
        val mainActionRow = view.findViewById<LinearLayout>(R.id.mainActionRow)
        val actionButtons = listOf<View>(
            view.findViewById(R.id.startButton),
            view.findViewById(R.id.addButton),
            view.findViewById(R.id.listButton),
            view.findViewById(R.id.saveButton),
            view.findViewById(R.id.positionButton),
            view.findViewById(R.id.stopPositionButton),
            view.findViewById(R.id.loopButton)
        )

        expandedControls.layoutParams = expandedControls.layoutParams?.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }

        val horizontalPadding = root.paddingLeft + root.paddingRight
        val verticalPadding = root.paddingTop + root.paddingBottom
        val contentWidth = (controlSize.widthPx - horizontalPadding).coerceAtLeast(1)
        val contentHeight = (controlSize.heightPx - verticalPadding).coerceAtLeast(1)
        val gapPx = (contentWidth / 64).coerceIn(dpToPx(2), dpToPx(8))
        val buttonHeight = ((contentHeight - gapPx) / 2).coerceAtLeast(1)
        val actionButtonWidth = ((contentWidth - gapPx * (actionButtons.size - 1)) / actionButtons.size)
            .coerceAtLeast(1)

        mainActionRow.layoutParams = (mainActionRow.layoutParams as LinearLayout.LayoutParams).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = 0
            weight = 1f
            topMargin = gapPx
        }

        actionButtons.forEachIndexed { index, button ->
            button.minimumWidth = 0
            button.setPadding(0, button.paddingTop, 0, button.paddingBottom)
            button.layoutParams = (button.layoutParams as LinearLayout.LayoutParams).apply {
                width = actionButtonWidth
                height = buttonHeight
                weight = 0f
                marginStart = if (index == 0) 0 else gapPx
            }
        }

        listOf(foldButton, closeButton).forEach { button ->
            button.minimumWidth = 0
            button.setPadding(0, button.paddingTop, 0, button.paddingBottom)
            button.layoutParams = (button.layoutParams as LinearLayout.LayoutParams).apply {
                width = actionButtonWidth
                height = buttonHeight
                weight = 0f
                marginStart = gapPx
            }
        }

        dragHandle.layoutParams = (dragHandle.layoutParams as LinearLayout.LayoutParams).apply {
            width = 0
            height = buttonHeight
            weight = 1f
            marginStart = 0
        }
    }

    @SuppressLint("InflateParams")
    internal fun showFloatingControlImpl() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.floating_control, null)
        floatingView = view

        val wm = windowManager ?: return
        val controlSize = calculateFloatingControlSizeForCurrentDisplayImpl()
        floatingControlSize = controlSize

        floatingParams = WindowManager.LayoutParams(
            controlSize.widthPx,
            controlSize.heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
                        if (actionListVisible) updateActionListViewportImpl(scriptController.loadSequence().size)
                    }
                    true
                }
                else -> false
            }
        }

        bindStartStopTouch(view.findViewById(R.id.startButton)) {
            executionController.executeSequence()
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
            toggleActionListImpl()
        }

        view.findViewById<View>(R.id.positionButton).setOnClickListener {
            togglePositionMarkersImpl()
        }

        view.findViewById<View>(R.id.saveButton).setOnClickListener {
            val sequence = scriptController.loadSequence()
            if (sequence.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_action_to_save), Toast.LENGTH_SHORT).show()
            } else {
                hideFloatingPanels()
                val editingName = scriptController.getEditingScriptName()
                showSaveConfirmPanel(editingName ?: scriptController.nextAutoName())
            }
        }

        view.findViewById<View>(R.id.closeButton).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (ActionSequenceExecutor.isRunning) {
                        executionController.stopExecution()
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
            collapseFloatingControlImpl()
        }

        // Collapsed mode controls
        bindStartStopTouch(view.findViewById(R.id.collapsedStartButton)) {
            executionController.executeSequence()
        }

        view.findViewById<View>(R.id.collapsedCloseButton).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (ActionSequenceExecutor.isRunning) {
                        executionController.stopExecution()
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
            expandFloatingControlImpl()
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

        updateFloatingTitleImpl()
        applyExpandedFloatingControlLayoutImpl(view, controlSize)

        wm.addView(view, floatingParams)
        ControlZoneChecker.register("floating_control") { getControlZoneRectImpl() }

        ScreenshotHider.register("floating_control",
            hide = {
                floatingView?.visibility = View.INVISIBLE
                hidePositionMarkersForScreenshotImpl()
            },
            reveal = {
                floatingView?.visibility = View.VISIBLE
                if (positionVisible) {
                    showPositionMarkersImpl()
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
                        executionController.stopExecution()
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

    internal fun getControlZoneRectImpl(): Rect? {
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
                onStop = { executionController.stopExecution() }
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
                onStop = { executionController.stopExecution() }
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

    internal fun setFloatingTouchThroughImpl(enabled: Boolean) {
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

    internal fun enableOverlayFocusImpl() {
        updateFloatingFlagsImpl(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
    }

    internal fun disableOverlayFocusImpl() {
        updateFloatingFlagsImpl(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    internal fun updateFloatingFlagsImpl(flags: Int) {
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

    @SuppressLint("InflateParams")
    private fun showSaveConfirmPanel(initialName: String) {
        val wm = windowManager ?: return
        if (saveConfirmPanelView != null) {
            hideSaveConfirmPanel()
        }

        val panel = LayoutInflater.from(this).inflate(R.layout.floating_save_confirm_panel, null)
        val params = createSaveConfirmPanelParams()
        saveConfirmPanelView = panel
        saveConfirmPanelParams = params

        panel.findViewById<TextView>(R.id.savePanelTitle).text = getString(R.string.confirm_save)
        val nameInput = panel.findViewById<EditText>(R.id.savePanelNameInput)
        nameInput.setText(initialName)
        nameInput.selectAll()
        bindPanelDrag(panel.findViewById(R.id.savePanelHeader), panel, params)
        panel.findViewById<View>(R.id.savePanelConfirmButton).setOnClickListener {
            saveCurrentSequenceWithConfirmedName(nameInput.text.toString().trim())
        }
        val dismiss = View.OnClickListener { hideSaveConfirmPanel() }
        panel.findViewById<View>(R.id.savePanelCancelButton).setOnClickListener(dismiss)

        floatingPanelController.show(
            panel = panel,
            params = params,
            zoneKey = "floating_save_confirm_panel",
            viewProvider = { saveConfirmPanelView },
            paramsProvider = { saveConfirmPanelParams }
        )
        enableSaveConfirmInput(nameInput)
    }

    private fun createSaveConfirmPanelParams(): WindowManager.LayoutParams {
        val screen = ScreenCaptureDisplayReader.current(this)
        val screenWidth = screen.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val screenHeight = screen.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val panelSize = FloatingWindowSizePolicy.saveConfirmPanelEstimatedSize(resources.displayMetrics.density)
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayTypeImpl(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            FloatingWindowLayoutPolicy.applyCenteredPosition(this, screenWidth, screenHeight, panelSize)
        }
    }

    private fun enableSaveConfirmInput(nameInput: EditText) {
        nameInput.requestFocus()
        nameInput.post {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(nameInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun saveCurrentSequenceWithConfirmedName(name: String) {
        if (name.isEmpty()) {
            Toast.makeText(this, getString(R.string.script_name_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val editingName = scriptController.getEditingScriptName()
        if (name != editingName && scriptController.hasSavedScript(name)) {
            Toast.makeText(this, getString(R.string.script_name_exists), Toast.LENGTH_SHORT).show()
            return
        }
        val sequence = scriptController.loadSequence()
        scriptController.saveCurrentSequenceWithConfirmedName(name, sequence)
        updateFloatingTitleImpl()
        val msg = when {
            editingName != null && name == editingName -> getString(R.string.script_updated)
            editingName != null -> getString(R.string.script_saved_as)
            else -> getString(R.string.script_saved)
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        sendBroadcast(Intent(scriptController.scriptsChangedAction()))
        hideSaveConfirmPanel()
    }

    private fun hideSaveConfirmPanel() {
        removePanel(saveConfirmPanelView, "floating_save_confirm_panel") {
            saveConfirmPanelView = null
        }
        saveConfirmPanelParams = null
    }

    private fun hideSaveConfirmPanel(view: View) {
        val saveConfirmPanel = view.findViewById<View>(R.id.saveConfirmPanel)
        if (saveConfirmPanel.visibility == View.GONE) return

        saveConfirmPanel.visibility = View.GONE
        floatingControlSize?.let { controlSize ->
            applyExpandedFloatingControlLayoutImpl(view, controlSize)
            floatingParams?.let { lp ->
                lp.width = controlSize.widthPx
                lp.height = controlSize.heightPx
                if (view.isAttachedToWindow) {
                    windowManager?.updateViewLayout(view, lp)
                }
            }
        }
        disableOverlayFocusImpl()
    }

    internal fun collapseFloatingControlImpl() {
        val view = floatingView ?: return
        hideSaveConfirmPanel()
        hideSaveConfirmPanel(view)
        view.findViewById<View>(R.id.expandedControls).visibility = View.GONE
        view.findViewById<View>(R.id.collapsedControls).visibility = View.VISIBLE
        applyCollapsedFloatingControlLayoutImpl(view, calculateCollapsedControlSizeForCurrentDisplayImpl())
        updateFloatingTitleImpl()
    }

    internal fun expandFloatingControlImpl() {
        val view = floatingView ?: return
        view.findViewById<View>(R.id.collapsedControls).visibility = View.GONE
        view.findViewById<View>(R.id.expandedControls).visibility = View.VISIBLE
        floatingControlSize?.let { controlSize ->
            applyExpandedFloatingControlLayoutImpl(view, controlSize)
            floatingParams?.let { lp ->
                lp.width = controlSize.widthPx
                lp.height = controlSize.heightPx
                windowManager?.updateViewLayout(view, lp)
            }
        }
    }

    private fun hideAllPanels() {
        hideExpandedPanels()
        hidePositionMarkersImpl()
        positionVisible = false
        updatePositionButton()
        scriptController.clearPendingInsertIndex()
    }

    internal fun updateFloatingTitleImpl() {
        val view = floatingView ?: return
        val name = scriptController.getEditingScriptName()
        val fullTitle = if (name.isNullOrBlank()) getString(R.string.floating_control_label) else name
        val shortTitle = if (name.isNullOrBlank()) getString(R.string.floating_control_label) else name.take(3)
        view.findViewById<TextView>(R.id.dragHandle).text = fullTitle
        view.findViewById<TextView>(R.id.collapsedTitle).text = shortTitle
    }

    internal fun hideFloatingControlImpl() {
        hideExecutionStopButton()
        setFloatingTouchThroughImpl(false)
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

    internal fun overlayTypeImpl(): Int {
        return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    }

    private fun requestScreenCapturePermissionForOcr() {
        Log.i(tag, "requestScreenCapturePermissionForOcr: starting permission activity")
        Toast.makeText(this, getString(R.string.screen_capture_not_ready), Toast.LENGTH_SHORT).show()
        try {
            startActivity(Intent(this, ScreenCapturePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.w(tag, "Failed to request screen capture permission for OCR", e)
            Toast.makeText(this, R.string.screen_capture_not_supported, Toast.LENGTH_SHORT).show()
        }
    }

    private fun createFullScreenPickerParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayTypeImpl(),
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
            overlayTypeImpl(),
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
        floatingPanelController.bindDrag(handle, panel, params)
    }

    private fun removePanel(
        view: View?,
        zoneKey: String,
        onRemoved: () -> Unit
    ) {
        floatingPanelController.remove(view, zoneKey, onRemoved)
    }

    private fun panelZoneRect(view: View?, params: WindowManager.LayoutParams?): Rect? {
        return floatingPanelController.zoneRect(view, params)
    }

    private fun hideFloatingPanels() {
        hideAddPanel()
        hideActionListImpl()
        hideLoopSettingsPanel()
        hideSaveConfirmPanel()
        hideProgramTemplatePanelImpl()
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
        floatingView?.let { hideSaveConfirmPanel(it) }
        hideLoopSettingsPanel()
        disableOverlayFocusImpl()

        val panel = LayoutInflater.from(this).inflate(R.layout.floating_add_panel, null)
        val params = addPanelParams ?: createPanelParams(0, 88)
        addPanelView = panel
        addPanelParams = params

        bindPanelDrag(panel.findViewById(R.id.addPanelDragHandle), panel, params)
        panel.findViewById<View>(R.id.tapOption).setOnClickListener {
            hideAddPanel()
            showPickerOverlayImpl(PICKER_TAP_POINT)
        }
        panel.findViewById<View>(R.id.swipeOption).setOnClickListener {
            hideAddPanel()
            showSwipePickerOverlayImpl()
        }
        panel.findViewById<View>(R.id.waitOption).setOnClickListener {
            hideAddPanel()
            showWaitDurationPickerImpl()
        }
        panel.findViewById<View>(R.id.programOption).setOnClickListener {
            hideAddPanel()
            showProgramEditorImpl()
        }

        floatingPanelController.show(
            panel = panel,
            params = params,
            zoneKey = "floating_add_panel",
            viewProvider = { addPanelView },
            paramsProvider = { addPanelParams }
        )
    }

    private fun hideAddPanel() {
        removePanel(addPanelView, "floating_add_panel") {
            addPanelView = null
        }
    }

    internal fun toggleActionListImpl() {
        if (actionListVisible) {
            hideActionListImpl()
        } else {
            showActionListImpl()
        }
    }

    @SuppressLint("InflateParams")
    internal fun showActionListImpl() {
        val wm = windowManager ?: return
        if (actionListPanelView == null) {
            val panel = LayoutInflater.from(this).inflate(R.layout.floating_action_list_panel, null)
            val params = actionListPanelParams ?: createPanelParams(0, 88)
            actionListPanelView = panel
            actionListPanelParams = params
            bindPanelDrag(panel.findViewById(R.id.actionListPanelHeader), panel, params)
            panel.findViewById<View>(R.id.actionListCloseButton).setOnClickListener {
                hideActionListImpl()
            }
            bindActionListScrollBarImpl(panel)
            floatingPanelController.show(
                panel = panel,
                params = params,
                zoneKey = "floating_action_list_panel",
                viewProvider = { actionListPanelView },
                paramsProvider = { actionListPanelParams }
            )
        }
        floatingView?.let { hideSaveConfirmPanel(it) }
        hideLoopSettingsPanel()
        disableOverlayFocusImpl()
        actionListVisible = true
        renderActionListImpl()
    }

    internal fun hideActionListImpl() {
        actionListVisible = false
        removePanel(actionListPanelView, "floating_action_list_panel") {
            actionListPanelView = null
        }
    }

    internal fun renderActionListImpl() {
        val view = actionListPanelView ?: return
        val sequence = scriptController.loadSequence()
        val container = view.findViewById<LinearLayout>(R.id.actionListContainer)
        container.removeAllViews()

        val header = view.findViewById<TextView>(R.id.actionListHeader)
        val rowWidth = dp(ACTION_LIST_PANEL_WIDTH_DP)

        if (sequence.isEmpty()) {
            header.text = getString(R.string.action_list_empty)
            container.addView(createActionListEmptyRowImpl(rowWidth))
            updateActionListViewportImpl(0)
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
                    formatStoredPercentImpl(action.x!!),
                    formatStoredPercentImpl(action.y!!)
                )
                ActionStep.TYPE_SWIPE -> getString(
                    R.string.action_list_swipe_short,
                    i + 1,
                    formatStoredPercentImpl(action.startX!!),
                    formatStoredPercentImpl(action.startY!!),
                    formatStoredPercentImpl(action.endX!!),
                    formatStoredPercentImpl(action.endY!!)
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

            val settingsBtn = createActionListButtonImpl(
                getString(R.string.settings_action_button),
                0xFF22C55E.toInt(),
                0x3322C55E
            ) {
                showActionSettingsImpl(stepIndex)
            }
            val deleteBtn = createActionListButtonImpl(
                getString(R.string.delete_action),
                0xFFFB7185.toInt(),
                0x33FB7185
            ) {
                deleteActionAtImpl(stepIndex)
            }
            val insertBeforeBtn = createActionListButtonImpl(
                getString(R.string.insert_before),
                0xFF60A5FA.toInt(),
                0x3360A5FA
            ) {
                scriptController.setPendingInsertIndex(stepIndex)
                showInsertTypeMenuImpl()
            }
            val insertAfterBtn = createActionListButtonImpl(
                getString(R.string.insert_after),
                0xFF60A5FA.toInt(),
                0x3360A5FA
            ) {
                scriptController.setPendingInsertIndex(stepIndex + 1)
                showInsertTypeMenuImpl()
            }

            buttonRow.addView(settingsBtn)
            buttonRow.addView(deleteBtn)
            buttonRow.addView(insertBeforeBtn)
            buttonRow.addView(insertAfterBtn)
            row.addView(desc)
            row.addView(buttonRow)
            container.addView(row)
        }
        updateActionListViewportImpl(sequence.size)
    }

    internal fun createActionListEmptyRowImpl(rowWidth: Int): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(rowWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
            background = roundedRect(0xFF1E293B.toInt(), 0xFF334155.toInt(), 12f)
            setPadding(dp(10f), dp(12f), dp(10f), dp(12f))
            text = getString(R.string.action_list_empty)
            setTextColor(0xFFCBD5E1.toInt())
            textSize = 13f
        }
    }

    internal fun createActionListButtonImpl(
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

    internal fun bindActionListScrollBarImpl(view: View) {
        val scroll = view.findViewById<ScrollView>(R.id.actionListScroll)
        val scrollBar = view.findViewById<ProgramTemplateMenuScrollBar>(R.id.actionListScrollBar)
        scrollBar.attachTo(scroll)
    }

    internal fun updateActionListViewportImpl(itemCount: Int) {
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

    internal fun deleteActionAtImpl(index: Int) {
        val sequence = scriptController.loadSequence().toMutableList()
        if (index < 0 || index >= sequence.size) return
        sequence.removeAt(index)
        scriptController.saveSequence(sequence)
        renderActionListImpl()
        refreshMarkersImpl()
    }

    internal fun showPickerOverlayImpl(mode: Int, onPointPicked: ((Int, Int) -> Unit)? = null) {
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
        val instructionView = view.findViewById<TextView>(R.id.pickerInstruction)
        val pointPickerOverlay = view.findViewById<PointPickerOverlayView>(R.id.pointPickerOverlay)
        instructionView.setText(instructionRes)

        if (mode == PICKER_TAP_POINT) {
            pickerHiddenFloatingWasVisible = floatingView?.visibility == View.VISIBLE
            floatingView?.visibility = View.INVISIBLE
            instructionView.visibility = View.GONE
            pointPickerOverlay.visibility = View.VISIBLE
            pointPickerOverlay.instruction = getString(instructionRes)
            pointPickerOverlay.coordinateFormatter = { x, y ->
                "${formatStoredPercentImpl(pixelXToStoredPercentImpl(x))}, ${formatStoredPercentImpl(pixelYToStoredPercentImpl(y))}"
            }
            pointPickerOverlay.onCancel = {
                hidePickerOverlayImpl()
            }
            pointPickerOverlay.onSavePoint = { x, y ->
                if (onPointPicked != null) {
                    hidePickerOverlayImpl()
                    onPointPicked(x, y)
                } else {
                    val storedX = pixelXToStoredPercentImpl(x)
                    val storedY = pixelYToStoredPercentImpl(y)
                    val action = ActionStep(type = ActionStep.TYPE_TAP, x = storedX, y = storedY)
                    appendActionToSequence(action)
                    hidePickerOverlayImpl()
                    Toast.makeText(
                        this,
                        getString(
                            R.string.action_added_tap,
                            formatStoredPercentImpl(storedX),
                            formatStoredPercentImpl(storedY)
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            instructionView.visibility = View.VISIBLE
            pointPickerOverlay.visibility = View.GONE
        }

        view.setOnTouchListener { _, event ->
            if (pickerMode == PICKER_TAP_POINT) return@setOnTouchListener true
            if (event.action == MotionEvent.ACTION_UP) {
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                val message: String

                if (onPointPicked != null) {
                    hidePickerOverlayImpl()
                    onPointPicked(x, y)
                    return@setOnTouchListener true
                }

                when (pickerMode) {
                    PICKER_TAP_POINT -> {
                        val storedX = pixelXToStoredPercentImpl(x)
                        val storedY = pixelYToStoredPercentImpl(y)
                        val action = ActionStep(type = ActionStep.TYPE_TAP, x = storedX, y = storedY)
                        appendActionToSequence(action)
                        message = getString(
                            R.string.action_added_tap,
                            formatStoredPercentImpl(storedX),
                            formatStoredPercentImpl(storedY)
                        )
                    }
                    PICKER_SWIPE_START -> {
                        pendingStartX = pixelXToStoredPercentImpl(x)
                        pendingStartY = pixelYToStoredPercentImpl(y)
                        hidePickerOverlayImpl()
                        Toast.makeText(
                            this,
                            getString(
                                R.string.swipe_start_set,
                                formatStoredPercentImpl(pendingStartX),
                                formatStoredPercentImpl(pendingStartY)
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                        showPickerOverlayImpl(PICKER_SWIPE_END)
                        return@setOnTouchListener true
                    }
                    PICKER_SWIPE_END -> {
                        val action = ActionStep(
                            type = ActionStep.TYPE_SWIPE,
                            startX = pendingStartX,
                            startY = pendingStartY,
                            endX = pixelXToStoredPercentImpl(x),
                            endY = pixelYToStoredPercentImpl(y)
                        )
                        appendActionToSequence(action)
                        message = getString(R.string.action_added_swipe)
                    }
                    else -> {
                        hidePickerOverlayImpl()
                        return@setOnTouchListener true
                    }
                }
                hidePickerOverlayImpl()
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                true
            } else {
                false
            }
        }

        wm.addView(view, params)
    }

    internal fun showSwipePickerOverlayImpl(onSwipePicked: ((Int, Int, Int, Int) -> Unit)? = null) {
        val wm = windowManager ?: return
        if (pickerView != null) return

        pickerMode = PICKER_SWIPE_START
        pickerHiddenFloatingWasVisible = floatingView?.visibility == View.VISIBLE
        floatingView?.visibility = View.INVISIBLE

        val view = LayoutInflater.from(this).inflate(R.layout.activity_position_picker, null)
        pickerView = view

        val pointPickerOverlay = view.findViewById<PointPickerOverlayView>(R.id.pointPickerOverlay)
        val swipePickerOverlay = view.findViewById<SwipePickerOverlayView>(R.id.swipePickerOverlay)
        val instructionView = view.findViewById<TextView>(R.id.pickerInstruction)

        instructionView.visibility = View.GONE
        pointPickerOverlay.visibility = View.GONE
        swipePickerOverlay.visibility = View.VISIBLE
        swipePickerOverlay.coordinateFormatter = { startX, startY, endX, endY, startActive ->
            val startText = "${formatStoredPercentImpl(pixelXToStoredPercentImpl(startX))}, " +
                formatStoredPercentImpl(pixelYToStoredPercentImpl(startY))
            val endText = "${formatStoredPercentImpl(pixelXToStoredPercentImpl(endX))}, " +
                formatStoredPercentImpl(pixelYToStoredPercentImpl(endY))
            if (startActive) {
                "($startText) -> $endText"
            } else {
                "$startText -> ($endText)"
            }
        }
        swipePickerOverlay.onCancel = {
            hidePickerOverlayImpl()
        }
        swipePickerOverlay.onSaveSwipe = { startX, startY, endX, endY ->
            if (onSwipePicked != null) {
                hidePickerOverlayImpl()
                onSwipePicked(startX, startY, endX, endY)
            } else {
                val action = ActionStep(
                    type = ActionStep.TYPE_SWIPE,
                    startX = pixelXToStoredPercentImpl(startX),
                    startY = pixelYToStoredPercentImpl(startY),
                    endX = pixelXToStoredPercentImpl(endX),
                    endY = pixelYToStoredPercentImpl(endY)
                )
                appendActionToSequence(action)
                hidePickerOverlayImpl()
                Toast.makeText(this, getString(R.string.action_added_swipe), Toast.LENGTH_SHORT).show()
            }
        }

        wm.addView(view, createFullScreenPickerParams())
    }

    internal fun hidePickerOverlayImpl() {
        hideProgramTemplatePanelImpl()
        val wm = windowManager
        val view = pickerView ?: return
        view.findViewById<PointPickerOverlayView>(R.id.pointPickerOverlay)?.cleanup()
        view.findViewById<SwipePickerOverlayView>(R.id.swipePickerOverlay)?.cleanup()
        try {
            wm?.removeView(view)
        } catch (_: Exception) {
        }
        ScreenshotHider.unregister("condition_picker")
        if (view.id == R.id.programEditorRoot) {
            programEditorParams = null
        }
        pickerView = null
        if (pickerHiddenFloatingWasVisible) {
            floatingView?.visibility = View.VISIBLE
        }
        pickerHiddenFloatingWasVisible = false
    }

    internal fun togglePositionMarkersImpl() {
        positionVisible = !positionVisible
        if (positionVisible) {
            val view = floatingView
            if (view != null) {
                hideSaveConfirmPanel(view)
                hideFloatingPanels()
                disableOverlayFocusImpl()
            }
            showPositionMarkersImpl()
        } else {
            hidePositionMarkersImpl()
        }
        updatePositionButton()
    }

    internal fun showPositionMarkersImpl() {
        val wm = windowManager ?: return
        if (actionMarkersOverlayView != null) return

        val sequence = scriptController.loadSequence()
        val density = resources.displayMetrics.density
        val screen = programScreenSizeImpl()
        screenWidthPx = screen.width
        screenHeightPx = screen.height

        var waitCount = 0
        val markers = mutableListOf<ActionMarkersOverlayView.Marker>()

        for ((i, action) in sequence.withIndex()) {
            val stepNum = i + 1
            when (action.type) {
                ActionStep.TYPE_TAP -> {
                    val x = storedPercentXToPointPxImpl(action.x ?: continue)
                    val y = storedPercentYToPointPxImpl(action.y ?: continue)
                    markers.add(
                        ActionMarkersOverlayView.Marker(
                            actionIndex = i,
                            pointType = ActionMarkersOverlayView.PointType.TAP,
                            x = x.toFloat(),
                            y = y.toFloat(),
                            label = "${stepNum}${getString(R.string.tap_action)}"
                        )
                    )
                }
                ActionStep.TYPE_SWIPE -> {
                    val sx = storedPercentXToPointPxImpl(action.startX ?: continue)
                    val sy = storedPercentYToPointPxImpl(action.startY ?: continue)
                    val ex = storedPercentXToPointPxImpl(action.endX ?: continue)
                    val ey = storedPercentYToPointPxImpl(action.endY ?: continue)
                    markers.add(
                        ActionMarkersOverlayView.Marker(
                            actionIndex = i,
                            pointType = ActionMarkersOverlayView.PointType.SWIPE_START,
                            x = sx.toFloat(),
                            y = sy.toFloat(),
                            label = "${stepNum}${getString(R.string.swipe_action)}start"
                        )
                    )
                    markers.add(
                        ActionMarkersOverlayView.Marker(
                            actionIndex = i,
                            pointType = ActionMarkersOverlayView.PointType.SWIPE_END,
                            x = ex.toFloat(),
                            y = ey.toFloat(),
                            label = "${stepNum}${getString(R.string.swipe_action)}end"
                        )
                    )
                }
                ActionStep.TYPE_WAIT -> {
                    val waitX = action.markerX ?: ((16 * density).toInt())
                    val waitY = action.markerY
                        ?: ((80 * density).toInt() + waitCount * (56 * density).toInt())
                    markers.add(
                        ActionMarkersOverlayView.Marker(
                            actionIndex = i,
                            pointType = ActionMarkersOverlayView.PointType.WAIT,
                            x = waitX.toFloat(),
                            y = waitY.toFloat(),
                            label = "${stepNum}${getString(R.string.marker_blank)}"
                        )
                    )
                    waitCount++
                }
                ActionStep.TYPE_PROGRAM -> {
                    val progX = action.markerX ?: ((16 * density).toInt())
                    val progY = action.markerY
                        ?: ((80 * density).toInt() + waitCount * (56 * density).toInt())
                    markers.add(
                        ActionMarkersOverlayView.Marker(
                            actionIndex = i,
                            pointType = ActionMarkersOverlayView.PointType.PROGRAM,
                            x = progX.toFloat(),
                            y = progY.toFloat(),
                            label = "${stepNum}\u7F16\u7A0B"
                        )
                    )
                    waitCount++
                }
            }
        }

        val overlay = ActionMarkersOverlayView(this).apply {
            setMarkers(markers)
            onMarkerClick = { actionIndex -> showActionSettingsImpl(actionIndex) }
            onMarkerMoved = { actionIndex, pointType, x, y ->
                updateActionCoordinateImpl(actionIndex, pointType, x, y)
            }
        }
        val params = WindowManager.LayoutParams(
            screenWidthPx,
            screenHeightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        wm.addView(overlay, params)
        actionMarkersOverlayView = overlay
        bringFloatingControlToFrontImpl()
    }

    internal fun bringFloatingControlToFrontImpl() {
        val wm = windowManager ?: return
        val view = floatingView ?: return
        val params = floatingParams ?: return
        try {
            wm.removeView(view)
            wm.addView(view, params)
        } catch (_: Exception) {
        }
    }

    internal fun hidePositionMarkersImpl() {
        val wm = windowManager ?: return
        actionMarkersOverlayView?.let { view ->
            view.cleanup()
            try {
                wm.removeView(view)
            } catch (_: Exception) {
            }
        }
        actionMarkersOverlayView = null
    }

    internal fun refreshMarkersImpl() {
        if (!positionVisible) return
        hidePositionMarkersImpl()
        showPositionMarkersImpl()
    }

    internal fun updateActionCoordinateImpl(
        actionIndex: Int,
        pointType: ActionMarkersOverlayView.PointType,
        newX: Int,
        newY: Int
    ) {
        val sequence = scriptController.loadSequence().toMutableList()
        if (actionIndex >= sequence.size) return

        val oldAction = sequence[actionIndex]
        val storedX = pixelXToStoredPercentImpl(newX)
        val storedY = pixelYToStoredPercentImpl(newY)
        val updatedAction = when (pointType) {
            ActionMarkersOverlayView.PointType.TAP -> oldAction.copy(x = storedX, y = storedY)
            ActionMarkersOverlayView.PointType.SWIPE_START -> oldAction.copy(startX = storedX, startY = storedY)
            ActionMarkersOverlayView.PointType.SWIPE_END -> oldAction.copy(endX = storedX, endY = storedY)
            ActionMarkersOverlayView.PointType.WAIT -> oldAction.copy(markerX = newX, markerY = newY)
            ActionMarkersOverlayView.PointType.PROGRAM -> oldAction.copy(markerX = newX, markerY = newY)
        }
        sequence[actionIndex] = updatedAction
        scriptController.saveSequence(sequence)
        if (actionListVisible) renderActionListImpl()
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
        hideSaveConfirmPanel(view)
        hidePickerOverlayImpl()
    }

    private fun hideAllBeforeRun() {
        scriptController.clearPendingInsertIndex()
        hideExpandedPanels()
        hidePositionMarkersForExecution()
    }

    private fun hidePositionMarkersForExecution() {
        if (!positionVisible) return
        hidePositionMarkersImpl()
        positionVisible = false
        updatePositionButton()
    }

    internal fun showInsertTypeMenuImpl() {
        val wm = windowManager ?: return
        if (pickerView != null) hidePickerOverlayImpl()

        val inflater = LayoutInflater.from(this)
        val picker = inflater.inflate(R.layout.duration_picker, null)
        pickerView = picker

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
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
                    hidePickerOverlayImpl()
                    when (actionType) {
                        ActionStep.TYPE_TAP -> showPickerOverlayImpl(PICKER_TAP_POINT)
                        ActionStep.TYPE_SWIPE -> showSwipePickerOverlayImpl()
                        ActionStep.TYPE_WAIT -> showWaitDurationPickerImpl()
                        ActionStep.TYPE_PROGRAM -> showProgramEditorImpl()
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
                hidePickerOverlayImpl()
            }
        }
        container.addView(cancel)

        wm.addView(picker, params)
    }

    internal fun showWaitDurationPickerImpl() {
        val view = floatingView ?: return
        val wm = windowManager ?: return
        if (pickerView != null) return

        val inflater = LayoutInflater.from(this)
        val picker = inflater.inflate(R.layout.duration_picker, null)
        pickerView = picker

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        bindPanelDrag(picker.findViewById(R.id.durationPickerDragHandle), picker, params)
        bindWaitDurationInputImpl(picker)

        wm.addView(picker, params)
        focusWaitDurationInputImpl(picker.findViewById(R.id.waitDurationInput))
    }

    internal fun bindWaitDurationInputImpl(picker: View) {
        val input = picker.findViewById<EditText>(R.id.waitDurationInput)
        input.isEnabled = true
        input.isFocusable = true
        input.isFocusableInTouchMode = true
        input.isCursorVisible = true
        picker.findViewById<View>(R.id.waitDurationCancelButton).setOnClickListener {
            hidePickerOverlayImpl()
        }
        picker.findViewById<View>(R.id.waitDurationSaveButton).setOnClickListener {
            val durationMs = parseMsInputImpl(input.text.toString())
            val action = ActionStep(type = ActionStep.TYPE_WAIT, durationMs = durationMs)
            appendActionToSequence(action)
            Toast.makeText(
                this@FloatingControlService,
                getString(R.string.action_added_wait, durationMs / 1000.0),
                Toast.LENGTH_SHORT
            ).show()
            hidePickerOverlayImpl()
        }
    }

    internal fun focusWaitDurationInputImpl(input: EditText) {
        input.requestFocus()
        input.selectAll()
        input.post {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    internal fun showProgramEditorImpl() {
        val wm = windowManager ?: return
        if (pickerView != null) hidePickerOverlayImpl()

        val inflater = LayoutInflater.from(this)
        val editor = inflater.inflate(R.layout.program_editor, null)
        pickerView = editor

        val currentDisplay = ScreenCaptureDisplayReader.current(this)
        val editorSize = FloatingWindowSizePolicy.programEditorSizeForDisplay(
            displayWidthPx = currentDisplay.width,
            displayHeightPx = currentDisplay.height,
            resourceWidthPx = resources.displayMetrics.widthPixels,
            resourceHeightPx = resources.displayMetrics.heightPixels,
            density = resources.displayMetrics.density
        )

        val params = WindowManager.LayoutParams(
            editorSize.widthPx,
            editorSize.heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            ProgramEditorWindowPolicy.FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = ProgramEditorWindowPolicy.SOFT_INPUT_MODE
        }
        programEditorParams = params

        val codeInput = editor.findViewById<EditText>(R.id.programCodeInput)
        applyProgramEditorCodePanelHeightImpl(editor, editorSize)
        configureProgramCodeInputImpl(codeInput)
        bindProgramTextToolbarImpl(editor, codeInput)
        val codeScrollBar = editor.findViewById<ProgramCodeScrollBar>(R.id.programCodeScrollBar)
        codeScrollBar.attachTo(codeInput)
        val lineNumbers = editor.findViewById<ProgramLineNumberView>(R.id.programLineNumbers)
        lineNumbers.attachTo(codeInput)
        codeInput.isVerticalScrollBarEnabled = false
        val restoredDraft = pendingProgramDraftCode
        if (restoredDraft != null) {
            codeInput.setText(restoredDraft)
            codeInput.setSelection(
                ProgramEditorWindowPolicy.openCursorPosition(
                    pendingProgramDraftCursor,
                    restoredDraft.length
                )
            )
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
            val sequence = scriptController.loadSequence()
            if (settingsActionIndex < sequence.size) {
                val action = sequence[settingsActionIndex]
                codeInput.setText(action.code ?: "")
                codeInput.setSelection(
                    ProgramEditorWindowPolicy.openCursorPosition(
                        restoreCursor = null,
                        codeLength = codeInput.text?.length ?: 0
                    )
                )
                codeInput.post { codeInput.scrollTo(0, 0) }
            }
        }

        editor.findViewById<View>(R.id.templateButton).setOnClickListener {
            showProgramTemplatePanelImpl(codeInput)
        }

        editor.findViewById<View>(R.id.programPickTapButton).setOnClickListener {
            startProgramPointAssistImpl(codeInput) { x, y ->
                val screen = programScreenSizeImpl()
                ProgramLuaAssist.tapSnippet(x, y, screen.width, screen.height)
            }
        }

        editor.findViewById<View>(R.id.programPickCoordButton).setOnClickListener {
            startProgramPointAssistImpl(codeInput) { x, y ->
                val screen = programScreenSizeImpl()
                ProgramLuaAssist.coordinateSnippet(x, y, screen.width, screen.height)
            }
        }

        editor.findViewById<View>(R.id.programPickSwipeButton).setOnClickListener {
            startProgramSwipeAssistImpl(codeInput)
        }

        editor.findViewById<View>(R.id.programPickAreaButton).setOnClickListener {
            saveProgramDraftForAssistImpl(codeInput)
            hidePickerOverlayImpl()
            showAreaPickerImpl(
                onAreaSelected = { area ->
                    val screen = programScreenSizeImpl()
                    restoreProgramEditorWithSnippetImpl(
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
                    restoreProgramEditorWithSnippetImpl(null)
                }
            )
        }

        editor.findViewById<View>(R.id.programPickColorButton).setOnClickListener {
            saveProgramDraftForAssistImpl(codeInput)
            hidePickerOverlayImpl()
            showColorPickerOverlayImpl(
                onColorSelected = { x, y, hex ->
                    val screen = programScreenSizeImpl()
                    restoreProgramEditorWithSnippetImpl(
                        ProgramLuaAssist.colorSnippet(hex, x, y, screen.width, screen.height)
                    )
                },
                onCancelled = {
                    restoreProgramEditorWithSnippetImpl(null)
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
            hidePickerOverlayImpl()
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
                val sequence = scriptController.loadSequence().toMutableList()
                if (settingsActionIndex in sequence.indices) {
                    val oldAction = sequence[settingsActionIndex]
                    sequence[settingsActionIndex] = oldAction.copy(
                        code = code,
                        delayBeforeMs = delayBefore,
                        repeatCount = repeatCount
                    )
                    scriptController.saveSequence(sequence)
                    if (actionListVisible) renderActionListImpl()
                    refreshMarkersImpl()
                }
                settingsActionIndex = -1
            } else {
                appendActionToSequence(action)
            }

            hidePickerOverlayImpl()
            Toast.makeText(this, getString(R.string.program_action_saved), Toast.LENGTH_SHORT).show()
        }

        wm.addView(editor, params)
        focusProgramCodeInputImpl(codeInput)
    }

    internal fun updateProgramEditorSizeForCurrentDisplayImpl() {
        val editor = pickerView ?: return
        if (editor.id != R.id.programEditorRoot) return
        val params = programEditorParams ?: return

        val currentDisplay = ScreenCaptureDisplayReader.current(this)
        val editorSize = FloatingWindowSizePolicy.programEditorSizeForDisplay(
            displayWidthPx = currentDisplay.width,
            displayHeightPx = currentDisplay.height,
            resourceWidthPx = resources.displayMetrics.widthPixels,
            resourceHeightPx = resources.displayMetrics.heightPixels,
            density = resources.displayMetrics.density
        )

        FloatingWindowLayoutPolicy.applyCenterGravitySize(params, editorSize)
        applyProgramEditorCodePanelHeightImpl(editor, editorSize)
        FloatingWindowLayoutPolicy.updateIfAttached(windowManager, editor, params)
    }

    internal fun applyProgramEditorCodePanelHeightImpl(editor: View, editorSize: FloatingWindowSize) {
        val codePanel = editor.findViewById<View>(R.id.programCodePanel)
        codePanel.layoutParams = (codePanel.layoutParams as LinearLayout.LayoutParams).apply {
            height = Math.round(editorSize.heightPx * ProgramEditorWindowPolicy.CODE_PANEL_HEIGHT_RATIO)
            weight = 0f
        }
    }

    internal fun configureProgramCodeInputImpl(codeInput: EditText) {
        codeInput.isFocusable = true
        codeInput.isFocusableInTouchMode = true
        codeInput.isLongClickable = true
        codeInput.setTextIsSelectable(true)
        codeInput.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        codeInput.filters = codeInput.filters.filterNot { it is android.text.InputFilter.LengthFilter }.toTypedArray()
        codeInput.setSelectAllOnFocus(false)
    }

    internal fun focusProgramCodeInputImpl(codeInput: EditText) {
        codeInput.post {
            codeInput.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(codeInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    internal fun bindProgramTextToolbarImpl(editor: View, codeInput: EditText) {
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
            writeProgramClipboardImpl(selectedText)
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
            writeProgramClipboardImpl(selectedText)
            applyProgramTextResultImpl(codeInput, result)
            Toast.makeText(this, R.string.program_edit_cut_done, Toast.LENGTH_SHORT).show()
        }

        editor.findViewById<View>(R.id.programPasteButton).setOnClickListener {
            codeInput.requestFocus()
            val clipboardText = readProgramClipboardTextImpl()
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
            applyProgramTextResultImpl(codeInput, result)
            Toast.makeText(this, R.string.program_edit_pasted, Toast.LENGTH_SHORT).show()
        }
    }

    internal fun writeProgramClipboardImpl(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Lua code", text))
    }

    internal fun readProgramClipboardTextImpl(): String? {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()
    }

    internal fun applyProgramTextResultImpl(codeInput: EditText, result: ProgramEditorTextResult) {
        codeInput.setText(result.code)
        codeInput.setSelection(result.cursor.coerceIn(0, result.code.length))
    }

    @SuppressLint("InflateParams")
    internal fun showProgramTemplatePanelImpl(codeInput: EditText) {
        val wm = windowManager ?: return
        if (programTemplatePanelView != null) {
            hideProgramTemplatePanelImpl()
            return
        }

        val panel = LayoutInflater.from(this).inflate(R.layout.floating_program_template_panel, null)
        val params = programTemplatePanelParams ?: createProgramTemplatePanelParamsImpl()
        val templateSize = calculateProgramTemplatePanelSizeForCurrentDisplayImpl()
        applyProgramTemplatePanelParamsImpl(params, templateSize)
        programTemplatePanelView = panel
        programTemplatePanelParams = params

        bindPanelDrag(panel.findViewById(R.id.programTemplateHeader), panel, params)
        panel.findViewById<View>(R.id.programTemplateCloseButton).setOnClickListener {
            hideProgramTemplatePanelImpl()
        }

        val templates = ProgramLuaAssist.quickTemplates()
        val container = panel.findViewById<LinearLayout>(R.id.programTemplateContainer)
        container.removeAllViews()
        val rowWidth = programTemplateRowWidthImpl(templateSize)
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
                    insertProgramSnippetImpl(codeInput, template.snippet)
                    hideProgramTemplatePanelImpl()
                    codeInput.requestFocus()
                }
            }
            container.addView(item)
        }

        val scrollView = panel.findViewById<ScrollView>(R.id.programTemplateScroll)
        val scrollBar = panel.findViewById<ProgramTemplateMenuScrollBar>(R.id.programTemplateScrollBar)
        scrollView.layoutParams = scrollView.layoutParams.apply {
            height = programTemplateScrollHeightImpl(templateSize)
        }
        applyProgramTemplatePanelContentSizeImpl(panel, templateSize)
        scrollBar.attachTo(scrollView)

        floatingPanelController.show(
            panel = panel,
            params = params,
            zoneKey = "floating_program_template_panel",
            viewProvider = { programTemplatePanelView },
            paramsProvider = { programTemplatePanelParams }
        )
    }

    internal fun createProgramTemplatePanelParamsImpl(): WindowManager.LayoutParams {
        val templateSize = calculateProgramTemplatePanelSizeForCurrentDisplayImpl()
        return WindowManager.LayoutParams(
            templateSize.widthPx,
            templateSize.heightPx,
            overlayTypeImpl(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((resources.displayMetrics.widthPixels - templateSize.widthPx) / 2).coerceAtLeast(0)
            y = ((resources.displayMetrics.heightPixels - templateSize.heightPx) / 2).coerceAtLeast(0)
        }
    }

    internal fun updateProgramTemplatePanelSizeForCurrentDisplayImpl() {
        val panel = programTemplatePanelView ?: return
        val params = programTemplatePanelParams ?: return
        val templateSize = calculateProgramTemplatePanelSizeForCurrentDisplayImpl()

        applyProgramTemplatePanelParamsImpl(params, templateSize)

        applyProgramTemplatePanelContentSizeImpl(panel, templateSize)
        FloatingWindowLayoutPolicy.updateIfAttached(windowManager, panel, params)
    }

    internal fun calculateProgramTemplatePanelSizeForCurrentDisplayImpl(): FloatingWindowSize {
        val currentDisplay = ScreenCaptureDisplayReader.current(this)
        return FloatingWindowSizePolicy.programTemplateSize(
            screenWidthPx = currentDisplay.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels,
            screenHeightPx = currentDisplay.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels,
            density = resources.displayMetrics.density
        )
    }

    internal fun applyProgramTemplatePanelParamsImpl(
        params: WindowManager.LayoutParams,
        templateSize: FloatingWindowSize
    ) {
        FloatingWindowLayoutPolicy.applyCenteredSize(
            params = params,
            screenWidthPx = resources.displayMetrics.widthPixels,
            screenHeightPx = resources.displayMetrics.heightPixels,
            size = templateSize
        )
    }

    internal fun applyProgramTemplatePanelContentSizeImpl(panel: View, templateSize: FloatingWindowSize) {
        panel.layoutParams = ViewGroup.LayoutParams(templateSize.widthPx, templateSize.heightPx)
        panel.findViewById<View>(R.id.programTemplateHeader).layoutParams =
            panel.findViewById<View>(R.id.programTemplateHeader).layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
        val rowWidth = programTemplateRowWidthImpl(templateSize)
        val container = panel.findViewById<LinearLayout>(R.id.programTemplateContainer)
        for (index in 0 until container.childCount) {
            container.getChildAt(index).layoutParams = LinearLayout.LayoutParams(
                rowWidth,
                dp(PROGRAM_TEMPLATE_ROW_HEIGHT_DP)
            ).apply {
                if (index > 0) topMargin = dp(8f)
            }
        }
        val scrollView = panel.findViewById<ScrollView>(R.id.programTemplateScroll)
        scrollView.layoutParams = scrollView.layoutParams.apply {
            width = rowWidth
            height = programTemplateScrollHeightImpl(templateSize)
        }
    }

    internal fun programTemplateRowWidthImpl(templateSize: FloatingWindowSize): Int {
        return (templateSize.widthPx - dp(34f)).coerceAtLeast(dp(120f))
    }

    internal fun programTemplateScrollHeightImpl(templateSize: FloatingWindowSize): Int {
        return (templateSize.heightPx - dp(66f)).coerceAtLeast(dp(PROGRAM_TEMPLATE_ROW_HEIGHT_DP))
    }

    internal fun hideProgramTemplatePanelImpl() {
        removePanel(programTemplatePanelView, "floating_program_template_panel") {
            programTemplatePanelView = null
        }
    }

    internal fun insertProgramSnippetImpl(codeInput: EditText, snippet: String) {
        val result = ProgramLuaAssist.insertSnippet(
            code = codeInput.text.toString(),
            cursor = codeInput.selectionStart.coerceAtLeast(0),
            snippet = snippet
        )
        codeInput.setText(result.code)
        codeInput.setSelection(result.cursor.coerceIn(0, result.code.length))
    }

    internal fun programScreenSizeImpl(): ProgramScreenSize {
        ScreenCaptureManager.refreshDisplayMetrics(this)
        val display = ScreenCaptureDisplayReader.current(this)
        val fallbackWidth = screenWidthPx.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        val fallbackHeight = screenHeightPx.takeIf { it > 0 }
            ?: resources.displayMetrics.heightPixels
        return ProgramScreenSizePolicy.choose(
            captureWidth = ScreenCaptureManager.getCaptureWidth(),
            captureHeight = ScreenCaptureManager.getCaptureHeight(),
            displayWidth = display.width,
            displayHeight = display.height,
            fallbackWidth = fallbackWidth,
            fallbackHeight = fallbackHeight
        )
    }

    internal fun pixelXToStoredPercentImpl(x: Int): Int {
        return ProgramCoordinateAdapter.pointToStoredPercent(x, programScreenSizeImpl().width)
    }

    internal fun pixelYToStoredPercentImpl(y: Int): Int {
        return ProgramCoordinateAdapter.pointToStoredPercent(y, programScreenSizeImpl().height)
    }

    internal fun storedPercentXToPointPxImpl(value: Int): Int {
        return ProgramCoordinateAdapter.storedPercentToPointPx(value, programScreenSizeImpl().width)
    }

    internal fun storedPercentYToPointPxImpl(value: Int): Int {
        return ProgramCoordinateAdapter.storedPercentToPointPx(value, programScreenSizeImpl().height)
    }

    internal fun storedPercentXToEdgePxImpl(value: Int): Int {
        return ProgramCoordinateAdapter.storedPercentToEdgePx(value, programScreenSizeImpl().width)
    }

    internal fun storedPercentYToEdgePxImpl(value: Int): Int {
        return ProgramCoordinateAdapter.storedPercentToEdgePx(value, programScreenSizeImpl().height)
    }

    internal fun formatStoredPercentImpl(value: Int): String {
        return ProgramCoordinateAdapter.formatStoredPercent(value)
    }

    internal fun startProgramPointAssistImpl(
        codeInput: EditText,
        snippetBuilder: (Int, Int) -> String
    ) {
        saveProgramDraftForAssistImpl(codeInput)
        hidePickerOverlayImpl()
        showPickerOverlayImpl(PICKER_TAP_POINT) { x, y ->
            restoreProgramEditorWithSnippetImpl(snippetBuilder(x, y))
        }
    }

    internal fun startProgramSwipeAssistImpl(codeInput: EditText) {
        saveProgramDraftForAssistImpl(codeInput)
        hidePickerOverlayImpl()
        showSwipePickerOverlayImpl { startX, startY, endX, endY ->
            val screen = programScreenSizeImpl()
            restoreProgramEditorWithSnippetImpl(
                ProgramLuaAssist.swipeSnippet(startX, startY, endX, endY, screen.width, screen.height)
            )
        }
    }

    internal fun saveProgramDraftForAssistImpl(codeInput: EditText) {
        pendingProgramDraftCode = codeInput.text.toString()
        pendingProgramDraftCursor = codeInput.selectionStart.coerceAtLeast(0)
    }

    internal fun restoreProgramEditorWithSnippetImpl(snippet: String?) {
        if (snippet != null) {
            val result = ProgramLuaAssist.insertSnippet(
                code = pendingProgramDraftCode.orEmpty(),
                cursor = pendingProgramDraftCursor,
                snippet = snippet
            )
            pendingProgramDraftCode = result.code
            pendingProgramDraftCursor = result.cursor
        }
        showProgramEditorImpl()
    }

    private fun appendActionToSequence(action: ActionStep) {
        scriptController.appendToSequence(action)
        if (actionListVisible) {
            renderActionListImpl()
        }
        refreshMarkersImpl()
    }

    internal fun showActionSettingsImpl(actionIndex: Int) {
        val wm = windowManager ?: return
        if (pickerView != null) hidePickerOverlayImpl()

        val sequence = scriptController.loadSequence()
        if (actionIndex < 0 || actionIndex >= sequence.size) return
        val action = sequence[actionIndex]
        settingsActionIndex = actionIndex

        if (action.type == ActionStep.TYPE_PROGRAM) {
            showProgramEditorImpl()
            return
        }

        hideLoopSettingsPanel()

        val inflater = LayoutInflater.from(this)
        val picker = inflater.inflate(R.layout.timing_picker, null)
        pickerView = picker

        val settingsWindowWidth = dp(ACTION_SETTINGS_PANEL_WIDTH_DP)
            .coerceAtMost(resources.displayMetrics.widthPixels - dp(32f))

        val params = WindowManager.LayoutParams(
            settingsWindowWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
            hidePickerOverlayImpl()
            settingsActionIndex = -1
        }

        val durationInput = picker.findViewById<EditText>(R.id.durationInput)
        val delayBeforeInput = picker.findViewById<EditText>(R.id.delayBeforeInput)
        val repeatCountInput = picker.findViewById<EditText>(R.id.repeatCountInput)

        durationInput.setText((action.durationMs ?: 1L).toString())
        delayBeforeInput.setText((action.delayBeforeMs ?: 1L).toString())
        repeatCountInput.setText((action.repeatCount ?: 1).toString())

        fun saveCurrentSettingsInputs(): Boolean {
            val durationMs = parseMsInputImpl(durationInput.text.toString())
            val delayBeforeMs = parseMsInputImpl(delayBeforeInput.text.toString())
            val repeatCount = parseRepeatCountImpl(repeatCountInput.text.toString())

            if (repeatCount < 0) {
                Toast.makeText(this, getString(R.string.loop_count_negative), Toast.LENGTH_SHORT).show()
                return false
            }

            val updatedSequence = scriptController.loadSequence().toMutableList()
            if (settingsActionIndex in updatedSequence.indices) {
                val oldAction = updatedSequence[settingsActionIndex]
                updatedSequence[settingsActionIndex] = oldAction.copy(
                    durationMs = durationMs,
                    delayBeforeMs = delayBeforeMs,
                    repeatCount = repeatCount
                )
                scriptController.saveSequence(updatedSequence)
                if (actionListVisible) renderActionListImpl()
                refreshMarkersImpl()
            }
            return true
        }

        picker.findViewById<View>(R.id.settingsSaveButton).setOnClickListener {
            if (!saveCurrentSettingsInputs()) {
                return@setOnClickListener
            }
            hidePickerOverlayImpl()
            settingsActionIndex = -1
        }

        picker.findViewById<View>(R.id.settingsConditionButton).setOnClickListener {
            if (!saveCurrentSettingsInputs()) {
                return@setOnClickListener
            }
            loadConditionEditStateImpl()
            hidePickerOverlayImpl()
            showConditionPickerImpl()
        }

        wm.addView(picker, params)
    }

    internal fun loadConditionEditStateImpl() {
        val sequence = scriptController.loadSequence()
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

    internal fun showConditionPickerImpl() {
        val wm = windowManager ?: return
        if (pickerView != null) hidePickerOverlayImpl()

        val inflater = LayoutInflater.from(this)
        val picker = inflater.inflate(R.layout.condition_picker, null)
        pickerView = picker

        val conditionWindowWidth = (560 * resources.displayMetrics.density).toInt()
            .coerceAtMost(resources.displayMetrics.widthPixels - (32 * resources.displayMetrics.density).toInt())

        val params = WindowManager.LayoutParams(
            conditionWindowWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
            hidePickerOverlayImpl()
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
        val colorToleranceRow = picker.findViewById<View>(R.id.conditionColorToleranceRow)
        val colorToleranceInput = picker.findViewById<EditText>(R.id.conditionColorToleranceInput)
        val saveBtn = picker.findViewById<View>(R.id.condSaveBtn)

        fun condTypeToLabel(type: String?): String = when (type) {
            ActionStep.CONDITION_TEXT_CONTAINS -> getString(R.string.condition_type_text_contains)
            ActionStep.CONDITION_COLOR_MATCH -> getString(R.string.condition_type_color_match)
            else -> getString(R.string.condition_type_none)
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
                    formatStoredPercentImpl(x),
                    formatStoredPercentImpl(y)
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
                val prefix = getString(R.string.condition_content_label)  // "鏉′欢鍐呭锛?
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
                if (conditionOcrPrefillRunning) {
                    textInput.setText("")
                }
            }
            selectAreaBtn.visibility = if (isText) View.VISIBLE else View.GONE
            // Area range display (text type only, after area is set)
            if (isText && condEditUseArea && condEditLeft != null && condEditRight != null
                && condEditTop != null && condEditBottom != null) {
                areaRangeText.visibility = View.VISIBLE
                areaRangeText.text = getString(
                    R.string.condition_area_range,
                    formatStoredPercentImpl(condEditLeft!!),
                    formatStoredPercentImpl(condEditTop!!),
                    formatStoredPercentImpl(condEditRight!!),
                    formatStoredPercentImpl(condEditBottom!!)
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
                        condEditUseArea = false
                        condEditLeft = null
                        condEditTop = null
                        condEditRight = null
                        condEditBottom = null
                    }
                    ActionStep.CONDITION_TEXT_CONTAINS -> {
                        // Switching to text: clear color-related state
                        condEditColorHex = null
                        condEditColorTolerance = null
                        condEditColorX = null
                        condEditColorY = null
                    }
                    null -> {
                        // Switching to unconditional: clear everything
                        condEditInvert = false
                        condEditText = null
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

        textInput.setText(condEditText ?: "")
        colorToleranceInput.setText((condEditColorTolerance ?: 10).toString())
        updateCondTypeDropdown()
        updateConditionFormUI()

        condTypeDropdown.setOnClickListener {
            showCondTypePopup()
        }

        invertBtn.setOnClickListener {
            condEditInvert = !condEditInvert
            updateInvertUI()
        }

        selectAreaBtn.setOnClickListener {
            Log.i(
                tag,
                "condition select area clicked: type=${condEditType ?: "none"} " +
                    "screen=${programScreenSizeImpl().width}x${programScreenSizeImpl().height} " +
                    "captureReady=${ScreenCaptureManager.isReady}"
            )
            if (
                OcrScreenCaptureReadinessPolicy.shouldRequestPermissionBeforeTextAreaSelection(
                    conditionType = condEditType,
                    captureReady = ScreenCaptureManager.isReady
                )
            ) {
                requestScreenCapturePermissionForOcr()
                return@setOnClickListener
            }
            hidePickerOverlayImpl()
            showAreaPickerImpl()
        }

        colorPickBtn.setOnClickListener {
            if (!ScreenCaptureManager.isReady) {
                Toast.makeText(this, getString(R.string.screen_capture_not_ready), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            wm.removeView(picker)
            ScreenshotHider.unregister("condition_picker")
            pickerView = null
            showColorPickerOverlayImpl()
        }

        saveBtn.setOnClickListener {
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

            val sequence = scriptController.loadSequence().toMutableList()
            if (settingsActionIndex in sequence.indices) {
                val oldAction = sequence[settingsActionIndex]
                sequence[settingsActionIndex] = oldAction.copy(
                    conditionType = savedType,
                    conditionText = if (savedType != null && !isSavedColor) condEditText else null,
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
                scriptController.saveSequence(sequence)
                if (actionListVisible) renderActionListImpl()
                refreshMarkersImpl()
            }
            val actionIndex = settingsActionIndex
            hidePickerOverlayImpl()
            if (actionIndex >= 0) {
                showActionSettingsImpl(actionIndex)
            } else {
                settingsActionIndex = -1
            }
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

    internal fun showAreaPickerImpl(
        onAreaSelected: ((Rect) -> Unit)? = null,
        onCancelled: (() -> Unit)? = null
    ) {
        val wm = windowManager ?: return

        Log.i(
            tag,
            "showAreaPicker start: callback=${onAreaSelected != null} " +
                "type=${condEditType ?: "none"} screen=${programScreenSizeImpl().width}x${programScreenSizeImpl().height} " +
                "captureReady=${ScreenCaptureManager.isReady}"
        )

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
                condEditLeft?.let { storedPercentXToEdgePxImpl(it) },
                condEditTop?.let { storedPercentYToEdgePxImpl(it) },
                condEditRight?.let { storedPercentXToEdgePxImpl(it) },
                condEditBottom?.let { storedPercentYToEdgePxImpl(it) }
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
            Log.i(
                tag,
                "areaPicker save clicked: hasRect=${rect != null} " +
                    "type=${condEditType ?: "none"} captureReady=${ScreenCaptureManager.isReady}"
            )
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
                condEditLeft = pixelXToStoredPercentImpl(screenLeft)
                condEditTop = pixelYToStoredPercentImpl(screenTop)
                condEditRight = pixelXToStoredPercentImpl(screenRight)
                condEditBottom = pixelYToStoredPercentImpl(screenBottom)
                condEditUseArea = true
                areaRect = Rect(screenLeft, screenTop, screenRight, screenBottom)
                shouldPrefillText = condEditType == ActionStep.CONDITION_TEXT_CONTAINS
                Log.i(
                    tag,
                    "areaPicker selected: rect=${areaRect.toShortString()} " +
                        "stored=[${condEditLeft},${condEditTop}][${condEditRight},${condEditBottom}] " +
                        "prefill=$shouldPrefillText"
                )
            }
            if (shouldPrefillText && areaRect != null && onAreaSelected == null) {
                removeAreaPickerViewImpl(view, selectionView)
                revealAreaPickerOverlays()
                prefillTextConditionFromAreaImpl(areaRect)
                return@setOnClickListener
            }

            removeAreaPickerViewImpl(view, selectionView)

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
                prefillTextConditionFromAreaImpl(areaRect)
            } else {
                Log.i(
                    tag,
                    "areaPicker finished without OCR prefill: shouldPrefill=$shouldPrefillText " +
                        "hasArea=${areaRect != null}"
                )
                revealAreaPickerOverlays()
                showConditionPickerImpl()
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
            showConditionPickerImpl()
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
                showConditionPickerImpl()
            }
        }
    }

    internal fun removeAreaPickerViewImpl(
        view: View,
        selectionView: AreaSelectionView
    ) {
        selectionView.cleanup()
        try {
            windowManager?.removeView(view)
        } catch (e: Exception) {
            Log.w(tag, "Failed to remove area picker", e)
        }
        if (pickerView === view) {
            pickerView = null
        }
    }

    internal fun prefillTextConditionFromAreaImpl(area: Rect) {
        Log.i(
            tag,
            "prefillTextConditionFromArea start: area=${area.toShortString()} " +
                "captureReady=${ScreenCaptureManager.isReady} " +
                "screen=${programScreenSizeImpl().width}x${programScreenSizeImpl().height}"
        )
        if (!ScreenCaptureManager.isReady) {
            Log.i(tag, "prefillTextConditionFromArea skipped: screen capture is not ready")
            ScreenshotHider.revealAll()
            showConditionPickerImpl()
            requestScreenCapturePermissionForOcr()
            return
        }

        conditionOcrPrefillGeneration++
        val generation = conditionOcrPrefillGeneration
        conditionOcrPrefillRunning = true
        condEditText = null
        showConditionPickerImpl()

        Thread {
            val handler = android.os.Handler(mainLooper)
            val startedAt = SystemClock.uptimeMillis()
            var initialDelayMs = 0L
            var totalCaptureMs = 0L
            var totalRecognizeMs = 0L
            val capturedBitmaps = mutableListOf<Bitmap>()
            val captureScores = mutableListOf<Double>()
            val captureSources = mutableListOf<String>()
            var failureMessageRes: Int? = null
            fun addOcrCandidate(
                source: String,
                bitmap: Bitmap
            ) {
                val score = OcrBitmapQuality.edgeScore(bitmap)
                if (OcrBitmapQuality.isProbablyBlank(score)) {
                    Log.d(
                        tag,
                        "OCR prefill skipped blank candidate source=$source " +
                            "bitmap=${bitmap.width}x${bitmap.height} " +
                            "sharpness=${String.format("%.2f", score)}"
                    )
                    bitmap.recycle()
                    return
                }
                if (OcrDebugConfig.VERBOSE_LOGS) {
                    Log.d(
                        tag,
                        "OCR prefill candidate source=$source bitmap=${bitmap.width}x${bitmap.height} " +
                            "sharpness=${String.format("%.2f", score)}"
                    )
                }
                capturedBitmaps += bitmap
                captureScores += score
                captureSources += source
            }

            fun recycleCapturedBitmaps() {
                capturedBitmaps.forEach { bitmap ->
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
                capturedBitmaps.clear()
                captureScores.clear()
                captureSources.clear()
            }

            val ocrText = try {
                Thread.sleep(OcrPrefillCapturePolicy.INITIAL_CAPTURE_DELAY_MS)
                initialDelayMs = SystemClock.uptimeMillis() - startedAt
                ScreenCaptureManager.refreshDisplayMetrics(this)
                Log.i(
                    tag,
                    "OCR prefill after refresh: captureReady=${ScreenCaptureManager.isReady} " +
                        "capture=${ScreenCaptureManager.getCaptureWidth()}x" +
                        ScreenCaptureManager.getCaptureHeight()
                )
                var text = ""
                captureLoop@ for (attemptIndex in 0 until OcrPrefillCapturePolicy.CAPTURE_ATTEMPT_COUNT) {
                    if (attemptIndex > 0) {
                        Thread.sleep(OcrPrefillCapturePolicy.BETWEEN_ATTEMPTS_DELAY_MS)
                    }

                    if (!ScreenCaptureManager.isReady) {
                        failureMessageRes = R.string.screen_capture_not_ready
                        break
                    }

                    val beforeCapture = SystemClock.uptimeMillis()
                    val bitmap = OcrHelper.captureAreaBitmap(
                        area = area,
                        screenWidth = ScreenCaptureManager.getCaptureWidth(),
                        screenHeight = ScreenCaptureManager.getCaptureHeight(),
                        timeoutMs = OcrTimingPolicy.PREFILL_CAPTURE_TIMEOUT_MS
                    )
                    val captureMs = SystemClock.uptimeMillis() - beforeCapture
                    totalCaptureMs += captureMs
                    if (bitmap == null) {
                        Log.d(
                            tag,
                            "OCR prefill capture attempt=${attemptIndex + 1}/" +
                                "${OcrPrefillCapturePolicy.CAPTURE_ATTEMPT_COUNT} returned null " +
                                "capture=${captureMs}ms area=${area.width()}x${area.height()}"
                        )
                    } else {
                        if (OcrDebugConfig.VERBOSE_LOGS) {
                            Log.d(
                                tag,
                                "OCR prefill capture attempt=${attemptIndex + 1}/" +
                                    "${OcrPrefillCapturePolicy.CAPTURE_ATTEMPT_COUNT} " +
                                    "bitmap=${bitmap.width}x${bitmap.height} capture=${captureMs}ms"
                            )
                        }
                        val candidateCountBefore = capturedBitmaps.size
                        addOcrCandidate("media-${attemptIndex + 1}", bitmap)
                        if (capturedBitmaps.size > candidateCountBefore) {
                            val candidateBitmap = capturedBitmaps.last()
                            val beforeRecognize = SystemClock.uptimeMillis()
                            val candidateText = OcrHelper.recognizeTextFromBitmap(candidateBitmap)
                            totalRecognizeMs += SystemClock.uptimeMillis() - beforeRecognize
                            if (candidateText.isNotEmpty()) {
                                text = candidateText
                                break@captureLoop
                            }
                        }
                    }
                }
                if (capturedBitmaps.isEmpty()) {
                    Log.d(
                        tag,
                        "OCR prefill empty bitmap: initialDelay=${initialDelayMs}ms " +
                            "capture=${totalCaptureMs}ms area=${area.width()}x${area.height()}"
                    )
                    if (failureMessageRes == null) {
                        failureMessageRes = R.string.condition_ocr_capture_failed
                    }
                    ""
                } else {
                    val bestFailureIndex = captureScores.indices.maxByOrNull { index ->
                        captureScores[index]
                    } ?: 0
                    if (OcrDebugConfig.VERBOSE_LOGS) {
                        Log.d(
                            tag,
                            "OCR prefill timing: initialDelay=${initialDelayMs}ms " +
                                "capture=${totalCaptureMs}ms " +
                                "recognize=${totalRecognizeMs}ms " +
                                "captures=${capturedBitmaps.size} " +
                                "bestCaptureSource=${captureSources[bestFailureIndex]} " +
                                "textLength=${text.length}"
                        )
                    }
                    if (
                        OcrDebugImagePolicy.shouldSavePrefillFailureCrop(
                            recognizedText = text,
                            captureFailure = false
                        )
                    ) {
                        val debugCrop = OcrDebugImageSaver.savePrefillFailureCrop(
                            context = this,
                            bitmap = capturedBitmaps[bestFailureIndex]
                        )
                        Log.i(
                            tag,
                            "OCR prefill saved failure crop: ${debugCrop?.absolutePath ?: "failed"}"
                        )
                    }
                    text
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                handler.post { ScreenshotHider.revealAll() }
                failureMessageRes = R.string.condition_ocr_capture_failed
                ""
            } catch (e: Exception) {
                Log.w(tag, "Failed to prefill text condition from OCR area", e)
                handler.post { ScreenshotHider.revealAll() }
                failureMessageRes = R.string.condition_ocr_recognize_failed
                ""
            } finally {
                recycleCapturedBitmaps()
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
                if (recognizedText.isEmpty()) {
                    val messageRes = failureMessageRes ?: R.string.condition_ocr_prefill_empty
                    Toast.makeText(this, getString(messageRes), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    internal fun showColorPickerOverlayImpl(
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
                    showConditionPickerImpl()
                    return@post
                }

                val overlay = ColorPickerOverlayView(this)

                val initX = screenW / 2
                val initY = screenH / 2
                val initHex = samplePixelHexImpl(image, initX, initY, screenW, screenH)
                overlay.setInitialPosition(initX, initY, initHex)

                overlay.colorSampler = { x, y ->
                    samplePixelHexImpl(image, x, y, screenW, screenH)
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
                        condEditColorX = pixelXToStoredPercentImpl(x)
                        condEditColorY = pixelYToStoredPercentImpl(y)
                        showConditionPickerImpl()
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
                        showConditionPickerImpl()
                    }
                }

                pickerView = overlay

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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

    internal fun samplePixelHexImpl(
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

    internal fun hidePositionMarkersForScreenshotImpl() {
        val wm = windowManager ?: return
        actionMarkersOverlayView?.let { view ->
            view.cleanup()
            try {
                wm.removeView(view)
            } catch (_: Exception) {}
        }
        actionMarkersOverlayView = null
    }

    internal fun restoreFloatingUIImpl() {
        floatingView?.visibility = View.VISIBLE
        if (positionVisible) {
            showPositionMarkersImpl()
        }
    }

    internal fun parseMsInputImpl(input: String): Long {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return 1L
        return try {
            trimmed.toLong().coerceAtLeast(1L)
        } catch (_: NumberFormatException) {
            1L
        }
    }

    internal fun parseRepeatCountImpl(input: String): Int {
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
        floatingView?.let { hideSaveConfirmPanel(it) }

        val panel = LayoutInflater.from(this).inflate(R.layout.floating_loop_panel, null)
        val params = loopPanelParams ?: createLoopSettingsPanelParams()
        applyLoopSettingsWindowPolicy(params)
        loopPanelView = panel
        loopPanelParams = params
        bindPanelDrag(panel.findViewById(R.id.loopPanelDragHandle), panel, params)
        bindLoopSettingsInputs(panel)
        panel.findViewById<View>(R.id.loopSettingsSave).setOnClickListener {
            saveLoopSettings()
        }
        floatingPanelController.show(
            panel = panel,
            params = params,
            zoneKey = "floating_loop_panel",
            viewProvider = { loopPanelView },
            paramsProvider = { loopPanelParams }
        )
        loadLoopSettings()
        enableOverlayFocusImpl()
    }

    private fun createLoopSettingsPanelParams(): WindowManager.LayoutParams {
        val params = createPanelParams(220, 88)
        applyLoopSettingsWindowPolicy(params)
        return params
    }

    private fun applyLoopSettingsWindowPolicy(params: WindowManager.LayoutParams) {
        params.flags = LoopSettingsWindowPolicy.FLAGS
        params.softInputMode = LoopSettingsWindowPolicy.SOFT_INPUT_MODE
    }

    private fun bindLoopSettingsInputs(panel: View) {
        val countInput = panel.findViewById<EditText>(R.id.loopCountInput)
        val gapInput = panel.findViewById<EditText>(R.id.loopGapInput)
        listOf(countInput, gapInput).forEach { input ->
            input.isFocusable = true
            input.isFocusableInTouchMode = true
            input.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) showKeyboardForLoopInput(view)
            }
            input.setOnClickListener { view ->
                view.requestFocus()
                showKeyboardForLoopInput(view)
            }
        }
    }

    private fun showKeyboardForLoopInput(view: View) {
        view.post {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideLoopSettingsPanel() {
        removePanel(loopPanelView, "floating_loop_panel") {
            loopPanelView = null
        }
        disableOverlayFocusImpl()
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
        disableOverlayFocusImpl()
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
        private const val COLLAPSED_CONTROL_ITEM_COUNT = 4
        private const val COLLAPSED_CONTROL_GAP_COUNT = 3
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

