package com.fffcccdfgh.androidclicker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class FloatingControlService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var floatingParams: WindowManager.LayoutParams? = null
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

    private enum class MarkerPointType { TAP, SWIPE_START, SWIPE_END, WAIT }
    private data class MarkerBinding(val actionIndex: Int, val pointType: MarkerPointType)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        hidePositionMarkers()
        hidePickerOverlay()
        hideFloatingControl()
        isRunning = false
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
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.floating_notification_title))
            .setContentText(getString(R.string.floating_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("InflateParams")
    private fun showFloatingControl() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.floating_control, null)
        floatingView = view

        val wm = windowManager ?: return

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
                    }
                    true
                }
                else -> false
            }
        }

        view.findViewById<View>(R.id.startButton).setOnClickListener {
            hideAllBeforeRun()
            executeSequence()
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

        view.findViewById<View>(R.id.clearOption).setOnClickListener {
            addMenu.visibility = View.GONE
            clearSequence()
            Toast.makeText(this, getString(R.string.sequence_cleared), Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.saveButton).setOnClickListener {
            val sequence = loadSequence()
            if (sequence.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_action_to_save), Toast.LENGTH_SHORT).show()
            } else {
                hideActionList()
                view.findViewById<View>(R.id.addMenu).visibility = View.GONE
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
            ScriptStorage.saveNamedScript(this, name, sequence)
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

        view.findViewById<View>(R.id.closeButton).setOnClickListener {
            stopSelf()
            Toast.makeText(this, getString(R.string.floating_stopped), Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.foldButton).setOnClickListener {
            collapseFloatingControl()
        }

        // Collapsed mode controls
        view.findViewById<View>(R.id.collapsedStartButton).setOnClickListener {
            hideAllBeforeRun()
            executeSequence()
        }

        view.findViewById<View>(R.id.collapsedCloseButton).setOnClickListener {
            stopSelf()
            Toast.makeText(this, getString(R.string.floating_stopped), Toast.LENGTH_SHORT).show()
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

        updateFloatingTitle()

        wm.addView(view, floatingParams)
    }

    private fun enableOverlayFocus() {
        floatingParams?.let {
            it.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            windowManager?.updateViewLayout(floatingView, it)
        }
    }

    private fun disableOverlayFocus() {
        floatingParams?.let {
            it.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager?.updateViewLayout(floatingView, it)
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
        val title = if (name.isNullOrBlank()) getString(R.string.floating_control_label) else name
        view.findViewById<TextView>(R.id.dragHandle).text = title
        view.findViewById<TextView>(R.id.collapsedTitle).text = title
    }

    private fun hideFloatingControl() {
        val view = floatingView
        val wm = windowManager
        if (view != null && wm != null) {
            try {
                wm.removeView(view)
            } catch (_: Exception) {
            }
        }
        floatingView = null
        floatingParams = null
    }

    private fun toggleAddMenu() {
        val view = floatingView ?: return
        val menu = view.findViewById<View>(R.id.addMenu)
        val opening = menu.visibility != View.VISIBLE
        if (opening) {
            hideActionList()
            view.findViewById<View>(R.id.saveConfirmPanel).visibility = View.GONE
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

            val desc = TextView(this).apply {
                text = when (action.type) {
                    ActionStep.TYPE_TAP -> getString(R.string.action_list_tap_short, i + 1, action.x!!, action.y!!)
                    ActionStep.TYPE_SWIPE -> getString(
                        R.string.action_list_swipe_short,
                        i + 1, action.startX!!, action.startY!!, action.endX!!, action.endY!!
                    )
                    ActionStep.TYPE_WAIT -> getString(
                        R.string.action_list_wait_short,
                        i + 1, (action.durationMs ?: 0L) / 1000.0
                    )
                    else -> ""
                }
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
    }

    private fun deleteActionAt(index: Int) {
        val sequence = loadSequence().toMutableList()
        if (index < 0 || index >= sequence.size) return
        sequence.removeAt(index)
        saveSequence(sequence)
        renderActionList()
        refreshMarkers()
    }

    private fun showPickerOverlay(mode: Int) {
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

    private fun hideExpandedPanels() {
        val view = floatingView ?: return
        view.findViewById<View>(R.id.addMenu).visibility = View.GONE
        hideActionList()
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
            ActionStep.TYPE_WAIT to getString(R.string.wait_action)
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
                    }
                }
            }
            container.addView(option)
        }

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
        val sequence = loadSequence()
        ActionSequenceExecutor.execute(this, sequence)
    }

    private fun showActionSettings(actionIndex: Int) {
        val wm = windowManager ?: return
        if (pickerView != null) hidePickerOverlay()

        val sequence = loadSequence()
        if (actionIndex < 0 || actionIndex >= sequence.size) return
        val action = sequence[actionIndex]
        settingsActionIndex = actionIndex

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

        val durationInput = picker.findViewById<EditText>(R.id.durationInput)
        val delayBeforeInput = picker.findViewById<EditText>(R.id.delayBeforeInput)
        val stepGapInput = picker.findViewById<EditText>(R.id.stepGapInput)

        durationInput.setText((action.durationMs ?: 1L).toString())
        delayBeforeInput.setText((action.delayBeforeMs ?: 1L).toString())
        stepGapInput.setText((action.stepGapMs ?: 1L).toString())

        picker.findViewById<View>(R.id.settingsSaveButton).setOnClickListener {
            val durationMs = parseMsInput(durationInput.text.toString())
            val delayBeforeMs = parseMsInput(delayBeforeInput.text.toString())
            val stepGapMs = parseMsInput(stepGapInput.text.toString())

            val updatedSequence = loadSequence().toMutableList()
            if (settingsActionIndex in updatedSequence.indices) {
                val oldAction = updatedSequence[settingsActionIndex]
                updatedSequence[settingsActionIndex] = oldAction.copy(
                    durationMs = durationMs,
                    delayBeforeMs = delayBeforeMs,
                    stepGapMs = stepGapMs
                )
                saveSequence(updatedSequence)
                if (actionListVisible) renderActionList()
                refreshMarkers()
            }
            hidePickerOverlay()
            settingsActionIndex = -1
        }

        picker.findViewById<View>(R.id.settingsCancelButton).setOnClickListener {
            hidePickerOverlay()
            settingsActionIndex = -1
        }

        wm.addView(picker, params)
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
        var isRunning = false
            private set

        const val PREFS_NAME = "tap_config"
        const val KEY_ACTION_SEQUENCE = "action_sequence"
        const val KEY_EDITING_SCRIPT_NAME = "current_editing_script_name"

        private const val PICKER_TAP_POINT = 0
        private const val PICKER_SWIPE_START = 1
        private const val PICKER_SWIPE_END = 2
        private const val DRAG_THRESHOLD_DP = 12f
    }
}
