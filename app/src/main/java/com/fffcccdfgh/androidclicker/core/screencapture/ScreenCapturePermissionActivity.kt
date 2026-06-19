package com.fffcccdfgh.androidclicker.core.screencapture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.fffcccdfgh.androidclicker.R

class ScreenCapturePermissionActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingStartCaptureService: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CODE)
        } catch (_: Exception) {
            Log.d(TAG, "Failed to start screen capture intent")
            Toast.makeText(this, R.string.screen_capture_not_supported, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                startCaptureServiceAndFinish(resultCode, data)
                return
            } else {
                Log.d(TAG, "Permission denied or cancelled")
            }
        }
        finish()
    }

    private fun startCaptureServiceAndFinish(resultCode: Int, data: Intent) {
        val startRunnable = Runnable {
            try {
                ScreenCaptureForegroundService.start(this, resultCode, data)
                Log.d(TAG, "Permission granted, starting foreground capture service")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground capture service", e)
                Toast.makeText(this, R.string.screen_capture_not_supported, Toast.LENGTH_SHORT).show()
            } finally {
                finish()
            }
        }
        pendingStartCaptureService = startRunnable
        mainHandler.postDelayed(startRunnable, SERVICE_START_DELAY_MS)
    }

    override fun onDestroy() {
        pendingStartCaptureService?.let { mainHandler.removeCallbacks(it) }
        pendingStartCaptureService = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScreenCapPerm"
        private const val REQUEST_CODE = 1
        private const val SERVICE_START_DELAY_MS = 150L
    }
}
