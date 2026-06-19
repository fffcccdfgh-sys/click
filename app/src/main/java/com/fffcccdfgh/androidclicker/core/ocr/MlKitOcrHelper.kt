package com.fffcccdfgh.androidclicker.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.TimeUnit

object MlKitOcrHelper {
    private const val TAG = "MlKitOcrHelper"
    private const val RECOGNIZE_TIMEOUT_MS = 5_000L

    private val lock = Any()
    private var recognizer: TextRecognizer? = null
    private var disabledReason: String? = null

    sealed class Result {
        data class Success(val text: String, val elapsedMs: Long) : Result()
        data class Unavailable(val reason: String) : Result()
    }

    fun recognizeTextFromBitmap(context: Context?, bitmap: Bitmap): Result {
        context?.applicationContext
            ?: return Result.Unavailable("context is not set")

        val disabled = disabledReason
        if (disabled != null) {
            return Result.Unavailable(disabled)
        }

        val activeRecognizer = try {
            getOrCreateRecognizer()
        } catch (e: Throwable) {
            val reason = "init failed: ${e.javaClass.simpleName}: ${e.message ?: "no message"}"
            disabledReason = reason
            Log.w(TAG, "ML Kit OCR unavailable: $reason", e)
            return Result.Unavailable(reason)
        }

        val startedAt = SystemClock.uptimeMillis()
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val text = Tasks.await(
                activeRecognizer.process(image),
                RECOGNIZE_TIMEOUT_MS,
                TimeUnit.MILLISECONDS
            ).text.trim()
            val elapsedMs = SystemClock.uptimeMillis() - startedAt
            Log.d(
                TAG,
                "ML Kit OCR recognize bitmap=${bitmap.width}x${bitmap.height} " +
                    "recognizedLength=${text.length} elapsed=${elapsedMs}ms"
            )
            Result.Success(text, elapsedMs)
        } catch (e: Throwable) {
            val reason = "recognize failed: ${e.javaClass.simpleName}: ${e.message ?: "no message"}"
            Log.w(TAG, "ML Kit OCR recognize failed: $reason", e)
            Result.Unavailable(reason)
        }
    }

    private fun getOrCreateRecognizer(): TextRecognizer {
        synchronized(lock) {
            val existing = recognizer
            if (existing != null) return existing

            val startedAt = SystemClock.uptimeMillis()
            Log.d(TAG, "ML Kit OCR init start")
            val created = TextRecognition.getClient(
                ChineseTextRecognizerOptions.Builder().build()
            )
            recognizer = created
            Log.d(TAG, "ML Kit OCR init success elapsed=${SystemClock.uptimeMillis() - startedAt}ms")
            return created
        }
    }
}
