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
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
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
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan

class FloatingControlService : Service() {
    private val stopDebugTag = "ClickerStopDebug"

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var floatingParams: WindowManager.LayoutParams? = null
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
    private var condEditUseArea: Boolean = false
    private var condEditLeft: Int? = null
    private var condEditTop: Int? = null
    private var condEditRight: Int? = null
    private var condEditBottom: Int? = null
    private var condEditColorHex: String? = null
    private var condEditColorTolerance: Int? = null
    private var condEditColorX: Int? = null
    private var condEditColorY: Int? = null
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
        screenWidthPx = resources.displayMetrics.widthPixels
        screenHeightPx = resources.displayMetrics.heightPixels

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

        val addMenu = view.findViewById<View>(R.id.addMenu)

        view.findViewById<View>(R.id.tapOption).setOnClickListener {
            addMenu.visibility = View.GONE
            showPickerOverlay(PICKER_TAP_POINT)
        }

        view.findViewById<View>(R.id.swipeOption).setOnClickListener {
            addMenu.visibility = View.GONE
            showPickerOverlay(PICKER_SWIPE_START)
        }

        view.findViewById<View>(R.id.waitOption).setOnClickListener {
            addMenu.visibility = View.GONE
            showWaitDurationPicker()
        }

        view.findViewById<View>(R.id.programOption).setOnClickListener {
            addMenu.visibility = View.GONE
            showProgramEditor()
        }

