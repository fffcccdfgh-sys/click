package com.fffcccdfgh.androidclicker

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log

object PaddleOcrHelper {
    private const val TAG = "PaddleOcrHelper"

    private val lock = Any()
    private var engine: PaddleOcrNative? = null
    private var disabledReason: String? = null

    sealed class Result {
        data class Success(val text: String, val elapsedMs: Long) : Result()
        data class Unavailable(val reason: String) : Result()
    }

    fun recognizeTextFromBitmap(context: Context?, bitmap: Bitmap): Result {
        val appContext = context?.applicationContext
            ?: return Result.Unavailable("context is not set")

        return synchronized(lock) {
            val disabled = disabledReason
            if (disabled != null) {
                return@synchronized Result.Unavailable(disabled)
            }

            val activeEngine = try {
                getOrCreateEngine(appContext)
            } catch (e: Throwable) {
                val reason = "init failed: ${e.javaClass.simpleName}: ${e.message ?: "no message"}"
                disabledReason = reason
                Log.w(TAG, "PaddleOCR unavailable: $reason", e)
                return@synchronized Result.Unavailable(reason)
            }

            val startedAt = SystemClock.uptimeMillis()
            try {
                val text = activeEngine.recognize(bitmap).trim()
                val elapsedMs = SystemClock.uptimeMillis() - startedAt
                if (OcrDebugConfig.VERBOSE_LOGS) {
                    Log.d(
                        TAG,
                        "PaddleOCR recognize bitmap=${bitmap.width}x${bitmap.height} " +
                            "recognizedLength=${text.length} elapsed=${elapsedMs}ms"
                    )
                }
                Result.Success(text, elapsedMs)
            } catch (e: Throwable) {
                val reason = "recognize failed: ${e.javaClass.simpleName}: ${e.message ?: "no message"}"
                Log.w(TAG, "PaddleOCR recognize failed", e)
                Result.Unavailable(reason)
            }
        }
    }

    private fun getOrCreateEngine(context: Context): PaddleOcrNative {
        val existing = engine
        if (existing != null) return existing

        val startedAt = SystemClock.uptimeMillis()
        Log.d(TAG, "PaddleOCR init start")
        val created = PaddleOcrNative.create(context)
        engine = created
        Log.d(TAG, "PaddleOCR init success elapsed=${SystemClock.uptimeMillis() - startedAt}ms")
        return created
    }
}
