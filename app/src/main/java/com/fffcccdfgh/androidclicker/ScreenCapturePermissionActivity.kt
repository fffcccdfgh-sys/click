package com.fffcccdfgh.androidclicker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast

class ScreenCapturePermissionActivity : Activity() {
    companion object {
        private const val TAG = "ScreenCapPerm"
        private const val REQUEST_CODE = 1
    }

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
                ScreenCaptureForegroundService.start(this, resultCode, data)
                Log.d(TAG, "Permission granted, starting foreground capture service")
            } else {
                Log.d(TAG, "Permission denied or cancelled")
            }
        }
        finish()
    }
}
