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
import android.widget.Toast

class FloatingControlService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var floatingParams: WindowManager.LayoutParams? = null
    private var pickerView: View? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

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

        view.findViewById<View>(R.id.pickPointButton).setOnClickListener {
            showPickerOverlay()
        }

        view.findViewById<View>(R.id.tapButton).setOnClickListener {
            executeConfiguredTap()
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

    private fun showPickerOverlay() {
        val wm = windowManager ?: return
        if (pickerView != null) return

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

        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_TAP_X, x)
                    .putInt(KEY_TAP_Y, y)
                    .apply()
                hidePickerOverlay()
                Toast.makeText(this, getString(R.string.pick_point_set, x, y), Toast.LENGTH_SHORT).show()
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

    private fun executeConfiguredTap() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val x = prefs.getInt(KEY_TAP_X, NO_COORDINATE)
        val y = prefs.getInt(KEY_TAP_Y, NO_COORDINATE)

        if (x == NO_COORDINATE || y == NO_COORDINATE) {
            Toast.makeText(this, getString(R.string.tap_no_coordinate), Toast.LENGTH_SHORT).show()
            return
        }

        val service = ClickAccessibilityService.instance
        if (service == null || !ClickAccessibilityService.isRunning) {
            Toast.makeText(this, getString(R.string.tap_service_disabled), Toast.LENGTH_SHORT).show()
            return
        }

        val success = service.performTap(x, y)
        if (success) {
            Toast.makeText(this, getString(R.string.tap_executed, x, y), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.tap_error), Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val CHANNEL_ID = "floating_control_channel"
        const val NOTIFICATION_ID = 1
        var isRunning = false
            private set
        private const val PREFS_NAME = "tap_config"
        const val KEY_TAP_X = "tap_x"
        const val KEY_TAP_Y = "tap_y"
        const val NO_COORDINATE = -1
    }
}
