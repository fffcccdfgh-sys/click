package com.fffcccdfgh.androidclicker

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast

class ScreenCaptureForegroundService : Service() {
    companion object {
        private const val TAG = "ScreenCaptureFgSvc"
        private const val ACTION_START_CAPTURE = "com.fffcccdfgh.androidclicker.START_SCREEN_CAPTURE"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "screen_capture_service"
        private const val NOTIFICATION_ID = 3001

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, ScreenCaptureForegroundService::class.java).apply {
                action = ACTION_START_CAPTURE
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenCaptureForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START_CAPTURE) {
            Log.d(TAG, "Ignoring service start without capture permission data")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = getResultData(intent)
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            Log.d(TAG, "Stopping service: missing screen capture permission data")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (!startScreenCaptureForegroundSafely()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            ScreenCaptureManager.initialize(this, resultCode, resultData)
            Log.d(TAG, "Screen capture initialized")
            OcrHelper.debugContext = this
            OcrHelper.warmUpAsync()
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to initialize screen capture", e)
            Toast.makeText(this, R.string.screen_capture_not_supported, Toast.LENGTH_SHORT).show()
            stopSelf(startId)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected screen capture initialization failure", e)
            Toast.makeText(this, R.string.screen_capture_not_supported, Toast.LENGTH_SHORT).show()
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    private fun startScreenCaptureForegroundSafely(): Boolean {
        return try {
            startScreenCaptureForeground()
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to start screen capture foreground service", e)
            Toast.makeText(this, R.string.screen_capture_not_supported, Toast.LENGTH_SHORT).show()
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected foreground service start failure", e)
            Toast.makeText(this, R.string.screen_capture_not_supported, Toast.LENGTH_SHORT).show()
            false
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        ScreenCaptureManager.release()
    }

    private fun getResultData(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
    }

    private fun startScreenCaptureForeground() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.screen_capture_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(getString(R.string.screen_capture_notification_title))
            .setContentText(getString(R.string.screen_capture_notification_text))
            .setOngoing(true)
            .build()
    }
}
