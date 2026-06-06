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
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

class PvzFloatingControlService : Service() {
    private val stopDebugTag = "ClickerStopDebug"

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var floatingParams: WindowManager.LayoutParams? = null
    private var editorView: View? = null
    private var editorParams: WindowManager.LayoutParams? = null
    private var programTemplatePanelView: View? = null
    private var programTemplatePanelParams: WindowManager.LayoutParams? = null
    private var calibrationPanelView: View? = null
    private var calibrationPanelParams: WindowManager.LayoutParams? = null
    private var calibrationPickerView: View? = null
    private var areaPickerBackgroundBitmap: Bitmap? = null
    private var executionStopButton: ExecutionStopButtonOverlay? = null
    private var floatingTouchThrough = false
    private var stopButtonPositioning = false
    private var pendingProgramDraftCode: String? = null
    private var pendingProgramDraftCursor: Int = 0
    private var pendingProgramRestoreCursor: Int? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_EXECUTION) {
            Log.d(stopDebugTag, "PvzFloatingControlService notification stop clicked")
            ActionSequenceExecutor.stop()
            return START_NOT_STICKY
        }
        val openEditorAfterStart = intent?.action == ACTION_OPEN_EDITOR

        startForeground(NOTIFICATION_ID, createNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView?.let { view ->
            try { windowManager?.removeView(view) } catch (_: Exception) {}
        }
        floatingView = null
        floatingParams = null
        hideEditor()
        hideCalibrationPanel()
        hideCalibrationPickerOverlay(revealOverlays = true)
        hideExecutionStopButton()
        showFloatingControl()
        if (openEditorAfterStart) {
            showProgramEditor()
        }
        isRunning = true

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(stopDebugTag, "PvzFloatingControlService.onDestroy")
        if (ActionSequenceExecutor.isRunning) {
            ActionSequenceExecutor.stop()
        }
        hideEditor()
        hideCalibrationPanel()
        hideCalibrationPickerOverlay(revealOverlays = true)
        hideExecutionStopButton()
        hideFloatingControl()
        isRunning = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.pvz_floating_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
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

    @SuppressLint("InflateParams")
    private fun showFloatingControl() {
        val wm = windowManager ?: return
        val view = LayoutInflater.from(this).inflate(R.layout.pvz_floating_control, null)
        floatingView = view

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
            alpha = ExecutionOverlayWindowPolicy.NORMAL_ALPHA
        }

        bindDrag(view.findViewById(R.id.pvzDragHandle), view)
        bindDrag(view.findViewById(R.id.pvzCollapsedTitle), view)

        bindStartStopTouch(view.findViewById(R.id.pvzStartButton)) {
            executeCurrentProgram()
        }
        bindStartStopTouch(view.findViewById(R.id.pvzCollapsedStartButton)) {
            executeCurrentProgram()
        }

        view.findViewById<View>(R.id.pvzEditButton).setOnClickListener {
            showProgramEditor()
        }
        view.findViewById<View>(R.id.pvzCalibrationButton).setOnClickListener {
            toggleCalibrationPanel()
        }
        view.findViewById<View>(R.id.pvzSaveButton).setOnClickListener {
            showSaveConfirmPanel()
        }
        view.findViewById<View>(R.id.pvzSaveConfirmYes).setOnClickListener {
            saveCurrentProgramWithConfirmedName()
        }
        view.findViewById<View>(R.id.pvzSaveConfirmNo).setOnClickListener {
            view.findViewById<View>(R.id.pvzSaveConfirmPanel).visibility = View.GONE
            disableOverlayFocus()
        }
        view.findViewById<View>(R.id.pvzStopPositionButton).setOnClickListener {
            toggleStopButtonPositioning()
        }
        view.findViewById<View>(R.id.pvzFoldButton).setOnClickListener {
            collapseFloatingControl()
        }
        view.findViewById<View>(R.id.pvzCollapsedExpandButton).setOnClickListener {
            expandFloatingControl()
        }
        bindClose(view.findViewById(R.id.pvzCloseButton))
        bindClose(view.findViewById(R.id.pvzCollapsedCloseButton))

        updateFloatingTitle()
        updateStopPositionButton()
        wm.addView(view, floatingParams)

        ControlZoneChecker.register(ZONE_KEY) { getControlZoneRect() }
        ScreenshotHider.register(
            ZONE_KEY,
            hide = { floatingView?.visibility = View.INVISIBLE },
            reveal = { floatingView?.visibility = View.VISIBLE }
        )
    }

    private fun bindDrag(handle: View, view: View) {
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = floatingParams?.x ?: 0
                    initialY = floatingParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    floatingParams?.let { params ->
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(view, params)
                    }
                    true
                }
                else -> false
            }
        }
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

    private fun bindClose(button: View) {
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (ActionSequenceExecutor.isRunning) {
                        ActionSequenceExecutor.stop()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    stopSelf()
                    Toast.makeText(this, R.string.pvz_floating_stopped, Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }

    private fun collapseFloatingControl() {
        val view = floatingView ?: return
        view.findViewById<View>(R.id.pvzSaveConfirmPanel).visibility = View.GONE
        disableOverlayFocus()
        view.findViewById<View>(R.id.pvzExpandedControls).visibility = View.GONE
        view.findViewById<View>(R.id.pvzCollapsedControls).visibility = View.VISIBLE
    }

    private fun expandFloatingControl() {
        val view = floatingView ?: return
        view.findViewById<View>(R.id.pvzExpandedControls).visibility = View.VISIBLE
        view.findViewById<View>(R.id.pvzCollapsedControls).visibility = View.GONE
    }

    private fun hideFloatingControl() {
        ScreenshotHider.unregister(ZONE_KEY)
        ControlZoneChecker.unregister(ZONE_KEY)
        val view = floatingView
        if (view != null) {
            try { windowManager?.removeView(view) } catch (_: Exception) {}
        }
        floatingView = null
        floatingParams = null
    }

    private fun getControlZoneRect(): Rect? {
        if (floatingTouchThrough) return null
        val view = floatingView ?: return null
        val params = floatingParams ?: return null
        if (view.width <= 0 || view.height <= 0) return null
        return Rect(params.x, params.y, params.x + view.width, params.y + view.height)
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
        hideEditor()
        hideCalibrationPanel()
        if (executionStopButton == null) {
            executionStopButton = ExecutionStopButtonOverlay(
                context = this,
                windowManager = wm,
                zoneKey = STOP_BUTTON_ZONE_KEY,
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

    private fun updateStopPositionButton() {
        val view = floatingView ?: return
        val button = view.findViewById<TextView>(R.id.pvzStopPositionButton)
        button.text = if (stopButtonPositioning) {
            getString(R.string.stop_position_done)
        } else {
            getString(R.string.stop_position_action)
        }
    }

    private fun showExecutionStopButton() {
        val wm = windowManager ?: return
        if (executionStopButton == null) {
            executionStopButton = ExecutionStopButtonOverlay(
                context = this,
                windowManager = wm,
                zoneKey = STOP_BUTTON_ZONE_KEY,
                onStop = { ActionSequenceExecutor.stop() }
            )
        }
        executionStopButton?.show()
    }

    private fun hideExecutionStopButton() {
        executionStopButton?.hide()
    }

    private fun executeCurrentProgram() {
        hideStopButtonPositionEditor()
        hideEditor()
        hideCalibrationPanel()
        hideCalibrationPickerOverlay(revealOverlays = true)
        floatingView?.findViewById<View>(R.id.pvzSaveConfirmPanel)?.visibility = View.GONE
        disableOverlayFocus()
        val code = loadCurrentProgramCode()
        if (code.isBlank()) {
            Toast.makeText(this, R.string.pvz_program_empty, Toast.LENGTH_SHORT).show()
            return
        }

        ActionSequenceExecutor.loopCount = 1
        ActionSequenceExecutor.loopEnabled = false
        ActionSequenceExecutor.loopGapMs = 0L

        ActionSequenceExecutor.onStarted = {
            Log.d(stopDebugTag, "PvzFloatingControlService.onStarted fired")
            setFloatingTouchThrough(true)
            showExecutionStopButton()
            updateStartStopButtons(true)
        }
        ActionSequenceExecutor.onFinished = {
            Log.d(stopDebugTag, "PvzFloatingControlService.onFinished fired")
            hideExecutionStopButton()
            setFloatingTouchThrough(false)
            updateStartStopButtons(false)
        }
        ActionSequenceExecutor.onStopped = {
            Log.d(stopDebugTag, "PvzFloatingControlService.onStopped fired")
            hideExecutionStopButton()
            setFloatingTouchThrough(false)
            updateStartStopButtons(false)
        }

        val density = resources.displayMetrics.density
        val paddingPx = ControlZoneChecker.dpToPx(density)
        ActionSequenceExecutor.execute(
            this,
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
                Toast.makeText(this, R.string.action_overlaps_control_stopped, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun updateStartStopButtons(running: Boolean) {
        val view = floatingView ?: return
        val startButton = view.findViewById<TextView>(R.id.pvzStartButton)
        val collapsedStartButton = view.findViewById<TextView>(R.id.pvzCollapsedStartButton)
        if (running) {
            startButton.text = getString(R.string.stop_action)
            startButton.setTextColor(Color.WHITE)
            startButton.background = getDrawable(R.drawable.floating_pill_danger)
            collapsedStartButton.text = getString(R.string.stop_action)
            collapsedStartButton.setTextColor(Color.WHITE)
            collapsedStartButton.background = getDrawable(R.drawable.floating_pill_danger)
        } else {
            startButton.text = getString(R.string.start_action)
            startButton.setTextColor(Color.WHITE)
            startButton.background = getDrawable(R.drawable.floating_pill_primary)
            collapsedStartButton.text = getString(R.string.start_action)
            collapsedStartButton.setTextColor(Color.WHITE)
            collapsedStartButton.background = getDrawable(R.drawable.floating_pill_primary)
        }
    }

    @SuppressLint("InflateParams")
    private fun showProgramEditor() {
        val wm = windowManager ?: return
        hideEditor()
        hideCalibrationPanel()
        hideCalibrationPickerOverlay(revealOverlays = true)

        val editor = LayoutInflater.from(this).inflate(R.layout.program_editor, null)
        editorView = editor

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
        editorParams = params

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
        codeInput.setText(loadCurrentProgramCode())
        val restoreCursor = pendingProgramRestoreCursor
        pendingProgramRestoreCursor = null
        codeInput.setSelection(
            restoreCursor?.coerceIn(0, codeInput.text?.length ?: 0)
                ?: (codeInput.text?.length ?: 0)
        )
        editor.findViewById<ProgramCodeScrollBar>(R.id.programCodeScrollBar).attachTo(codeInput)
        editor.findViewById<ProgramLineNumberView>(R.id.programLineNumbers).attachTo(codeInput)
        codeInput.isVerticalScrollBarEnabled = false
        bindProgramTextToolbar(editor, codeInput)
        bindProgramEditorDrag(editor.findViewById(R.id.programEditorTitle), editor, params)

        editor.findViewById<View>(R.id.templateButton).setOnClickListener {
            showProgramTemplatePanel(codeInput)
        }
        bindPvzProgramInsertTools(editor, codeInput)
        editor.findViewById<View>(R.id.testParseButton).setOnClickListener {
            testProgramParse(codeInput.text.toString())
        }
        editor.findViewById<View>(R.id.programCloseButton).setOnClickListener {
            hideEditor()
        }
        editor.findViewById<View>(R.id.programSaveButton).setOnClickListener {
            val code = codeInput.text.toString()
            if (!validateProgramCode(code)) return@setOnClickListener
            saveCurrentProgramDraft(code)
            hideEditor()
            Toast.makeText(this, R.string.program_action_saved, Toast.LENGTH_SHORT).show()
        }

        wm.addView(editor, params)
        focusProgramCodeInput(codeInput)
        ScreenshotHider.register(
            EDITOR_ZONE_KEY,
            hide = { editorView?.visibility = View.INVISIBLE },
            reveal = { editorView?.visibility = View.VISIBLE }
        )
    }

    private fun bindPvzProgramInsertTools(editor: View, codeInput: EditText) {
        editor.findViewById<TextView>(R.id.programPickTapButton).text =
            getString(R.string.pvz_program_insert_tap)
        editor.findViewById<View>(R.id.programPickCoordButton).visibility = View.GONE
        editor.findViewById<TextView>(R.id.programPickSwipeButton).text =
            getString(R.string.pvz_program_insert_swipe)
        editor.findViewById<TextView>(R.id.programPickAreaButton).text =
            getString(R.string.pvz_program_insert_text)
        editor.findViewById<TextView>(R.id.programPickColorButton).text =
            getString(R.string.pvz_program_insert_color)

        editor.findViewById<View>(R.id.programPickTapButton).setOnClickListener {
            insertProgramSnippet(codeInput, "tap()")
        }
        editor.findViewById<View>(R.id.programPickSwipeButton).setOnClickListener {
            insertProgramSnippet(codeInput, "swipe(, , , )")
        }
        editor.findViewById<View>(R.id.programPickAreaButton).setOnClickListener {
            showPvzProgramTextAreaPicker(codeInput)
        }
        editor.findViewById<View>(R.id.programPickColorButton).setOnClickListener {
            showPvzProgramColorPicker(codeInput)
        }
    }

    private fun bindProgramEditorDrag(handle: View, editor: View, params: WindowManager.LayoutParams) {
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
                    windowManager?.updateViewLayout(editor, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun hideEditor() {
        hideProgramTemplatePanel()
        ScreenshotHider.unregister(EDITOR_ZONE_KEY)
        val view = editorView
        if (view != null) {
            try { windowManager?.removeView(view) } catch (_: Exception) {}
        }
        editorView = null
        editorParams = null
    }

    private fun saveProgramDraftForAssist(codeInput: EditText) {
        pendingProgramDraftCode = codeInput.text.toString()
        pendingProgramDraftCursor = codeInput.selectionStart.coerceAtLeast(0)
    }

    private fun restoreProgramEditorWithSnippet(snippet: String?) {
        val code = pendingProgramDraftCode.orEmpty()
        if (snippet != null) {
            val result = ProgramLuaAssist.insertSnippet(
                code = code,
                cursor = pendingProgramDraftCursor,
                snippet = snippet
            )
            saveCurrentProgramDraft(result.code)
            pendingProgramRestoreCursor = result.cursor
        } else {
            saveCurrentProgramDraft(code)
            pendingProgramRestoreCursor = pendingProgramDraftCursor
        }
        pendingProgramDraftCode = null
        showProgramEditor()
    }

    private fun showPvzProgramTextAreaPicker(codeInput: EditText) {
        val wm = windowManager ?: return
        saveProgramDraftForAssist(codeInput)
        hideEditor()
        hideCalibrationPanel()
        hideCalibrationPickerOverlay(revealOverlays = false)

        // Capture screenshot before hiding overlays (overlays are system windows,
        // so they won't appear in the capture)
        val capturedImage = if (ScreenCaptureManager.isReady) {
            ScreenCaptureManager.refreshDisplayMetrics(this)
            ScreenCaptureManager.captureFrameSync()
        } else {
            null
        }
        val backgroundBitmap: Bitmap? = capturedImage?.let { image ->
            try {
                ScreenCaptureManager.imageToBitmap(image)
            } finally {
                try { image.close() } catch (_: Exception) {}
            }
        }
        // Recycle previous bitmap if any
        areaPickerBackgroundBitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
        areaPickerBackgroundBitmap = backgroundBitmap

        ScreenshotHider.hideAll()
        val view = LayoutInflater.from(this).inflate(R.layout.area_picker, null)
        calibrationPickerView = view

        // Set screenshot as background if available
        if (backgroundBitmap != null) {
            view.findViewById<ImageView>(R.id.areaPickerBackground)?.setImageBitmap(backgroundBitmap)
        }

        val params = createFullScreenPickerParams()

        val selectionView = view.findViewById<AreaSelectionView>(R.id.areaSelectionView)
        val buttonsRow = view.findViewById<View>(R.id.areaPickerButtons)
        val saveBtn = view.findViewById<TextView>(R.id.areaPickerSaveBtn)
        val cancelBtn = view.findViewById<TextView>(R.id.areaPickerCancelBtn)
        view.findViewById<TextView>(R.id.areaPickerInstruction)
            .setText(R.string.pvz_program_text_picker_instruction)

        selectionView.onInteractionStarted = {
            buttonsRow.visibility = View.GONE
        }
        selectionView.onInteractionFinished = {
            buttonsRow.visibility = View.VISIBLE
        }

        saveBtn.setOnClickListener {
            val rect = selectionView.getSelectionRect()
            if (rect == null) {
                Toast.makeText(this, R.string.area_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val loc = IntArray(2)
            selectionView.getLocationOnScreen(loc)
            val screen = currentProgramScreenSize()
            val snippet = ProgramLuaAssist.textAreaSnippet(
                left = rect.left + loc[0],
                top = rect.top + loc[1],
                right = rect.right + loc[0],
                bottom = rect.bottom + loc[1],
                screenWidth = screen.width,
                screenHeight = screen.height
            )
            removePvzProgramAssistOverlay()
            restoreProgramEditorWithSnippet(snippet)
        }

        cancelBtn.setOnClickListener {
            removePvzProgramAssistOverlay()
            restoreProgramEditorWithSnippet(null)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            // Recycle the background bitmap on failure
            areaPickerBackgroundBitmap?.let {
                if (!it.isRecycled) it.recycle()
            }
            areaPickerBackgroundBitmap = null
            ScreenshotHider.revealAll()
            Log.w(stopDebugTag, "Failed to show PVZ2 program text area picker", e)
            restoreProgramEditorWithSnippet(null)
        }
    }

    private fun removePvzProgramAssistOverlay() {
        val view = calibrationPickerView ?: return
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
        }
        calibrationPickerView = null
        // Recycle the background bitmap to free memory
        areaPickerBackgroundBitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
        areaPickerBackgroundBitmap = null
        ScreenshotHider.revealAll()
    }

    private fun showPvzProgramColorPicker(codeInput: EditText) {
        val wm = windowManager ?: return
        if (!ScreenCaptureManager.isReady) {
            Toast.makeText(this, R.string.screen_capture_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        ScreenCaptureManager.refreshDisplayMetrics(this)
        if (!ScreenCaptureManager.isReady) {
            Toast.makeText(this, R.string.screen_capture_not_ready, Toast.LENGTH_SHORT).show()
            return
        }

        saveProgramDraftForAssist(codeInput)
        hideEditor()
        hideCalibrationPanel()
        hideCalibrationPickerOverlay(revealOverlays = false)
        ScreenshotHider.hideAll()

        val screenW = ScreenCaptureManager.getCaptureWidth()
        val screenH = ScreenCaptureManager.getCaptureHeight()
        Thread {
            Thread.sleep(300)
            val image = ScreenCaptureManager.captureFrameSync(COLOR_PICK_CAPTURE_TIMEOUT_MS)
            android.os.Handler(mainLooper).post {
                if (image == null) {
                    ScreenshotHider.revealAll()
                    Toast.makeText(this, R.string.condition_color_pick_failed, Toast.LENGTH_SHORT).show()
                    restoreProgramEditorWithSnippet(null)
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
                overlay.onConfirm = { _, _, hex ->
                    try {
                        wm.removeView(overlay)
                    } catch (_: Exception) {
                    }
                    calibrationPickerView = null
                    image.close()
                    ScreenshotHider.revealAll()
                    restoreProgramEditorWithSnippet("check_color(\"$hex\", 10, )")
                }
                overlay.onCancel = {
                    try {
                        wm.removeView(overlay)
                    } catch (_: Exception) {
                    }
                    calibrationPickerView = null
                    image.close()
                    ScreenshotHider.revealAll()
                    restoreProgramEditorWithSnippet(null)
                }
                calibrationPickerView = overlay
                val params = createFullScreenPickerParams()
                wm.addView(overlay, params)
            }
        }.start()
    }

    private fun samplePixelHex(
        image: android.media.Image,
        screenX: Int,
        screenY: Int,
        screenW: Int,
        screenH: Int
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

    private fun configureProgramCodeInput(codeInput: EditText) {
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
        clipboard.setPrimaryClip(ClipData.newPlainText("PVZ2 Lua code", text))
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

        val templates = pvzQuickTemplates()
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
                layoutParams = LinearLayout.LayoutParams(
                    rowWidth,
                    dp(PROGRAM_TEMPLATE_ROW_HEIGHT_DP)
                ).apply {
                    if (index > 0) topMargin = dp(8f)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    insertProgramSnippetInline(codeInput, template.snippet)
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
        ControlZoneChecker.register(TEMPLATE_ZONE_KEY) {
            panelZoneRect(programTemplatePanelView, programTemplatePanelParams)
        }
        ScreenshotHider.register(
            TEMPLATE_ZONE_KEY,
            hide = { programTemplatePanelView?.visibility = View.INVISIBLE },
            reveal = { programTemplatePanelView?.visibility = View.VISIBLE }
        )
    }

    private fun pvzQuickTemplates(): List<ProgramLuaTemplate> {
        return listOf(
            ProgramLuaTemplate(
                id = "pvz_plant_slots",
                title = getString(R.string.pvz_calibration_plant_slots),
                snippet = "plant_slots[].x, plant_slots[].y"
            ),
            ProgramLuaTemplate(
                id = "pvz_board",
                title = getString(R.string.pvz_calibration_board),
                snippet = "board[][].x, board[][].y"
            ),
            ProgramLuaTemplate(
                id = "pvz_sun",
                title = getString(R.string.pvz_calibration_sun),
                snippet = "sun_.x, sun_.y"
            ),
            ProgramLuaTemplate(
                id = "pvz_plant_food",
                title = getString(R.string.pvz_calibration_plant_food),
                snippet = "plant_food_.x, plant_food_.y"
            ),
            ProgramLuaTemplate(
                id = "pvz_artifact",
                title = getString(R.string.pvz_calibration_artifact),
                snippet = "artifact_.x, artifact_.y"
            ),
            ProgramLuaTemplate(
                id = "pvz_cucumber",
                title = getString(R.string.pvz_calibration_cucumber),
                snippet = "cucumber_.x, cucumber_.y"
            ),
            ProgramLuaTemplate(
                id = "pvz_recharge",
                title = getString(R.string.pvz_calibration_recharge),
                snippet = "recharge_.x, recharge_.y"
            ),
            ProgramLuaTemplate(
                id = "pvz_cards",
                title = getString(R.string.pvz_calibration_cards),
                snippet = "cards_edge.x, cards_edge.y"
            ),
            ProgramLuaTemplate(
                id = "pvz_other",
                title = getString(R.string.pvz_calibration_other),
                snippet = "other_.x, other_.y"
            ),
            ProgramLuaTemplate(
                id = "pvz_endless_supply",
                title = getString(R.string.pvz_calibration_endless_supply),
                snippet = "endless_supply_.x, endless_supply_.y"
            )
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
        removePanel(programTemplatePanelView, TEMPLATE_ZONE_KEY) {
            programTemplatePanelView = null
        }
    }

    @SuppressLint("InflateParams")
    private fun toggleCalibrationPanel() {
        if (calibrationPanelView != null) {
            hideCalibrationPanel()
        } else {
            showCalibrationPanel()
        }
    }

    @SuppressLint("InflateParams")
    private fun showCalibrationPanel() {
        val wm = windowManager ?: return
        if (calibrationPanelView != null) return

        val panel = LayoutInflater.from(this).inflate(R.layout.pvz_calibration_panel, null)
        val params = calibrationPanelParams ?: createCalibrationPanelParams()
        calibrationPanelView = panel
        calibrationPanelParams = params

        constrainCalibrationPanelScroll(panel, params)
        bindPanelDrag(panel.findViewById(R.id.pvzCalibrationHeader), panel, params)
        panel.findViewById<View>(R.id.pvzCalibrationCloseButton).setOnClickListener {
            hideCalibrationPanel()
        }
        bindCalibrationStatusButtons(panel)

        wm.addView(panel, params)
        ControlZoneChecker.register(CALIBRATION_ZONE_KEY) {
            panelZoneRect(calibrationPanelView, calibrationPanelParams)
        }
        ScreenshotHider.register(
            CALIBRATION_ZONE_KEY,
            hide = { calibrationPanelView?.visibility = View.INVISIBLE },
            reveal = { calibrationPanelView?.visibility = View.VISIBLE }
        )
    }

    private fun createCalibrationPanelParams(): WindowManager.LayoutParams {
        val panelWidth = dp(PROGRAM_TEMPLATE_PANEL_WIDTH_DP + 20f)
        val screen = currentProgramScreenSize()
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
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
            x = ((screen.width - panelWidth) / 2).coerceAtLeast(0)
            y = (screen.height * 0.16f).toInt().coerceAtLeast(0)
        }
    }

    private fun constrainCalibrationPanelScroll(panel: View, params: WindowManager.LayoutParams) {
        val scroll = panel.findViewById<View>(R.id.pvzCalibrationEntryScroll)
        val screen = currentProgramScreenSize()
        val maxHeight = (screen.height - params.y - dp(92f)).coerceAtLeast(dp(160f))
        val contentWidth = dp(PROGRAM_TEMPLATE_PANEL_WIDTH_DP + 20f)
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.AT_MOST)
        scroll.measure(widthSpec, unspecified)
        val targetHeight = scroll.measuredHeight.takeIf { it > 0 }
            ?.coerceAtMost(maxHeight)
            ?: maxHeight
        scroll.layoutParams = scroll.layoutParams.apply {
            height = targetHeight
        } as ViewGroup.LayoutParams
    }

    private fun hideCalibrationPanel() {
        removePanel(calibrationPanelView, CALIBRATION_ZONE_KEY) {
            calibrationPanelView = null
        }
    }

    private fun bindCalibrationStatusButtons(panel: View) {
        bindCalibrationStatusButton(
            panel,
            R.id.pvzCalibrationPlantSlotsStatus,
            PvzCalibrationStorage.PLANT_SLOTS
        )
        bindCalibrationStatusButton(
            panel,
            R.id.pvzCalibrationBoardStatus,
            PvzCalibrationStorage.BOARD
        )
        bindCalibrationStatusButton(
            panel,
            R.id.pvzCalibrationSunStatus,
            PvzCalibrationStorage.SUN
        )
        bindCalibrationStatusButton(
            panel,
            R.id.pvzCalibrationPlantFoodStatus,
            PvzCalibrationStorage.PLANT_FOOD
        )
        bindCalibrationStatusButton(
            panel,
            R.id.pvzCalibrationArtifactStatus,
            PvzCalibrationStorage.ARTIFACT
        )
        bindCalibrationStatusButton(
            panel,
            R.id.pvzCalibrationCucumberStatus,
            PvzCalibrationStorage.CUCUMBER
        )
        bindCalibrationStatusButton(
            panel,
            R.id.pvzCalibrationRechargeStatus,
            PvzCalibrationStorage.RECHARGE
        )
        bindCalibrationStatusButton(
            panel,
            R.id.pvzCalibrationCardsStatus,
            PvzCalibrationStorage.CARDS
        )
        bindCalibrationStatusButton(
            panel,
            R.id.pvzCalibrationOtherStatus,
            PvzCalibrationStorage.OTHER
        )
        bindCalibrationStatusButton(
            panel,
            R.id.pvzCalibrationEndlessSupplyStatus,
            PvzCalibrationStorage.ENDLESS_SUPPLY
        )
    }

    private fun bindCalibrationStatusButton(panel: View, viewId: Int, key: String) {
        val button = panel.findViewById<TextView>(viewId)
        updateCalibrationStatusButton(button, key)
        button.setOnClickListener {
            openCalibrationEditor(key)
        }
    }

    private fun updateCalibrationStatusButton(button: TextView, key: String) {
        val calibrated = PvzCalibrationStorage.isCalibrated(this, key)
        button.text = getString(
            if (calibrated) {
                R.string.pvz_calibration_calibrated
            } else {
                R.string.pvz_calibration_uncalibrated
            }
        )
        button.setTextColor(
            if (calibrated) {
                0xFF16A34A.toInt()
            } else {
                0xFF2563EB.toInt()
            }
        )
        button.background = ContextCompat.getDrawable(
            this,
            if (calibrated) {
                R.drawable.main_permission_done_bg
            } else {
                R.drawable.main_permission_action_bg
            }
        )
    }

    private fun openCalibrationEditor(key: String) {
        if (key == PvzCalibrationStorage.PLANT_SLOTS) {
            showPlantSlotsCalibrationPicker()
            return
        }
        if (key == PvzCalibrationStorage.BOARD) {
            showBoardCalibrationPicker()
            return
        }
        if (key == PvzCalibrationStorage.SUN) {
            showSunCalibrationPicker()
            return
        }
        if (key == PvzCalibrationStorage.PLANT_FOOD) {
            showPlantFoodCalibrationPicker()
            return
        }
        if (key == PvzCalibrationStorage.ARTIFACT) {
            showArtifactCalibrationPicker()
            return
        }
        if (key == PvzCalibrationStorage.CUCUMBER) {
            showCucumberCalibrationPicker()
            return
        }
        if (key == PvzCalibrationStorage.RECHARGE) {
            showRechargeCalibrationPicker()
            return
        }
        if (key == PvzCalibrationStorage.CARDS) {
            showCardsCalibrationPicker()
            return
        }
        if (key == PvzCalibrationStorage.OTHER) {
            showOtherCalibrationPicker()
            return
        }
        if (key == PvzCalibrationStorage.ENDLESS_SUPPLY) {
            showEndlessSupplyCalibrationPicker()
            return
        }
        Log.d(stopDebugTag, "PVZ2 calibration entry clicked: $key")
    }

    @SuppressLint("InflateParams")
    private fun showPlantSlotsCalibrationPicker() {
        val wm = windowManager ?: return
        if (calibrationPickerView != null) return

        ScreenshotHider.hideAll()
        val view = LayoutInflater.from(this).inflate(R.layout.area_picker, null)
        calibrationPickerView = view

        val params = createFullScreenPickerParams()

        val selectionView = view.findViewById<AreaSelectionView>(R.id.areaSelectionView)
        val buttonsRow = view.findViewById<View>(R.id.areaPickerButtons)
        val saveBtn = view.findViewById<TextView>(R.id.areaPickerSaveBtn)
        val cancelBtn = view.findViewById<TextView>(R.id.areaPickerCancelBtn)
        view.findViewById<TextView>(R.id.areaPickerInstruction)
            .setText(R.string.pvz_plant_slots_picker_instruction)

        selectionView.divisionCount = PLANT_SLOT_COUNT
        selectionView.divisionOrientation = AreaSelectionView.DivisionOrientation.HORIZONTAL
        restorePlantSlotsCalibrationArea(selectionView)
        selectionView.onInteractionStarted = {
            buttonsRow.visibility = View.GONE
        }
        selectionView.onInteractionFinished = {
            buttonsRow.visibility = View.VISIBLE
        }

        saveBtn.setOnClickListener {
            val rect = selectionView.getSelectionRect()
            if (rect == null) {
                Toast.makeText(this, R.string.condition_area_too_small, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val loc = IntArray(2)
            selectionView.getLocationOnScreen(loc)
            val screenRect = Rect(
                rect.left + loc[0],
                rect.top + loc[1],
                rect.right + loc[0],
                rect.bottom + loc[1]
            )
            savePlantSlotsCalibration(screenRect)
            selectionView.cleanup()
            hideCalibrationPickerOverlay(revealOverlays = true)
            refreshCalibrationPanelStatuses()
            Toast.makeText(this, R.string.pvz_plant_slots_calibration_saved, Toast.LENGTH_SHORT).show()
        }

        cancelBtn.setOnClickListener {
            selectionView.cleanup()
            hideCalibrationPickerOverlay(revealOverlays = true)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            ScreenshotHider.revealAll()
            Log.w(stopDebugTag, "Failed to show PVZ2 plant slots calibration picker", e)
        }
    }

    @SuppressLint("InflateParams")
    private fun showBoardCalibrationPicker() {
        val wm = windowManager ?: return
        if (calibrationPickerView != null) return

        ScreenshotHider.hideAll()
        val view = LayoutInflater.from(this).inflate(R.layout.area_picker, null)
        calibrationPickerView = view

        val params = createFullScreenPickerParams()

        val selectionView = view.findViewById<AreaSelectionView>(R.id.areaSelectionView)
        val buttonsRow = view.findViewById<View>(R.id.areaPickerButtons)
        val saveBtn = view.findViewById<TextView>(R.id.areaPickerSaveBtn)
        val cancelBtn = view.findViewById<TextView>(R.id.areaPickerCancelBtn)
        view.findViewById<TextView>(R.id.areaPickerInstruction)
            .setText(R.string.pvz_board_picker_instruction)

        selectionView.divisionRows = PvzCalibrationStorage.BOARD_ROWS
        selectionView.divisionColumns = PvzCalibrationStorage.BOARD_COLUMNS
        restoreBoardCalibrationArea(selectionView)
        selectionView.onInteractionStarted = {
            buttonsRow.visibility = View.GONE
        }
        selectionView.onInteractionFinished = {
            buttonsRow.visibility = View.VISIBLE
        }

        saveBtn.setOnClickListener {
            val rect = selectionView.getSelectionRect()
            if (rect == null) {
                Toast.makeText(this, R.string.condition_area_too_small, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val loc = IntArray(2)
            selectionView.getLocationOnScreen(loc)
            val screenRect = Rect(
                rect.left + loc[0],
                rect.top + loc[1],
                rect.right + loc[0],
                rect.bottom + loc[1]
            )
            saveBoardCalibration(screenRect)
            selectionView.cleanup()
            hideCalibrationPickerOverlay(revealOverlays = true)
            refreshCalibrationPanelStatuses()
            Toast.makeText(this, R.string.pvz_board_calibration_saved, Toast.LENGTH_SHORT).show()
        }

        cancelBtn.setOnClickListener {
            selectionView.cleanup()
            hideCalibrationPickerOverlay(revealOverlays = true)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            ScreenshotHider.revealAll()
            Log.w(stopDebugTag, "Failed to show PVZ2 board calibration picker", e)
        }
    }

    private fun restoreBoardCalibrationArea(selectionView: AreaSelectionView) {
        val area = PvzCalibrationStorage.getBoardArea(this) ?: return
        selectionView.setInitialRect(
            storedPercentXToEdgePx(area.left),
            storedPercentYToEdgePx(area.top),
            storedPercentXToEdgePx(area.right),
            storedPercentYToEdgePx(area.bottom)
        )
    }

    private fun saveBoardCalibration(rect: Rect) {
        val rowHeight = rect.height().toFloat() / PvzCalibrationStorage.BOARD_ROWS.toFloat()
        val columnWidth = rect.width().toFloat() / PvzCalibrationStorage.BOARD_COLUMNS.toFloat()
        val points = mutableListOf<PvzCalibrationStorage.GridPoint>()
        for (row in 1..PvzCalibrationStorage.BOARD_ROWS) {
            val centerY = rect.top + rowHeight * (row - 0.5f)
            for (column in 1..PvzCalibrationStorage.BOARD_COLUMNS) {
                val centerX = rect.left + columnWidth * (column - 0.5f)
                points.add(
                    PvzCalibrationStorage.GridPoint(
                        row = row,
                        column = column,
                        x = pixelXToStoredPercent(centerX.toInt()),
                        y = pixelYToStoredPercent(centerY.toInt())
                    )
                )
            }
        }
        PvzCalibrationStorage.saveBoard(
            this,
            left = pixelXToStoredPercent(rect.left),
            top = pixelYToStoredPercent(rect.top),
            right = pixelXToStoredPercent(rect.right),
            bottom = pixelYToStoredPercent(rect.bottom),
            centers = points
        )
    }

    private fun showSunCalibrationPicker() {
        val wm = windowManager ?: return
        if (calibrationPickerView != null) return

        ScreenshotHider.hideAll()
        val view = PvzPointCalibrationView(this)
        calibrationPickerView = view

        val params = createFullScreenPickerParams()

        view.setPoints(sunCalibrationPoints())
        view.onSave = { points ->
            saveSunCalibration(points)
            hideCalibrationPickerOverlay(revealOverlays = true)
            refreshCalibrationPanelStatuses()
            Toast.makeText(this, R.string.pvz_sun_calibration_saved, Toast.LENGTH_SHORT).show()
        }
        view.onCancel = {
            hideCalibrationPickerOverlay(revealOverlays = true)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            ScreenshotHider.revealAll()
            Log.w(stopDebugTag, "Failed to show PVZ2 sun calibration picker", e)
        }
    }

    private fun sunCalibrationPoints(): List<PvzCalibrationPoint> {
        val saved = PvzCalibrationStorage.getSunPoints(this).associateBy { it.key }
        if (saved.isNotEmpty()) {
            return sunPointSpecs().map { spec ->
                val point = saved[spec.key]
                PvzCalibrationPoint(
                    key = spec.key,
                    label = spec.label,
                    x = point?.x?.let { storedPercentXToPointPx(it).toFloat() } ?: spec.defaultX,
                    y = point?.y?.let { storedPercentYToPointPx(it).toFloat() } ?: spec.defaultY
                )
            }
        }
        return sunPointSpecs().map { spec ->
            PvzCalibrationPoint(
                key = spec.key,
                label = spec.label,
                x = spec.defaultX,
                y = spec.defaultY
            )
        }
    }

    private data class SunPointSpec(
        val key: String,
        val label: String,
        val defaultX: Float,
        val defaultY: Float
    )

    private fun sunPointSpecs(): List<SunPointSpec> {
        val screen = currentProgramScreenSize()
        val centerY = screen.height / 2f
        val labels = listOf(
            PvzCalibrationStorage.SUN_BUY_KEY to getString(R.string.pvz_sun_buy_key),
            PvzCalibrationStorage.SUN_AD to getString(R.string.pvz_sun_ad),
            PvzCalibrationStorage.SUN_10_DIAMOND to getString(R.string.pvz_sun_10_diamond),
            PvzCalibrationStorage.SUN_CLOSE to getString(R.string.pvz_sun_close)
        )
        return labels.mapIndexed { index, (key, label) ->
            SunPointSpec(
                key = key,
                label = label,
                defaultX = screen.width * (index + 1).toFloat() / (labels.size + 1).toFloat(),
                defaultY = centerY
            )
        }
    }

    private fun saveSunCalibration(points: List<PvzCalibrationPoint>) {
        PvzCalibrationStorage.saveSunPoints(
            this,
            points.map { point ->
                PvzCalibrationStorage.NamedPoint(
                    key = point.key,
                    x = pixelXToStoredPercent(point.x.toInt()),
                    y = pixelYToStoredPercent(point.y.toInt())
                )
            }
        )
    }

    private fun showPlantFoodCalibrationPicker() {
        val wm = windowManager ?: return
        if (calibrationPickerView != null) return

        ScreenshotHider.hideAll()
        val view = PvzPointCalibrationView(this)
        calibrationPickerView = view

        val params = createFullScreenPickerParams()

        view.setPoints(plantFoodCalibrationPoints())
        view.onSave = { points ->
            savePlantFoodCalibration(points)
            hideCalibrationPickerOverlay(revealOverlays = true)
            refreshCalibrationPanelStatuses()
            Toast.makeText(this, R.string.pvz_plant_food_calibration_saved, Toast.LENGTH_SHORT).show()
        }
        view.onCancel = {
            hideCalibrationPickerOverlay(revealOverlays = true)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            ScreenshotHider.revealAll()
            Log.w(stopDebugTag, "Failed to show PVZ2 plant food calibration picker", e)
        }
    }

    private fun plantFoodCalibrationPoints(): List<PvzCalibrationPoint> {
        val saved = PvzCalibrationStorage.getPlantFoodPoints(this).associateBy { it.key }
        if (saved.isNotEmpty()) {
            return plantFoodPointSpecs().map { spec ->
                val point = saved[spec.key]
                PvzCalibrationPoint(
                    key = spec.key,
                    label = spec.label,
                    x = point?.x?.let { storedPercentXToPointPx(it).toFloat() } ?: spec.defaultX,
                    y = point?.y?.let { storedPercentYToPointPx(it).toFloat() } ?: spec.defaultY
                )
            }
        }
        return plantFoodPointSpecs().map { spec ->
            PvzCalibrationPoint(
                key = spec.key,
                label = spec.label,
                x = spec.defaultX,
                y = spec.defaultY
            )
        }
    }

    private data class PlantFoodPointSpec(
        val key: String,
        val label: String,
        val defaultX: Float,
        val defaultY: Float
    )

    private fun plantFoodPointSpecs(): List<PlantFoodPointSpec> {
        val screen = currentProgramScreenSize()
        val centerY = screen.height / 2f
        val labels = listOf(
            PvzCalibrationStorage.PLANT_FOOD_BEAN to getString(R.string.pvz_plant_food_bean),
            PvzCalibrationStorage.PLANT_FOOD_PLUS to getString(R.string.pvz_plant_food_plus),
            PvzCalibrationStorage.PLANT_FOOD_BUY to getString(R.string.pvz_plant_food_buy)
        )
        return labels.mapIndexed { index, (key, label) ->
            PlantFoodPointSpec(
                key = key,
                label = label,
                defaultX = screen.width * (index + 1).toFloat() / (labels.size + 1).toFloat(),
                defaultY = centerY
            )
        }
    }

    private fun savePlantFoodCalibration(points: List<PvzCalibrationPoint>) {
        PvzCalibrationStorage.savePlantFoodPoints(
            this,
            points.map { point ->
                PvzCalibrationStorage.NamedPoint(
                    key = point.key,
                    x = pixelXToStoredPercent(point.x.toInt()),
                    y = pixelYToStoredPercent(point.y.toInt())
                )
            }
        )
    }

    private fun showArtifactCalibrationPicker() {
        val wm = windowManager ?: return
        if (calibrationPickerView != null) return

        ScreenshotHider.hideAll()
        val view = PvzPointCalibrationView(this)
        calibrationPickerView = view

        val params = createFullScreenPickerParams()

        view.setPoints(artifactCalibrationPoints())
        view.onSave = { points ->
            saveArtifactCalibration(points)
            hideCalibrationPickerOverlay(revealOverlays = true)
            refreshCalibrationPanelStatuses()
            Toast.makeText(this, R.string.pvz_artifact_calibration_saved, Toast.LENGTH_SHORT).show()
        }
        view.onCancel = {
            hideCalibrationPickerOverlay(revealOverlays = true)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            ScreenshotHider.revealAll()
            Log.w(stopDebugTag, "Failed to show PVZ2 artifact calibration picker", e)
        }
    }

    private fun artifactCalibrationPoints(): List<PvzCalibrationPoint> {
        val saved = PvzCalibrationStorage.getArtifactPoints(this).associateBy { it.key }
        if (saved.isNotEmpty()) {
            return artifactPointSpecs().map { spec ->
                val point = saved[spec.key]
                PvzCalibrationPoint(
                    key = spec.key,
                    label = spec.label,
                    x = point?.x?.let { storedPercentXToPointPx(it).toFloat() } ?: spec.defaultX,
                    y = point?.y?.let { storedPercentYToPointPx(it).toFloat() } ?: spec.defaultY
                )
            }
        }
        return artifactPointSpecs().map { spec ->
            PvzCalibrationPoint(
                key = spec.key,
                label = spec.label,
                x = spec.defaultX,
                y = spec.defaultY
            )
        }
    }

    private data class ArtifactPointSpec(
        val key: String,
        val label: String,
        val defaultX: Float,
        val defaultY: Float
    )

    private fun artifactPointSpecs(): List<ArtifactPointSpec> {
        val screen = currentProgramScreenSize()
        val centerY = screen.height / 2f
        val labels = listOf(
            PvzCalibrationStorage.ARTIFACT_MAIN to getString(R.string.pvz_artifact_main),
            PvzCalibrationStorage.ARTIFACT_SMALL to getString(R.string.pvz_artifact_small),
            PvzCalibrationStorage.ARTIFACT_MEDIUM to getString(R.string.pvz_artifact_medium),
            PvzCalibrationStorage.ARTIFACT_LARGE to getString(R.string.pvz_artifact_large)
        )
        return labels.mapIndexed { index, (key, label) ->
            ArtifactPointSpec(
                key = key,
                label = label,
                defaultX = screen.width * (index + 1).toFloat() / (labels.size + 1).toFloat(),
                defaultY = centerY
            )
        }
    }

    private fun saveArtifactCalibration(points: List<PvzCalibrationPoint>) {
        PvzCalibrationStorage.saveArtifactPoints(
            this,
            points.map { point ->
                PvzCalibrationStorage.NamedPoint(
                    key = point.key,
                    x = pixelXToStoredPercent(point.x.toInt()),
                    y = pixelYToStoredPercent(point.y.toInt())
                )
            }
        )
    }

    private fun showCucumberCalibrationPicker() {
        val wm = windowManager ?: return
        if (calibrationPickerView != null) return

        ScreenshotHider.hideAll()
        val view = PvzPointCalibrationView(this)
        calibrationPickerView = view

        val params = createFullScreenPickerParams()

        view.setPoints(cucumberCalibrationPoints())
        view.onSave = { points ->
            saveCucumberCalibration(points)
            hideCalibrationPickerOverlay(revealOverlays = true)
            refreshCalibrationPanelStatuses()
            Toast.makeText(this, R.string.pvz_cucumber_calibration_saved, Toast.LENGTH_SHORT).show()
        }
        view.onCancel = {
            hideCalibrationPickerOverlay(revealOverlays = true)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            ScreenshotHider.revealAll()
            Log.w(stopDebugTag, "Failed to show PVZ2 cucumber calibration picker", e)
        }
    }

    private fun cucumberCalibrationPoints(): List<PvzCalibrationPoint> {
        val saved = PvzCalibrationStorage.getCucumberPoints(this).associateBy { it.key }
        if (saved.isNotEmpty()) {
            return cucumberPointSpecs().map { spec ->
                val point = saved[spec.key]
                PvzCalibrationPoint(
                    key = spec.key,
                    label = spec.label,
                    x = point?.x?.let { storedPercentXToPointPx(it).toFloat() } ?: spec.defaultX,
                    y = point?.y?.let { storedPercentYToPointPx(it).toFloat() } ?: spec.defaultY
                )
            }
        }
        return cucumberPointSpecs().map { spec ->
            PvzCalibrationPoint(
                key = spec.key,
                label = spec.label,
                x = spec.defaultX,
                y = spec.defaultY
            )
        }
    }

    private data class CucumberPointSpec(
        val key: String,
        val label: String,
        val defaultX: Float,
        val defaultY: Float
    )

    private fun cucumberPointSpecs(): List<CucumberPointSpec> {
        val screen = currentProgramScreenSize()
        val centerY = screen.height / 2f
        val labels = listOf(
            PvzCalibrationStorage.CUCUMBER_MAIN to getString(R.string.pvz_cucumber_main),
            PvzCalibrationStorage.CUCUMBER_DROP to getString(R.string.pvz_cucumber_drop),
            PvzCalibrationStorage.CUCUMBER_CLOSE to getString(R.string.pvz_cucumber_close)
        )
        return labels.mapIndexed { index, (key, label) ->
            CucumberPointSpec(
                key = key,
                label = label,
                defaultX = screen.width * (index + 1).toFloat() / (labels.size + 1).toFloat(),
                defaultY = centerY
            )
        }
    }

    private fun saveCucumberCalibration(points: List<PvzCalibrationPoint>) {
        PvzCalibrationStorage.saveCucumberPoints(
            this,
            points.map { point ->
                PvzCalibrationStorage.NamedPoint(
                    key = point.key,
                    x = pixelXToStoredPercent(point.x.toInt()),
                    y = pixelYToStoredPercent(point.y.toInt())
                )
            }
        )
    }

    private fun showRechargeCalibrationPicker() {
        val wm = windowManager ?: return
        if (calibrationPickerView != null) return

        ScreenshotHider.hideAll()
        val view = PvzPointCalibrationView(this)
        calibrationPickerView = view

        val params = createFullScreenPickerParams()

        view.setPoints(rechargeCalibrationPoints())
        view.onSave = { points ->
            saveRechargeCalibration(points)
            hideCalibrationPickerOverlay(revealOverlays = true)
            refreshCalibrationPanelStatuses()
            Toast.makeText(this, R.string.pvz_recharge_calibration_saved, Toast.LENGTH_SHORT).show()
        }
        view.onCancel = {
            hideCalibrationPickerOverlay(revealOverlays = true)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            ScreenshotHider.revealAll()
            Log.w(stopDebugTag, "Failed to show PVZ2 recharge calibration picker", e)
        }
    }

    private fun rechargeCalibrationPoints(): List<PvzCalibrationPoint> {
        val saved = PvzCalibrationStorage.getRechargePoints(this).associateBy { it.key }
        if (saved.isNotEmpty()) {
            return rechargePointSpecs().map { spec ->
                val point = saved[spec.key]
                PvzCalibrationPoint(
                    key = spec.key,
                    label = spec.label,
                    x = point?.x?.let { storedPercentXToPointPx(it).toFloat() } ?: spec.defaultX,
                    y = point?.y?.let { storedPercentYToPointPx(it).toFloat() } ?: spec.defaultY
                )
            }
        }
        return rechargePointSpecs().map { spec ->
            PvzCalibrationPoint(
                key = spec.key,
                label = spec.label,
                x = spec.defaultX,
                y = spec.defaultY
            )
        }
    }

    private data class RechargePointSpec(
        val key: String,
        val label: String,
        val defaultX: Float,
        val defaultY: Float
    )

    private fun rechargePointSpecs(): List<RechargePointSpec> {
        val screen = currentProgramScreenSize()
        val centerY = screen.height / 2f
        val labels = listOf(
            PvzCalibrationStorage.RECHARGE_MAIN to getString(R.string.pvz_recharge_main),
            PvzCalibrationStorage.RECHARGE_CLOSE to getString(R.string.pvz_recharge_close)
        )
        return labels.mapIndexed { index, (key, label) ->
            RechargePointSpec(
                key = key,
                label = label,
                defaultX = screen.width * (index + 1).toFloat() / (labels.size + 1).toFloat(),
                defaultY = centerY
            )
        }
    }

    private fun saveRechargeCalibration(points: List<PvzCalibrationPoint>) {
        PvzCalibrationStorage.saveRechargePoints(
            this,
            points.map { point ->
                PvzCalibrationStorage.NamedPoint(
                    key = point.key,
                    x = pixelXToStoredPercent(point.x.toInt()),
                    y = pixelYToStoredPercent(point.y.toInt())
                )
            }
        )
    }

    private fun showCardsCalibrationPicker() {
        val wm = windowManager ?: return
        if (calibrationPickerView != null) return

        ScreenshotHider.hideAll()
        val view = PvzPointCalibrationView(this)
        calibrationPickerView = view

        val params = createFullScreenPickerParams()

        view.setPoints(cardsCalibrationPoints())
        view.onSave = { points ->
            saveCardsCalibration(points)
            hideCalibrationPickerOverlay(revealOverlays = true)
            refreshCalibrationPanelStatuses()
            Toast.makeText(this, R.string.pvz_cards_calibration_saved, Toast.LENGTH_SHORT).show()
        }
        view.onCancel = {
            hideCalibrationPickerOverlay(revealOverlays = true)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            ScreenshotHider.revealAll()
            Log.w(stopDebugTag, "Failed to show PVZ2 cards calibration picker", e)
        }
    }

    private fun cardsCalibrationPoints(): List<PvzCalibrationPoint> {
        val screen = currentProgramScreenSize()
        val saved = PvzCalibrationStorage.getCardsPoints(this).firstOrNull()
        return listOf(
            PvzCalibrationPoint(
                key = PvzCalibrationStorage.CARDS_EDGE,
                label = getString(R.string.pvz_cards_edge),
                x = saved?.x?.let { storedPercentXToPointPx(it).toFloat() } ?: screen.width / 2f,
                y = saved?.y?.let { storedPercentYToPointPx(it).toFloat() } ?: screen.height / 2f
            )
        )
    }

    private fun saveCardsCalibration(points: List<PvzCalibrationPoint>) {
        PvzCalibrationStorage.saveCardsPoints(
            this,
            points.map { point ->
                PvzCalibrationStorage.NamedPoint(
                    key = point.key,
                    x = pixelXToStoredPercent(point.x.toInt()),
                    y = pixelYToStoredPercent(point.y.toInt())
                )
            }
        )
    }

    private fun showOtherCalibrationPicker() {
        val wm = windowManager ?: return
        if (calibrationPickerView != null) return

        ScreenshotHider.hideAll()
        val view = PvzPointCalibrationView(this)
        calibrationPickerView = view

        val params = createFullScreenPickerParams()

        view.setPoints(otherCalibrationPoints())
        view.onSave = { points ->
            saveOtherCalibration(points)
            hideCalibrationPickerOverlay(revealOverlays = true)
            refreshCalibrationPanelStatuses()
            Toast.makeText(this, R.string.pvz_other_calibration_saved, Toast.LENGTH_SHORT).show()
        }
        view.onCancel = {
            hideCalibrationPickerOverlay(revealOverlays = true)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            ScreenshotHider.revealAll()
            Log.w(stopDebugTag, "Failed to show PVZ2 other calibration picker", e)
        }
    }

    private fun otherCalibrationPoints(): List<PvzCalibrationPoint> {
        val saved = PvzCalibrationStorage.getOtherPoints(this).associateBy { it.key }
        if (saved.isNotEmpty()) {
            return otherPointSpecs().map { spec ->
                val point = saved[spec.key]
                PvzCalibrationPoint(
                    key = spec.key,
                    label = spec.label,
                    x = point?.x?.let { storedPercentXToPointPx(it).toFloat() } ?: spec.defaultX,
                    y = point?.y?.let { storedPercentYToPointPx(it).toFloat() } ?: spec.defaultY
                )
            }
        }
        return otherPointSpecs().map { spec ->
            PvzCalibrationPoint(
                key = spec.key,
                label = spec.label,
                x = spec.defaultX,
                y = spec.defaultY
            )
        }
    }

    private data class OtherPointSpec(
        val key: String,
        val label: String,
        val defaultX: Float,
        val defaultY: Float
    )

    private fun otherPointSpecs(): List<OtherPointSpec> {
        val screen = currentProgramScreenSize()
        val labels = listOf(
            PvzCalibrationStorage.OTHER_SPEED_UP to getString(R.string.pvz_other_speed_up),
            PvzCalibrationStorage.OTHER_PAUSE to getString(R.string.pvz_other_pause),
            PvzCalibrationStorage.OTHER_CONTINUE to getString(R.string.pvz_other_continue),
            PvzCalibrationStorage.OTHER_RESTART to getString(R.string.pvz_other_restart),
            PvzCalibrationStorage.OTHER_BACK_TO_MAP to getString(R.string.pvz_other_back_to_map),
            PvzCalibrationStorage.OTHER_SHOVEL to getString(R.string.pvz_other_shovel),
            PvzCalibrationStorage.OTHER_CARD_START_BATTLE to getString(R.string.pvz_other_card_start_battle),
            PvzCalibrationStorage.OTHER_START_BATTLE to getString(R.string.pvz_other_start_battle),
            PvzCalibrationStorage.OTHER_FINAL_WAVE_RED to getString(R.string.pvz_other_final_wave_red),
            PvzCalibrationStorage.OTHER_NEXT_WAVE to getString(R.string.pvz_other_next_wave),
            PvzCalibrationStorage.OTHER_SWITCH_FORM to getString(R.string.pvz_other_switch_form)
        )
        val columns = 3
        val rows = (labels.size + columns - 1) / columns
        return labels.mapIndexed { index, (key, label) ->
            val column = index % columns
            val row = index / columns
            OtherPointSpec(
                key = key,
                label = label,
                defaultX = screen.width * (column + 1).toFloat() / (columns + 1).toFloat(),
                defaultY = screen.height * (row + 1).toFloat() / (rows + 1).toFloat()
            )
        }
    }

    private fun saveOtherCalibration(points: List<PvzCalibrationPoint>) {
        PvzCalibrationStorage.saveOtherPoints(
            this,
            points.map { point ->
                PvzCalibrationStorage.NamedPoint(
                    key = point.key,
                    x = pixelXToStoredPercent(point.x.toInt()),
                    y = pixelYToStoredPercent(point.y.toInt())
                )
            }
        )
    }

    private fun showEndlessSupplyCalibrationPicker() {
        val wm = windowManager ?: return
        if (calibrationPickerView != null) return

        ScreenshotHider.hideAll()
        val view = PvzEndlessSupplyCalibrationView(this)
        calibrationPickerView = view

        val params = createFullScreenPickerParams()

        view.setCalibration(endlessSupplyCalibrationArea(), endlessSupplyCalibrationPoints())
        view.onSave = { area, points ->
            saveEndlessSupplyCalibration(area, points)
            hideCalibrationPickerOverlay(revealOverlays = true)
            refreshCalibrationPanelStatuses()
            Toast.makeText(this, R.string.pvz_endless_supply_calibration_saved, Toast.LENGTH_SHORT).show()
        }
        view.onCancel = {
            hideCalibrationPickerOverlay(revealOverlays = true)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            calibrationPickerView = null
            ScreenshotHider.revealAll()
            Log.w(stopDebugTag, "Failed to show PVZ2 endless supply calibration picker", e)
        }
    }

    private fun endlessSupplyCalibrationArea(): PvzCalibrationArea {
        val saved = PvzCalibrationStorage.getEndlessSupplyTextArea(this)
        val screen = currentProgramScreenSize()
        val rect = if (saved != null) {
            Rect(
                storedPercentXToEdgePx(saved.left),
                storedPercentYToEdgePx(saved.top),
                storedPercentXToEdgePx(saved.right),
                storedPercentYToEdgePx(saved.bottom)
            )
        } else {
            Rect(
                (screen.width * 0.38f).toInt(),
                (screen.height * 0.22f).toInt(),
                (screen.width * 0.62f).toInt(),
                (screen.height * 0.34f).toInt()
            )
        }
        return PvzCalibrationArea(
            label = getString(R.string.pvz_endless_supply_text_area),
            rect = android.graphics.RectF(rect)
        )
    }

    private fun endlessSupplyCalibrationPoints(): List<PvzCalibrationPoint> {
        val saved = PvzCalibrationStorage.getEndlessSupplyPoints(this).associateBy { it.key }
        if (saved.isNotEmpty()) {
            return endlessSupplyPointSpecs().map { spec ->
                val point = saved[spec.key]
                PvzCalibrationPoint(
                    key = spec.key,
                    label = spec.label,
                    x = point?.x?.let { storedPercentXToPointPx(it).toFloat() } ?: spec.defaultX,
                    y = point?.y?.let { storedPercentYToPointPx(it).toFloat() } ?: spec.defaultY
                )
            }
        }
        return endlessSupplyPointSpecs().map { spec ->
            PvzCalibrationPoint(
                key = spec.key,
                label = spec.label,
                x = spec.defaultX,
                y = spec.defaultY
            )
        }
    }

    private data class EndlessSupplyPointSpec(
        val key: String,
        val label: String,
        val defaultX: Float,
        val defaultY: Float
    )

    private fun endlessSupplyPointSpecs(): List<EndlessSupplyPointSpec> {
        val screen = currentProgramScreenSize()
        val labels = listOf(
            PvzCalibrationStorage.ENDLESS_SUPPLY_ABILITY to getString(R.string.pvz_endless_supply_ability),
            PvzCalibrationStorage.ENDLESS_SUPPLY_BLUE_CONFIRM to getString(R.string.pvz_endless_supply_blue_confirm),
            PvzCalibrationStorage.ENDLESS_SUPPLY_GREEN_CONFIRM to getString(R.string.pvz_endless_supply_green_confirm),
            PvzCalibrationStorage.ENDLESS_SUPPLY_FINAL_CONFIRM to getString(R.string.pvz_endless_supply_final_confirm),
            PvzCalibrationStorage.ENDLESS_SUPPLY_PAIR to getString(R.string.pvz_endless_supply_pair),
            PvzCalibrationStorage.ENDLESS_SUPPLY_1 to getString(R.string.pvz_endless_supply_1),
            PvzCalibrationStorage.ENDLESS_SUPPLY_2 to getString(R.string.pvz_endless_supply_2),
            PvzCalibrationStorage.ENDLESS_SUPPLY_3 to getString(R.string.pvz_endless_supply_3),
            PvzCalibrationStorage.ENDLESS_SUPPLY_CONTINUE_CHALLENGE to getString(R.string.pvz_endless_supply_continue_challenge)
        )
        val columns = 5
        val rows = (labels.size + columns - 1) / columns
        return labels.mapIndexed { index, (key, label) ->
            val column = index % columns
            val row = index / columns
            EndlessSupplyPointSpec(
                key = key,
                label = label,
                defaultX = screen.width * (column + 1).toFloat() / (columns + 1).toFloat(),
                defaultY = screen.height * (row + 1).toFloat() / (rows + 1).toFloat()
            )
        }
    }

    private fun saveEndlessSupplyCalibration(
        area: PvzCalibrationArea,
        points: List<PvzCalibrationPoint>
    ) {
        val rect = area.rect
        PvzCalibrationStorage.saveEndlessSupply(
            this,
            textArea = PvzCalibrationStorage.Area(
                left = pixelXToStoredPercent(rect.left.toInt()),
                top = pixelYToStoredPercent(rect.top.toInt()),
                right = pixelXToStoredPercent(rect.right.toInt()),
                bottom = pixelYToStoredPercent(rect.bottom.toInt())
            ),
            points = points.map { point ->
                PvzCalibrationStorage.NamedPoint(
                    key = point.key,
                    x = pixelXToStoredPercent(point.x.toInt()),
                    y = pixelYToStoredPercent(point.y.toInt())
                )
            }
        )
    }

    private fun restorePlantSlotsCalibrationArea(selectionView: AreaSelectionView) {
        val area = PvzCalibrationStorage.getPlantSlotsArea(this) ?: return
        selectionView.setInitialRect(
            storedPercentXToEdgePx(area.left),
            storedPercentYToEdgePx(area.top),
            storedPercentXToEdgePx(area.right),
            storedPercentYToEdgePx(area.bottom)
        )
    }

    private fun savePlantSlotsCalibration(rect: Rect) {
        val slotHeight = rect.height().toFloat() / PLANT_SLOT_COUNT.toFloat()
        val centerX = rect.left + rect.width() / 2f
        val points = (0 until PLANT_SLOT_COUNT).map { index ->
            val centerY = rect.top + slotHeight * (index + 0.5f)
            PvzCalibrationStorage.Point(
                x = pixelXToStoredPercent(centerX.toInt()),
                y = pixelYToStoredPercent(centerY.toInt())
            )
        }
        PvzCalibrationStorage.savePlantSlots(
            this,
            left = pixelXToStoredPercent(rect.left),
            top = pixelYToStoredPercent(rect.top),
            right = pixelXToStoredPercent(rect.right),
            bottom = pixelYToStoredPercent(rect.bottom),
            centers = points
        )
    }

    private fun refreshCalibrationPanelStatuses() {
        val panel = calibrationPanelView ?: return
        bindCalibrationStatusButtons(panel)
    }

    private fun hideCalibrationPickerOverlay(revealOverlays: Boolean) {
        val view = calibrationPickerView ?: return
        if (view is PvzPointCalibrationView) {
            view.cleanup()
        }
        if (view is PvzEndlessSupplyCalibrationView) {
            view.cleanup()
        }
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
        }
        calibrationPickerView = null
        // Recycle the background bitmap if present
        areaPickerBackgroundBitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
        areaPickerBackgroundBitmap = null
        if (revealOverlays) {
            ScreenshotHider.revealAll()
        }
    }

    private fun pixelXToStoredPercent(x: Int): Int {
        return ProgramCoordinateAdapter.pointToStoredPercent(x, currentProgramScreenSize().width)
    }

    private fun pixelYToStoredPercent(y: Int): Int {
        return ProgramCoordinateAdapter.pointToStoredPercent(y, currentProgramScreenSize().height)
    }

    private fun storedPercentXToEdgePx(value: Int): Int {
        return ProgramCoordinateAdapter.storedPercentToEdgePx(value, currentProgramScreenSize().width)
    }

    private fun storedPercentYToEdgePx(value: Int): Int {
        return ProgramCoordinateAdapter.storedPercentToEdgePx(value, currentProgramScreenSize().height)
    }

    private fun storedPercentXToPointPx(value: Int): Int {
        return ProgramCoordinateAdapter.storedPercentToPointPx(value, currentProgramScreenSize().width)
    }

    private fun storedPercentYToPointPx(value: Int): Int {
        return ProgramCoordinateAdapter.storedPercentToPointPx(value, currentProgramScreenSize().height)
    }

    private fun currentProgramScreenSize(): ProgramScreenSize {
        val display = ScreenCaptureDisplayReader.current(this)
        val width = display.width
            .takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        val height = display.height
            .takeIf { it > 0 }
            ?: resources.displayMetrics.heightPixels
        return ProgramScreenSize(width, height)
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

    private fun insertProgramSnippetInline(codeInput: EditText, snippet: String) {
        val code = codeInput.text.toString()
        val selectionStart = codeInput.selectionStart.coerceAtLeast(0)
        val selectionEnd = codeInput.selectionEnd.coerceAtLeast(0)
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, code.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, code.length)
        val result = code.substring(0, start) + snippet + code.substring(end)
        codeInput.setText(result)
        codeInput.setSelection((start + snippet.length).coerceIn(0, result.length))
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
                    true
                }
                else -> false
            }
        }
    }

    private fun removePanel(view: View?, zoneKey: String, onRemoved: () -> Unit) {
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

    private fun testProgramParse(code: String) {
        try {
            val globals = org.luaj.vm2.lib.jse.JsePlatform.standardGlobals()
            globals.load(code, "pvz2")
            Toast.makeText(this, R.string.program_parse_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: getString(R.string.program_parse_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateProgramCode(code: String): Boolean {
        if (code.isBlank()) {
            Toast.makeText(this, R.string.program_code_empty, Toast.LENGTH_SHORT).show()
            return false
        }
        try {
            val globals = org.luaj.vm2.lib.jse.JsePlatform.standardGlobals()
            globals.load(code, "pvz2")
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: getString(R.string.program_parse_failed), Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun showSaveConfirmPanel() {
        val code = loadCurrentProgramCode()
        if (code.isBlank()) {
            Toast.makeText(this, R.string.pvz_program_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val view = floatingView ?: return
        val panel = view.findViewById<View>(R.id.pvzSaveConfirmPanel)
        val input = view.findViewById<EditText>(R.id.pvzSaveNameInput)
        input.setText(getCurrentScriptName() ?: PvzScriptStorage.nextAutoName(this))
        input.selectAll()
        panel.visibility = View.VISIBLE
        enableOverlayFocus()
    }

    private fun saveCurrentProgramWithConfirmedName() {
        val view = floatingView ?: return
        val code = loadCurrentProgramCode()
        if (code.isBlank()) {
            Toast.makeText(this, R.string.pvz_program_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val input = view.findViewById<EditText>(R.id.pvzSaveNameInput)
        val name = input.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.script_name_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val oldName = getCurrentScriptName()
        if (name != oldName && PvzScriptStorage.getScript(this, name) != null) {
            Toast.makeText(this, R.string.script_name_exists, Toast.LENGTH_SHORT).show()
            return
        }
        saveCurrentProgramCode(name, code)
        view.findViewById<View>(R.id.pvzSaveConfirmPanel).visibility = View.GONE
        disableOverlayFocus()
        Toast.makeText(this, R.string.script_saved, Toast.LENGTH_SHORT).show()
    }

    private fun loadCurrentProgramCode(): String {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PROGRAM_CODE, "").orEmpty()
    }

    private fun saveCurrentProgramDraft(code: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PROGRAM_CODE, code)
            .apply()
    }

    private fun saveCurrentProgramCode(name: String, code: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val scriptName = prefs.getString(KEY_SCRIPT_NAME, null)
            ?.takeIf { it.isNotBlank() }
        if (scriptName != null && scriptName != name) {
            PvzScriptStorage.deleteScript(this, scriptName)
        }
        prefs.edit()
            .putString(KEY_PROGRAM_CODE, code)
            .putString(KEY_SCRIPT_NAME, name)
            .apply()
        PvzScriptStorage.saveNamedScript(
            this,
            name,
            listOf(
                ActionStep(
                    type = ActionStep.TYPE_PROGRAM,
                    code = code,
                    delayBeforeMs = 1L,
                    repeatCount = 1
                )
            ),
            loopCount = 1,
            loopGapMs = 0L
        )
        sendBroadcast(Intent(PvzScriptStorage.ACTION_SCRIPTS_CHANGED).apply {
            setPackage(packageName)
        })
        updateFloatingTitle()
    }

    private fun getCurrentScriptName(): String? {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SCRIPT_NAME, null)
            ?.takeIf { it.isNotBlank() }
    }

    private fun updateFloatingTitle() {
        val view = floatingView ?: return
        val name = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SCRIPT_NAME, null)
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.pvz_default_script_name)
        view.findViewById<TextView>(R.id.pvzDragHandle).text = name
        view.findViewById<TextView>(R.id.pvzCollapsedTitle).text = name
    }

    companion object {
        const val CHANNEL_ID = "pvz_floating_control_channel"
        const val NOTIFICATION_ID = 4
        const val ACTION_STOP_EXECUTION = "com.fffcccdfgh.androidclicker.PVZ_STOP_EXECUTION"
        const val ACTION_OPEN_EDITOR = "com.fffcccdfgh.androidclicker.PVZ_OPEN_EDITOR"
        const val PREFS_NAME = "pvz2_script_config"
        const val KEY_PROGRAM_CODE = "program_code"
        const val KEY_SCRIPT_NAME = "script_name"
        private const val ZONE_KEY = "pvz_floating_control"
        private const val EDITOR_ZONE_KEY = "pvz_program_editor"
        private const val TEMPLATE_ZONE_KEY = "pvz_program_template_panel"
        private const val CALIBRATION_ZONE_KEY = "pvz_calibration_panel"
        private const val STOP_BUTTON_ZONE_KEY = "pvz_execution_stop_button"
        private const val PROGRAM_TEMPLATE_PANEL_WIDTH_DP = 292f
        private const val PROGRAM_TEMPLATE_ROW_HEIGHT_DP = 44f
        private const val PROGRAM_TEMPLATE_MAX_VISIBLE_ROWS = 9
        private const val COLOR_PICK_CAPTURE_TIMEOUT_MS = 3000L
        private const val PLANT_SLOT_COUNT = 8
        var isRunning = false
            private set
    }
}
