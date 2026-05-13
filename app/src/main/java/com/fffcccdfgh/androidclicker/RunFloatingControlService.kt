package com.fffcccdfgh.androidclicker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast

class RunFloatingControlService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var floatingParams: WindowManager.LayoutParams? = null
    private var scriptActions: List<ActionStep> = emptyList()
    private var scriptName: String = ""
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val json = intent?.getStringExtra(EXTRA_SCRIPT_JSON)
        if (json != null) {
            scriptActions = try {
                ActionStep.listFromJson(json)
            } catch (_: Exception) {
                emptyList()
            }
        }
        scriptName = intent?.getStringExtra(EXTRA_SCRIPT_NAME) ?: scriptName

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        if (floatingView == null) {
            showFloatingControl()
        } else {
            updateTitle()
        }

        return START_STICKY
    }

    private fun updateTitle() {
        val view = floatingView ?: return
        val label = view.findViewById<TextView>(R.id.runDragHandle)
        label.text = scriptName.take(3)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        hideFloatingControl()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.run_notification_channel),
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
            .setContentTitle(getString(R.string.run_notification_title))
            .setContentText(getString(R.string.run_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("InflateParams")
    private fun showFloatingControl() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.floating_run_control, null)
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
            y = 400
        }

        val titleView = view.findViewById<TextView>(R.id.runDragHandle)
        titleView.text = scriptName.take(3)

        titleView.setOnTouchListener { _, event ->
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

        view.findViewById<View>(R.id.runStartButton).setOnClickListener {
            if (scriptActions.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_action_set), Toast.LENGTH_SHORT).show()
            } else {
                ActionSequenceExecutor.execute(this, scriptActions)
            }
        }

        view.findViewById<View>(R.id.runCloseButton).setOnClickListener {
            stopSelf()
            Toast.makeText(this, getString(R.string.run_stopped), Toast.LENGTH_SHORT).show()
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

    companion object {
        const val CHANNEL_ID = "run_floating_control_channel"
        const val NOTIFICATION_ID = 2
        const val EXTRA_SCRIPT_JSON = "script_json"
        const val EXTRA_SCRIPT_NAME = "script_name"
    }
}
