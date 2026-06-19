package com.fffcccdfgh.androidclicker.feature.clicker.floating

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.fffcccdfgh.androidclicker.R
import com.fffcccdfgh.androidclicker.app.MainActivity
import com.fffcccdfgh.androidclicker.core.execution.ActionSequenceExecutor
import com.fffcccdfgh.androidclicker.core.execution.ActionStep
import com.fffcccdfgh.androidclicker.core.execution.ControlZoneChecker
import com.fffcccdfgh.androidclicker.core.execution.ExecutionOverlayWindowPolicy
import com.fffcccdfgh.androidclicker.core.execution.ExecutionStopButtonOverlay
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenshotHider
import java.util.LinkedHashMap

class RunFloatingControlService : Service() {
    private val stopDebugTag = "ClickerStopDebug"

    private class WindowEntry(
        val scriptName: String,
        val scriptActions: List<ActionStep>,
        val loopCount: Int,
        val loopGapMs: Long,
        var view: View?,
        var params: WindowManager.LayoutParams?,
        var initialX: Int = 0,
        var initialY: Int = 0,
        var initialTouchX: Float = 0f,
        var initialTouchY: Float = 0f
    )

    private val windows = LinkedHashMap<String, WindowEntry>()
    private var windowManager: WindowManager? = null
    private var foregroundStarted = false
    private var activeScriptName: String? = null
    private var runWindowsTouchThrough = false
    private var executionStopButton: ExecutionStopButtonOverlay? = null
    private var windowYOffset = 400

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_EXECUTION) {
            Log.d(stopDebugTag, "RunFloatingControlService notification stop clicked")
            stopActiveExecution()
            return START_NOT_STICKY
        }

        val json = intent?.getStringExtra(EXTRA_SCRIPT_JSON) ?: return START_NOT_STICKY
        val name = intent?.getStringExtra(EXTRA_SCRIPT_NAME) ?: return START_NOT_STICKY
        val loopCount = intent.getIntExtra(EXTRA_LOOP_COUNT, 1).coerceAtLeast(0)
        val loopGapMs = intent.getLongExtra(EXTRA_LOOP_GAP_MS, 0L).coerceAtLeast(0L)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        if (!foregroundStarted) {
            startForeground(NOTIFICATION_ID, buildNotification())
            foregroundStarted = true
        }

        if (windows.containsKey(name)) {
            // Already open — could bring to front, but for now ignore duplicate
            return START_STICKY
        }

        val actions = try {
            ActionStep.listFromJson(json)
        } catch (_: Exception) {
            emptyList()
        }

        val entry = WindowEntry(name, actions, loopCount, loopGapMs, null, null)
        windows[name] = entry
        showRunWindow(entry)
        updateNotification()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(stopDebugTag, "RunFloatingControlService.onDestroy")
        stopActiveExecution()
        hideExecutionStopButton()
        setAllRunWindowsTouchThrough(false)
        for (entry in windows.values.toList()) {
            removeWindowView(entry)
        }
        windows.clear()
    }

    // ── notification ────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.run_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, RunFloatingControlService::class.java).apply {
            action = ACTION_STOP_EXECUTION
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = getString(R.string.run_notification_title)
        val count = windows.size
        val text = if (count <= 1) getString(R.string.run_notification_text)
                   else "$count 个运行窗口"
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.notification_stop), pendingStop)
            .build()
    }

    private fun updateNotification() {
        if (!foregroundStarted) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    // ── window management ────────────────────────────────────────────

    @SuppressLint("InflateParams")
    private fun showRunWindow(entry: WindowEntry) {
        val wm = windowManager ?: return
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.floating_run_control, null)
        entry.view = view

        val y = windowYOffset
        windowYOffset += 120
        if (windowYOffset > 1000) windowYOffset = 400

        entry.params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            runWindowFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            this.y = y
            alpha = ExecutionOverlayWindowPolicy.NORMAL_ALPHA
        }

        val titleView = view.findViewById<TextView>(R.id.runDragHandle)
        titleView.text = entry.scriptName.take(3)

        // drag
        titleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    entry.initialX = entry.params?.x ?: 0
                    entry.initialY = entry.params?.y ?: 0
                    entry.initialTouchX = event.rawX
                    entry.initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    entry.params?.let { lp ->
                        lp.x = entry.initialX + (event.rawX - entry.initialTouchX).toInt()
                        lp.y = entry.initialY + (event.rawY - entry.initialTouchY).toInt()
                        wm.updateViewLayout(view, lp)
                    }
                    true
                }
                else -> false
            }
        }

        // start / stop
        bindStartStopTouch(entry, view.findViewById(R.id.runStartButton))

        // close
        view.findViewById<View>(R.id.runCloseButton).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (entry.scriptName == activeScriptName && ActionSequenceExecutor.isRunning) {
                        ActionSequenceExecutor.stop()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    closeWindow(entry)
                    true
                }
                else -> false
            }
        }

        wm.addView(view, entry.params)
        ControlZoneChecker.register(zoneKey(entry)) { getControlZoneRect(entry) }
        ScreenshotHider.register(zoneKey(entry),
            hide = {
                entry.view?.visibility = View.INVISIBLE
            },
            reveal = {
                entry.view?.visibility = View.VISIBLE
            }
        )
    }

    private fun bindStartStopTouch(entry: WindowEntry, button: View) {
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
                    } else if (ActionSequenceExecutor.isRunning) {
                        // Another script is running
                        Toast.makeText(this@RunFloatingControlService, R.string.another_script_running, Toast.LENGTH_SHORT).show()
                    } else {
                        executeRunSequence(entry)
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

    private fun closeWindow(entry: WindowEntry) {
        removeWindowView(entry)
        windows.remove(entry.scriptName)
        if (windows.isEmpty()) {
            foregroundStarted = false
            stopSelf()
        } else {
            updateNotification()
        }
    }

    private fun removeWindowView(entry: WindowEntry) {
        val wm = windowManager
        val view = entry.view
        if (view != null && wm != null) {
            try { wm.removeView(view) } catch (_: Exception) {}
        }
        ScreenshotHider.unregister(zoneKey(entry))
        entry.view = null
        entry.params = null
        ControlZoneChecker.unregister(zoneKey(entry))
    }

    private fun stopActiveExecution() {
        if (ActionSequenceExecutor.isRunning) {
            ActionSequenceExecutor.stop()
        }
        hideExecutionStopButton()
        setAllRunWindowsTouchThrough(false)
        activeScriptName = null
    }

    // ── control zone ─────────────────────────────────────────────────

    private fun zoneKey(entry: WindowEntry) = "run_${entry.scriptName}"

    private fun getControlZoneRect(entry: WindowEntry): Rect? {
        if (runWindowsTouchThrough) return null
        val view = entry.view ?: return null
        val params = entry.params ?: return null
        if (view.width <= 0 || view.height <= 0) return null
        return Rect(params.x, params.y, params.x + view.width, params.y + view.height)
    }

    private fun showExecutionStopButton() {
        val wm = windowManager ?: return
        if (executionStopButton == null) {
            executionStopButton = ExecutionStopButtonOverlay(
                context = this,
                windowManager = wm,
                zoneKey = "run_execution_stop_button",
                onStop = { stopActiveExecution() }
            )
        }
        executionStopButton?.show()
    }

    private fun hideExecutionStopButton() {
        executionStopButton?.hide()
    }

    private fun runWindowFlags(): Int {
        return if (runWindowsTouchThrough) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
    }

    private fun setAllRunWindowsTouchThrough(enabled: Boolean) {
        runWindowsTouchThrough = enabled
        for (entry in windows.values) {
            setRunWindowTouchThrough(entry)
        }
    }

    private fun setRunWindowTouchThrough(entry: WindowEntry) {
        val params = entry.params ?: return
        params.flags = runWindowFlags()
        params.alpha = if (runWindowsTouchThrough) {
            ExecutionOverlayWindowPolicy.TOUCH_THROUGH_ALPHA
        } else {
            ExecutionOverlayWindowPolicy.NORMAL_ALPHA
        }
        val view = entry.view ?: return
        if (view.isAttachedToWindow) {
            windowManager?.updateViewLayout(view, params)
        }
    }

    // ── execution ────────────────────────────────────────────────────

    private fun executeRunSequence(entry: WindowEntry) {
        ActionSequenceExecutor.loopCount = entry.loopCount
        ActionSequenceExecutor.loopEnabled = entry.loopCount != 1
        ActionSequenceExecutor.loopGapMs = entry.loopGapMs

        Log.d(stopDebugTag,
            "RunFloatingControlService.executeRunSequence script=${entry.scriptName} loopCount=${entry.loopCount} loopEnabled=${ActionSequenceExecutor.loopEnabled} loopGapMs=${ActionSequenceExecutor.loopGapMs}")

        ActionSequenceExecutor.onStarted = {
            Log.d(stopDebugTag, "RunFloatingControlService.onStarted script=${entry.scriptName}")
            activeScriptName = entry.scriptName
            setAllRunWindowsTouchThrough(true)
            showExecutionStopButton()
            updateWindowButton(entry, true)
            updateAllOtherButtons(entry.scriptName)
        }
        ActionSequenceExecutor.onFinished = {
            Log.d(stopDebugTag, "RunFloatingControlService.onFinished script=${entry.scriptName}")
            hideExecutionStopButton()
            setAllRunWindowsTouchThrough(false)
            activeScriptName = null
            updateWindowButton(entry, false)
            updateAllOtherButtons(null)
        }
        ActionSequenceExecutor.onStopped = {
            Log.d(stopDebugTag, "RunFloatingControlService.onStopped script=${entry.scriptName}")
            hideExecutionStopButton()
            setAllRunWindowsTouchThrough(false)
            activeScriptName = null
            updateWindowButton(entry, false)
            updateAllOtherButtons(null)
        }

        val density = resources.displayMetrics.density
        val paddingPx = ControlZoneChecker.dpToPx(density)

        ActionSequenceExecutor.execute(
            this,
            entry.scriptActions,
            canDispatchAction = { action ->
                !ControlZoneChecker.isActionInAnyZone(action, paddingPx)
            },
            onBlocked = {
                Toast.makeText(this, R.string.action_overlaps_control_stopped, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun updateWindowButton(entry: WindowEntry, running: Boolean) {
        val view = entry.view ?: return
        val btn = view.findViewById<TextView>(R.id.runStartButton)
        if (running) {
            btn.text = getString(R.string.stop_action)
            btn.setTextColor(Color.WHITE)
            btn.background = getDrawable(R.drawable.floating_pill_danger)
        } else {
            btn.text = getString(R.string.start_action)
            btn.setTextColor(Color.WHITE)
            btn.background = getDrawable(R.drawable.floating_pill_primary)
        }
    }

    private fun updateAllOtherButtons(activeName: String?) {
        for ((name, entry) in windows) {
            if (name != activeName) {
                updateWindowButton(entry, false)
            }
        }
    }

    // ── companion ────────────────────────────────────────────────────

    companion object {
        const val CHANNEL_ID = "run_floating_control_channel"
        const val NOTIFICATION_ID = 2
        const val ACTION_STOP_EXECUTION = "com.fffcccdfgh.androidclicker.STOP_EXECUTION"
        const val EXTRA_SCRIPT_JSON = "script_json"
        const val EXTRA_SCRIPT_NAME = "script_name"
        const val EXTRA_LOOP_COUNT = "loop_count"
        const val EXTRA_LOOP_GAP_MS = "loop_gap_ms"
    }
}