        view.findViewById<View>(R.id.saveButton).setOnClickListener {
            val sequence = loadSequence()
            if (sequence.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_action_to_save), Toast.LENGTH_SHORT).show()
            } else {
                hideActionList()
                view.findViewById<View>(R.id.addMenu).visibility = View.GONE
                view.findViewById<View>(R.id.loopSettingsPanel).visibility = View.GONE
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

        view.findViewById<View>(R.id.loopSettingsSave).setOnClickListener {
            saveLoopSettings()
        }

        bindActionListScrollBar(view)
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
        hideAllPanels()
        val view = floatingView ?: return
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

    private fun toggleAddMenu() {
        val view = floatingView ?: return
        val menu = view.findViewById<View>(R.id.addMenu)
        val opening = menu.visibility != View.VISIBLE
        if (opening) {
            hideActionList()
            view.findViewById<View>(R.id.saveConfirmPanel).visibility = View.GONE
            view.findViewById<View>(R.id.loopSettingsPanel).visibility = View.GONE
            disableOverlayFocus()
        }
        menu.visibility = if (opening) View.VISIBLE else View.GONE
    }

    private fun toggleActionList() {
        if (actionListVisible) {
            hideActionList()
        } else {
            showActionList()
        }
    }

    private fun showActionList() {
        val view = floatingView ?: return
        val panel = view.findViewById<View>(R.id.actionListPanel)
        view.findViewById<View>(R.id.addMenu).visibility = View.GONE
        view.findViewById<View>(R.id.saveConfirmPanel).visibility = View.GONE
        view.findViewById<View>(R.id.loopSettingsPanel).visibility = View.GONE
        disableOverlayFocus()
        panel.visibility = View.VISIBLE
        actionListVisible = true
        renderActionList()
    }

    private fun hideActionList() {
        val view = floatingView ?: return
        val panel = view.findViewById<View>(R.id.actionListPanel)
        panel.visibility = View.GONE
        actionListVisible = false
    }

    private fun renderActionList() {
        val view = floatingView ?: return
        val sequence = loadSequence()
        val container = view.findViewById<LinearLayout>(R.id.actionListContainer)
        container.removeAllViews()

        val header = view.findViewById<TextView>(R.id.actionListHeader)

        if (sequence.isEmpty()) {
            header.text = getString(R.string.action_list_empty)
            updateActionListViewport(0)
            return
        }

        header.text = getString(R.string.action_list_title, sequence.size)

        for ((i, action) in sequence.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, 4, 0, 4)
            }

            val baseText = when (action.type) {
                ActionStep.TYPE_TAP -> getString(R.string.action_list_tap_short, i + 1, action.x!!, action.y!!)
                ActionStep.TYPE_SWIPE -> getString(
                    R.string.action_list_swipe_short,
                    i + 1, action.startX!!, action.startY!!, action.endX!!, action.endY!!
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
                setTextColor(Color.WHITE)
                textSize = 12f
            }

            val stepIndex = i
            val buttonRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START
            }

            val settingsBtn = TextView(this).apply {
                text = getString(R.string.settings_action_button)
                setTextColor(0xFF4CAF50.toInt())
                textSize = 12f
                setPadding(0, 2, 6, 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    showActionSettings(stepIndex)
                }
            }

            val deleteBtn = TextView(this).apply {
                text = getString(R.string.delete_action)
                setTextColor(0xFFFF8888.toInt())
                textSize = 12f
                setPadding(0, 2, 6, 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    deleteActionAt(stepIndex)
                }
            }

            val insertBeforeBtn = TextView(this).apply {
                text = getString(R.string.insert_before)
                setTextColor(0xFF2196F3.toInt())
                textSize = 12f
                setPadding(0, 2, 6, 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    pendingInsertIndex = stepIndex
                    showInsertTypeMenu()
                }
            }

            val insertAfterBtn = TextView(this).apply {
                text = getString(R.string.insert_after)
                setTextColor(0xFF2196F3.toInt())
                textSize = 12f
                setPadding(0, 2, 0, 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    pendingInsertIndex = stepIndex + 1
                    showInsertTypeMenu()
                }
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

    private fun bindActionListScrollBar(view: View) {
        val scroll = view.findViewById<ScrollView>(R.id.actionListScroll)
        val scrollBar = view.findViewById<ProgramTemplateMenuScrollBar>(R.id.actionListScrollBar)
        scrollBar.attachTo(scroll)
    }

    private fun updateActionListViewport(itemCount: Int) {
        val view = floatingView ?: return
        val scroll = view.findViewById<ScrollView>(R.id.actionListScroll)
        val scrollBar = view.findViewById<View>(R.id.actionListScrollBar)

        if (itemCount <= 0) {
            scroll.layoutParams = scroll.layoutParams.apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
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
        val panelY = (floatingParams?.y ?: 0).coerceAtLeast(0)
        val reservedHeight = (
            ACTION_LIST_RESERVED_HEIGHT_DP * density +
                ACTION_LIST_BOTTOM_MARGIN_DP * density +
                0.5f
            ).toInt()
        val maxByScreen = (screenHeight - panelY - reservedHeight)
            .coerceAtLeast((ACTION_LIST_MIN_HEIGHT_DP * density + 0.5f).toInt())
        val viewportHeight = maxRowsHeight.coerceAtMost(maxByScreen)

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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

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
                        val action = ActionStep(type = ActionStep.TYPE_TAP, x = x, y = y)
                        appendToSequence(action)
                        message = getString(R.string.action_added_tap, x, y)
                    }
                    PICKER_SWIPE_START -> {
                        pendingStartX = x
                        pendingStartY = y
                        hidePickerOverlay()
                        Toast.makeText(this, getString(R.string.swipe_start_set, x, y), Toast.LENGTH_SHORT).show()
                        showPickerOverlay(PICKER_SWIPE_END)
                        return@setOnTouchListener true
                    }
                    PICKER_SWIPE_END -> {
                        val action = ActionStep(
                            type = ActionStep.TYPE_SWIPE,
                            startX = pendingStartX,
                            startY = pendingStartY,
                            endX = x,
                            endY = y
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
        val wm = windowManager
        val view = pickerView ?: return
        try {
            wm?.removeView(view)
        } catch (_: Exception) {
        }
        pickerView = null
    }

    private fun togglePositionMarkers() {
        positionVisible = !positionVisible
        if (positionVisible) {
            val view = floatingView
            if (view != null) {
                view.findViewById<View>(R.id.saveConfirmPanel).visibility = View.GONE
                view.findViewById<View>(R.id.loopSettingsPanel).visibility = View.GONE
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
        screenWidthPx = resources.displayMetrics.widthPixels
        screenHeightPx = resources.displayMetrics.heightPixels

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
                    val x = action.x ?: continue
                    val y = action.y ?: continue
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
                    val sx = action.startX ?: continue
                    val sy = action.startY ?: continue
                    val ex = action.endX ?: continue
                    val ey = action.endY ?: continue

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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
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
        val updatedAction = when (binding.pointType) {
            MarkerPointType.TAP -> oldAction.copy(x = newX, y = newY)
            MarkerPointType.SWIPE_START -> oldAction.copy(startX = newX, startY = newY)
            MarkerPointType.SWIPE_END -> oldAction.copy(endX = newX, endY = newY)
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
        view.findViewById<View>(R.id.addMenu).visibility = View.GONE
        hideActionList()
        view.findViewById<View>(R.id.saveConfirmPanel).visibility = View.GONE
        view.findViewById<View>(R.id.loopSettingsPanel).visibility = View.GONE
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

        val editorWidth = (resources.displayMetrics.widthPixels * 0.9).toInt()
        val editorHeight = (resources.displayMetrics.heightPixels * 0.88).toInt()

        val params = WindowManager.LayoutParams(
            editorWidth,
            editorHeight,
            type,
            ProgramEditorWindowPolicy.FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = ProgramEditorWindowPolicy.SOFT_INPUT_MODE
        }

        val codeInput = editor.findViewById<EditText>(R.id.programCodeInput)
        configureProgramCodeInput(codeInput)
        bindProgramTextToolbar(editor, codeInput)
        val codeScrollBar = editor.findViewById<ProgramCodeScrollBar>(R.id.programCodeScrollBar)
        codeScrollBar.attachTo(codeInput)
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
            showProgramTemplateMenu(it, codeInput)
        }

        editor.findViewById<View>(R.id.programPickTapButton).setOnClickListener {
            startProgramPointAssist(codeInput) { x, y ->
                ProgramLuaAssist.tapSnippet(x, y)
            }
        }

        editor.findViewById<View>(R.id.programPickCoordButton).setOnClickListener {
            startProgramPointAssist(codeInput) { x, y ->
                ProgramLuaAssist.coordinateSnippet(x, y)
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
                    restoreProgramEditorWithSnippet(
                        ProgramLuaAssist.textAreaSnippet(area.left, area.top, area.right, area.bottom)
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
                    restoreProgramEditorWithSnippet(ProgramLuaAssist.colorSnippet(hex, x, y))
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

        editor.findViewById<View>(R.id.programCancelButton).setOnClickListener {
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

    private fun showProgramTemplateMenu(anchor: View, codeInput: EditText) {
        val popup = PopupWindow(this)
        val density = resources.displayMetrics.density
        val templates = ProgramLuaAssist.quickTemplates()
        val itemHeightPx = (44 * density + 0.5f).toInt()
        val popupPaddingPx = (8 * density + 0.5f).toInt()
        val itemHorizontalPaddingPx = (18 * density + 0.5f).toInt()
        val itemVerticalPaddingPx = (12 * density + 0.5f).toInt()
        val itemMinWidthPx = (220 * density + 0.5f).toInt()
        val menuHeight = ProgramTemplateMenuLayout.popupHeight(
            itemCount = templates.size,
            itemHeightPx = itemHeightPx,
            verticalPaddingPx = popupPaddingPx * 2,
            maxVisibleRows = 5
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(0xF2222222.toInt())
                setStroke((density + 0.5f).toInt(), 0x44FFFFFF)
                cornerRadius = 10f * density
            }
            setPadding(popupPaddingPx, popupPaddingPx, popupPaddingPx, popupPaddingPx)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (template in templates) {
            val item = TextView(this).apply {
                text = template.title
                textSize = 13f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                minWidth = itemMinWidthPx
                setPadding(
                    itemHorizontalPaddingPx,
                    itemVerticalPaddingPx,
                    itemHorizontalPaddingPx,
                    itemVerticalPaddingPx
                )
                minHeight = itemHeightPx
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    insertProgramSnippet(codeInput, template.snippet)
                    popup.dismiss()
                }
            }
            container.addView(item)
        }

        val scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                container,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val scrollBar = ProgramTemplateMenuScrollBar(this).apply {
            attachTo(scrollView)
        }
        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            scrollBar,
            LinearLayout.LayoutParams(
                (16 * density + 0.5f).toInt(),
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                leftMargin = (6 * density + 0.5f).toInt()
            }
        )

        popup.contentView = root
        popup.width = WindowManager.LayoutParams.WRAP_CONTENT
        popup.height = menuHeight
        popup.isOutsideTouchable = true
        popup.isFocusable = true
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.showAsDropDown(anchor)
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
                restoreProgramEditorWithSnippet(
                    ProgramLuaAssist.swipeSnippet(startX, startY, endX, endY)
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

        floatingView?.findViewById<View>(R.id.loopSettingsPanel)?.visibility = View.GONE

        val inflater = LayoutInflater.from(this)
        val picker = inflater.inflate(R.layout.timing_picker, null)
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
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        picker.findViewById<View>(R.id.settingsTitle).setOnTouchListener { _, event ->
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

        picker.findViewById<View>(R.id.settingsCancelButton).setOnClickListener {
            hidePickerOverlay()
            settingsActionIndex = -1
        }

        val condBtn = TextView(this).apply {
            text = getString(R.string.condition_button)
            setTextColor(0xFFFFC107.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (!saveCurrentSettingsInputs()) {
                    return@setOnClickListener
                }
                loadConditionEditState()
                hidePickerOverlay()
                showConditionPicker()
            }
        }
        (picker as LinearLayout).addView(condBtn)

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

        picker.findViewById<View>(R.id.conditionTitle).setOnTouchListener { _, event ->
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
                colorPosText.text = getString(R.string.condition_color_pos_text, x, y)
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
                textInput.hint = getString(R.string.condition_text_hint)
            }
            selectAreaBtn.visibility = if (isText) View.VISIBLE else View.GONE
            // Area range display (text type only, after area is set)
            if (isText && condEditUseArea && condEditLeft != null && condEditRight != null
                && condEditTop != null && condEditBottom != null) {
                areaRangeText.visibility = View.VISIBLE
                areaRangeText.text = getString(
                    R.string.condition_area_range,
                    condEditLeft!!, condEditTop!!, condEditRight!!, condEditBottom!!
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
            hidePickerOverlay()
            showAreaPicker()
        }

        colorPickBtn.setOnClickListener {
            if (!ScreenCaptureManager.isReady) {
                Toast.makeText(this, getString(R.string.screen_capture_not_ready), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            wm.removeView(picker)
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

        picker.findViewById<View>(R.id.condCancelBtn).setOnClickListener {
            hidePickerOverlay()
            settingsActionIndex = -1
        }

        wm.addView(picker, params)
    }

    private fun showAreaPicker(
        onAreaSelected: ((Rect) -> Unit)? = null,
        onCancelled: (() -> Unit)? = null
    ) {
        val wm = windowManager ?: return

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.area_picker, null)
        pickerView = view

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

        val selectionView = view.findViewById<AreaSelectionView>(R.id.areaSelectionView)
        val buttonsRow = view.findViewById<View>(R.id.areaPickerButtons)
        val saveBtn = view.findViewById<TextView>(R.id.areaPickerSaveBtn)
        val cancelBtn = view.findViewById<TextView>(R.id.areaPickerCancelBtn)

        // Restore previous area only when editing a normal condition. Program
        // assist should start with a fresh selection.
        if (onAreaSelected == null) {
            selectionView.setInitialRect(condEditLeft, condEditTop, condEditRight, condEditBottom)
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
                condEditLeft = screenLeft
                condEditTop = screenTop
                condEditRight = screenRight
                condEditBottom = screenBottom
                condEditUseArea = true
                areaRect = Rect(screenLeft, screenTop, screenRight, screenBottom)
                shouldPrefillText = condEditType == ActionStep.CONDITION_TEXT_CONTAINS
            }
            selectionView.cleanup()
            wm.removeView(view)
            pickerView = null

            if (onAreaSelected != null) {
                if (areaRect != null) {
                    onAreaSelected(areaRect)
                } else {
                    onCancelled?.invoke()
                }
                return@setOnClickListener
            }

            if (shouldPrefillText && areaRect != null) {
                prefillTextConditionFromArea(areaRect)
            } else {
                showConditionPicker()
            }
        }

        cancelBtn.setOnClickListener {
            selectionView.cleanup()
            wm.removeView(view)
            pickerView = null
            if (onCancelled != null) {
                onCancelled()
                return@setOnClickListener
            }
            showConditionPicker()
        }

        wm.addView(view, params)
    }

    private fun prefillTextConditionFromArea(area: Rect) {
        val service = ClickAccessibilityService.instance
        if (service == null || !ClickAccessibilityService.isRunning) {
            showConditionPicker()
            return
        }

        if (!ScreenCaptureManager.isReady) {
            condEditText = service.collectTextInArea(area)
            showConditionPicker()
            return
        }

        ScreenshotHider.hideAll()
        Thread {
            Thread.sleep(150)
            ScreenCaptureManager.refreshDisplayMetrics(this)
            val ocrBitmap = OcrHelper.captureAreaBitmap(
                area = area,
                screenWidth = ScreenCaptureManager.getCaptureWidth(),
                screenHeight = ScreenCaptureManager.getCaptureHeight()
            )
            android.os.Handler(mainLooper).post {
                try {
                    val accessibilityText = service.collectTextInArea(area)
                    condEditText = TextConditionDetector.prefillText(
                        ocrText = "",
                        accessibilityText = accessibilityText
                    )
                } finally {
                    ScreenshotHider.revealAll()
                    showConditionPicker()
                }
            }
            if (ocrBitmap == null) return@Thread

            val ocrText = try {
                OcrHelper.recognizeTextFromBitmap(ocrBitmap)
            } finally {
                ocrBitmap.recycle()
            }

            if (ocrText.isBlank()) return@Thread
            android.os.Handler(mainLooper).post {
                val textInput = pickerView?.findViewById<EditText>(R.id.conditionTextInput)
                val previousText = condEditText?.trim().orEmpty()
                val currentText = textInput?.text?.toString()?.trim().orEmpty()
                if (currentText.isEmpty() || currentText == previousText) {
                    condEditText = TextConditionDetector.prefillText(
                        ocrText = ocrText,
                        accessibilityText = previousText
                    )
                    textInput?.setText(condEditText.orEmpty())
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
                        condEditColorX = x
                        condEditColorY = y
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
        val view = floatingView ?: return
        val panel = view.findViewById<View>(R.id.loopSettingsPanel)
        if (panel.visibility == View.VISIBLE) {
            panel.visibility = View.GONE
            disableOverlayFocus()
        } else {
            view.findViewById<View>(R.id.addMenu).visibility = View.GONE
            hideActionList()
            view.findViewById<View>(R.id.saveConfirmPanel).visibility = View.GONE
            loadLoopSettings()
            panel.visibility = View.VISIBLE
            enableOverlayFocus()
        }
    }

    private fun saveLoopSettings() {
        val view = floatingView ?: return
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

        view.findViewById<View>(R.id.loopSettingsPanel).visibility = View.GONE
        disableOverlayFocus()
        Toast.makeText(this, getString(R.string.loop_settings_saved), Toast.LENGTH_SHORT).show()
    }

    private fun loadLoopSettings() {
        val savedCount = getLoopCount(this)
        val savedGap = getLoopGapMs(this)

        val view = floatingView ?: return
        view.findViewById<EditText>(R.id.loopCountInput).setText(savedCount.toString())
        view.findViewById<EditText>(R.id.loopGapInput).setText(savedGap.toString())
    }

    private fun updateLoopUI() {
        val view = floatingView ?: return
        val loopBtn = view.findViewById<TextView>(R.id.loopButton)
        loopBtn.text = getString(R.string.loop_button)
        loopBtn.setTextColor(Color.WHITE)
        view.findViewById<View>(R.id.loopSettingsPanel).visibility = View.GONE
        disableOverlayFocus()
    }

    private fun updateStartStopButtons(running: Boolean) {
        val view = floatingView ?: return
        val startBtn = view.findViewById<TextView>(R.id.startButton)
        val collapsedStartBtn = view.findViewById<TextView>(R.id.collapsedStartButton)

        if (running) {
            startBtn.text = getString(R.string.stop_action)
            startBtn.setTextColor(0xFFFF8888.toInt())
            collapsedStartBtn.text = getString(R.string.stop_action)
            collapsedStartBtn.setTextColor(0xFFFF8888.toInt())
        } else {
            startBtn.text = getString(R.string.start_action)
            startBtn.setTextColor(Color.WHITE)
            collapsedStartBtn.text = getString(R.string.start_action)
            collapsedStartBtn.setTextColor(Color.WHITE)
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
        private const val ACTION_LIST_MAX_VISIBLE_ROWS = 15
        private const val ACTION_LIST_MIN_HEIGHT_DP = 120f
        private const val ACTION_LIST_RESERVED_HEIGHT_DP = 128f
        private const val ACTION_LIST_BOTTOM_MARGIN_DP = 16f
    }
}
