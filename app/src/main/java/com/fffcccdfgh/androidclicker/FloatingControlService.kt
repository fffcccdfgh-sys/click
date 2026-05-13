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

    companion object {
        const val CHANNEL_ID = "floating_control_channel"
        const val NOTIFICATION_ID = 1
        var isRunning = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
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

        layoutParams = WindowManager.LayoutParams(
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
                    initialX = layoutParams?.x ?: 0
                    initialY = layoutParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams?.let { lp ->
                        lp.x = initialX + (event.rawX - initialTouchX).toInt()
                        lp.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(view, lp)
                    }
                    true
                }
                else -> false
            }
        }

        view.findViewById<View>(R.id.closeButton).setOnClickListener {
            stopSelf()
            Toast.makeText(this, getString(R.string.floating_stopped), Toast.LENGTH_SHORT).show()
        }

        wm.addView(view, layoutParams)
    }

    private fun hideFloatingControl() {
        val view = floatingView ?: return
        val wm = windowManager ?: return
        try {
            wm.removeView(view)
        } catch (_: Exception) {
        }
        floatingView = null
        windowManager = null
    }
}
