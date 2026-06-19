package com.fffcccdfgh.androidclicker.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object OcrDebugImageSaver {
    private const val TAG = "OcrDebugImageSaver"
    private const val DEBUG_DIR_NAME = "ocr-debug"

    fun savePrefillFailureCrop(
        context: Context,
        bitmap: Bitmap
    ): File? {
        return try {
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: context.filesDir
            val debugDir = File(baseDir, DEBUG_DIR_NAME)
            if (!debugDir.exists() && !debugDir.mkdirs()) {
                Log.w(TAG, "Failed to create OCR debug directory: ${debugDir.absolutePath}")
                return null
            }

            val file = File(
                debugDir,
                OcrDebugImagePolicy.cropFileName(
                    timestampMs = SystemClock.uptimeMillis(),
                    width = bitmap.width,
                    height = bitmap.height
                )
            )
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.i(TAG, "Saved OCR debug crop: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save OCR debug crop", e)
            null
        }
    }
}
