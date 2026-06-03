package com.fffcccdfgh.androidclicker

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optical Character Recognition helper using ML Kit Chinese recognizer.
 */
object OcrHelper {
    private const val TAG = "OcrHelper"
    private const val WARM_UP_BITMAP_SIZE = 32

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val warmUpStarted = AtomicBoolean(false)

    /**
     * Run OCR on the current screen and search for [targetText] within [area].
     * Returns true if the text is found (case-sensitive partial match).
     */
    fun detectText(
        targetText: String,
        area: Rect?,
        screenWidth: Int,
        screenHeight: Int,
        filterMode: String? = null
    ): Boolean {
        if (targetText.isEmpty()) return true
        return recognizeText(
            area = area,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            filterMode = filterMode
        ).contains(targetText)
    }

    fun recognizeText(
        area: Rect?,
        screenWidth: Int,
        screenHeight: Int,
        captureTimeoutMs: Long = OcrTimingPolicy.DEFAULT_CAPTURE_TIMEOUT_MS,
        filterMode: String? = null
    ): String {
        val bitmap = captureAreaBitmap(area, screenWidth, screenHeight, captureTimeoutMs) ?: return ""
        return try {
            recognizeTextFromBitmap(bitmap, filterMode)
        } finally {
            bitmap.recycle()
        }
    }

    fun captureAreaBitmap(
        area: Rect?,
        screenWidth: Int,
        screenHeight: Int,
        timeoutMs: Long = OcrTimingPolicy.DEFAULT_CAPTURE_TIMEOUT_MS
    ): Bitmap? {
        if (!ScreenCaptureManager.isReady) {
            Log.d(TAG, "OCR skipped: screen capture is not ready")
            return null
        }

        val image = ScreenCaptureManager.captureFrameSync(timeoutMs) ?: run {
            Log.d(TAG, "OCR skipped: failed to capture a frame")
            return null
        }
        return try {
            val captureArea = mapCaptureArea(
                area = area,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                captureWidth = image.width,
                captureHeight = image.height
            ) ?: return null

            if (
                captureArea.left == 0 &&
                captureArea.top == 0 &&
                captureArea.right == image.width &&
                captureArea.bottom == image.height
            ) {
                ScreenCaptureManager.imageToBitmap(image)
            } else {
                OcrImageCropper.cropImageAreaToBitmap(image, captureArea)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to crop OCR bitmap", e)
            null
        } finally {
            image.close()
        }
    }

    fun recognizeTextFromBitmap(bitmap: Bitmap, filterMode: String? = null): String {
        val filteredBitmap = OcrBitmapFilter.apply(bitmap, filterMode)
        return try {
            processBitmapText(filteredBitmap) { result ->
                result.text.trim()
            } ?: ""
        } finally {
            if (filteredBitmap !== bitmap) {
                filteredBitmap.recycle()
            }
        }
    }

    fun warmUpAsync() {
        if (!warmUpStarted.compareAndSet(false, true)) return
        Thread {
            val startedAt = SystemClock.uptimeMillis()
            val bitmap = Bitmap.createBitmap(
                WARM_UP_BITMAP_SIZE,
                WARM_UP_BITMAP_SIZE,
                Bitmap.Config.ARGB_8888
            )
            try {
                processBitmapText(bitmap, OcrTimingPolicy.WARM_UP_RECOGNITION_TIMEOUT_MS) { Unit }
                Log.d(TAG, "OCR warm-up finished in ${SystemClock.uptimeMillis() - startedAt}ms")
            } catch (e: Exception) {
                Log.w(TAG, "OCR warm-up failed", e)
            } finally {
                bitmap.recycle()
            }
        }.start()
    }

    private fun <T> processBitmapText(
        bitmap: Bitmap,
        timeoutMs: Long = 5000L,
        handleResult: (Text) -> T
    ): T? {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val result = Tasks.await(recognizer.process(inputImage), timeoutMs, TimeUnit.MILLISECONDS)
            if (result == null) return null

            handleResult(result)
        } catch (e: Exception) {
            Log.w(TAG, "OCR failed", e)
            null
        }
    }

    private fun mapCaptureArea(
        area: Rect?,
        screenWidth: Int,
        screenHeight: Int,
        captureWidth: Int,
        captureHeight: Int
    ): OcrAreaMapper.Area? {
        if (area == null) {
            return OcrAreaMapper.Area(0, 0, captureWidth, captureHeight)
        }
        return OcrAreaMapper.mapScreenAreaToCaptureArea(
            left = area.left,
            top = area.top,
            right = area.right,
            bottom = area.bottom,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            captureWidth = captureWidth,
            captureHeight = captureHeight
        )
    }

}
