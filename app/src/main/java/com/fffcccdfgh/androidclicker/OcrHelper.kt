package com.fffcccdfgh.androidclicker

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.TimeUnit

/**
 * Optical Character Recognition helper using ML Kit Chinese recognizer.
 */
object OcrHelper {
    private const val TAG = "OcrHelper"

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /**
     * Run OCR on the current screen and search for [targetText] within [area].
     * Returns true if the text is found (case-sensitive partial match).
     */
    fun detectText(
        targetText: String,
        area: Rect?,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (targetText.isEmpty()) return true
        return recognizeText(
            area = area,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        ).contains(targetText)
    }

    fun recognizeText(
        area: Rect?,
        screenWidth: Int,
        screenHeight: Int
    ): String {
        val bitmap = captureAreaBitmap(area, screenWidth, screenHeight) ?: return ""
        return try {
            recognizeTextFromBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    fun captureAreaBitmap(
        area: Rect?,
        screenWidth: Int,
        screenHeight: Int
    ): Bitmap? {
        if (!ScreenCaptureManager.isReady) {
            Log.d(TAG, "OCR skipped: screen capture is not ready")
            return null
        }

        val image = ScreenCaptureManager.captureFrameSync(3000L) ?: run {
            Log.d(TAG, "OCR skipped: failed to capture a frame")
            return null
        }
        var sourceBitmap: Bitmap? = null
        return try {
            sourceBitmap = ScreenCaptureManager.imageToBitmap(image)
            val capturedBitmap = sourceBitmap ?: return null
            val captureArea = mapCaptureArea(
                area = area,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                captureWidth = capturedBitmap.width,
                captureHeight = capturedBitmap.height
            ) ?: return null

            if (
                captureArea.left == 0 &&
                captureArea.top == 0 &&
                captureArea.right == capturedBitmap.width &&
                captureArea.bottom == capturedBitmap.height
            ) {
                val result = capturedBitmap
                sourceBitmap = null
                result
            } else {
                Bitmap.createBitmap(
                    capturedBitmap,
                    captureArea.left,
                    captureArea.top,
                    captureArea.width,
                    captureArea.height
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to crop OCR bitmap", e)
            null
        } finally {
            sourceBitmap?.recycle()
            image.close()
        }
    }

    fun recognizeTextFromBitmap(bitmap: Bitmap): String {
        return processBitmapText(bitmap) { result ->
            result.text.trim()
        } ?: ""
    }

    private fun <T> processBitmapText(
        bitmap: Bitmap,
        handleResult: (Text) -> T
    ): T? {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val result = Tasks.await(recognizer.process(inputImage), 5, TimeUnit.SECONDS)
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
