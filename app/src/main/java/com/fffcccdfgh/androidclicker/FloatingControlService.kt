package com.fffcccdfgh.androidclicker

import android.accessibilityservice.GestureDescription
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingControl()
        isRunning = true

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
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
            executeSequence()
        }

        view.findViewById<View>(R.id.addButton).setOnClickListener {
            toggleAddMenu()
        }

        view.findViewById<View>(R.id.listButton).setOnClickListener {
            toggleActionList()
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

        view.findViewById<View>(R.id.clearOption).setOnClickListener {
            addMenu.visibility = View.GONE
            clearSequence()
            Toast.makeText(this, getString(R.string.sequence_cleared), Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.listAddTap).setOnClickListener {
            showPickerOverlay(PICKER_TAP_POINT)
        }

        view.findViewById<View>(R.id.listAddSwipe).setOnClickListener {
            showPickerOverlay(PICKER_SWIPE_START)
        }

        view.findViewById<View>(R.id.closeButton).setOnClickListener {
            stopSelf()
            Toast.makeText(this, getString(R.string.floating_stopped), Toast.LENGTH_SHORT).show()
        }

        wm.addView(view, floatingParams)
    }

    private fun hideFloatingControl() {
        val view = floatingView ?: return
        val wm = windowManager ?: return
        try {
            wm.removeView(view)
        } catch (_: Exception) {
        }
        floatingView = null
        floatingParams = null
        windowManager = null
    }

    private fun toggleAddMenu() {
        val view = floatingView ?: return
        val menu = view.findViewById<View>(R.id.addMenu)
        menu.visibility = if (menu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun toggleActionList() {
        if (actionListVisible) {
            hideActionList()
        } else {
            val view = floatingView ?: return
            view.findViewById<View>(R.id.addMenu).visibility = View.GONE
            showActionList()
        }
    }

    private fun showActionList() {
        val view = floatingView ?: return
        val panel = view.findViewById<View>(R.id.actionListPanel)
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
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, 4, 0, 4)
                gravity = Gravity.CENTER_VERTICAL
            }

            val desc = TextView(this).apply {
                text = when (action.type) {
                    ActionStep.TYPE_TAP -> getString(R.string.action_list_tap_short, i + 1, action.x!!, action.y!!)
                    ActionStep.TYPE_SWIPE -> getString(
                        R.string.action_list_swipe_short,
                        i + 1, action.startX!!, action.startY!!, action.endX!!, action.endY!!
                    )
                    else -> ""
                }
                setTextColor(Color.WHITE)
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val stepIndex = i
            val deleteBtn = TextView(this).apply {
                text = getString(R.string.delete_action)
                setTextColor(0xFFFF8888.toInt())
                textSize = 12f
                setPadding(12, 4, 4, 4)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    deleteActionAt(stepIndex)
                }
            }

            row.addView(desc)
            row.addView(deleteBtn)
            container.addView(row)
        }
    }

    private fun deleteActionAt(index: Int) {
        val sequence = loadSequence().toMutableList()
        if (index < 0 || index >= sequence.size) return
        sequence.removeAt(index)
        saveSequence(sequence)
        renderActionList()
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
    }

    private fun appendToSequence(action: ActionStep) {
        val sequence = loadSequence().toMutableList()
        sequence.add(action)
        saveSequence(sequence)
        if (actionListVisible) {
            renderActionList()
        }
    }

    private fun executeSequence() {
        val sequence = loadSequence()
        if (sequence.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_action_set), Toast.LENGTH_SHORT).show()
            return
        }

        val service = ClickAccessibilityService.instance
        if (service == null || !ClickAccessibilityService.isRunning) {
            Toast.makeText(this, getString(R.string.tap_service_disabled), Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, getString(R.string.sequence_executing_step, 1, sequence.size), Toast.LENGTH_SHORT).show()
        executeStep(sequence, 0, service)
    }

    private fun executeStep(sequence: List<ActionStep>, index: Int, service: ClickAccessibilityService) {
        if (index >= sequence.size) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, getString(R.string.sequence_done), Toast.LENGTH_SHORT).show()
            }
            return
        }

        val action = sequence[index]
        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                scheduleNextStep(sequence, index + 1, service)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                scheduleNextStep(sequence, index + 1, service)
            }
        }

        val dispatched = when (action.type) {
            ActionStep.TYPE_TAP -> service.performTap(action.x!!, action.y!!, callback)
            ActionStep.TYPE_SWIPE -> service.performSwipe(
                action.startX!!, action.startY!!, action.endX!!, action.endY!!, 300, callback
            )
            else -> false
        }

        if (!dispatched) {
            scheduleNextStep(sequence, index + 1, service)
        }
    }

    private fun scheduleNextStep(sequence: List<ActionStep>, nextIndex: Int, service: ClickAccessibilityService) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (nextIndex < sequence.size) {
                Toast.makeText(this, getString(R.string.sequence_executing_step, nextIndex + 1, sequence.size), Toast.LENGTH_SHORT).show()
            }
            executeStep(sequence, nextIndex, service)
        }, STEP_GAP_MS)
    }

    companion object {
        const val CHANNEL_ID = "floating_control_channel"
        const val NOTIFICATION_ID = 1
        var isRunning = false
            private set

        const val PREFS_NAME = "tap_config"
        const val KEY_ACTION_SEQUENCE = "action_sequence"
        private const val STEP_GAP_MS = 500L

        private const val PICKER_TAP_POINT = 0
        private const val PICKER_SWIPE_START = 1
        private const val PICKER_SWIPE_END = 2
    }
}
