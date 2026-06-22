package com.fffcccdfgh.androidclicker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optical Character Recognition helper using offline PaddleOCR.
 */
object OcrHelper {
    private const val TAG = "OcrHelper"
    private const val WARM_UP_BITMAP_SIZE = 32

    private val warmUpStarted = AtomicBoolean(false)
    private var lastPaddleUnavailableReason: String? = null

    var debugContext: Context? = null
        set(value) {
            field = value?.applicationContext
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
        Log.d(
            TAG,
            "detectText start: targetLength=${targetText.length} area=${area?.toShortString() ?: "full"} " +
                "screen=${screenWidth}x${screenHeight}"
        )
        val bitmap = captureAreaBitmap(
            area = area,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        ) ?: run {
            Log.d(TAG, "detectText skipped: capture returned null")
            return false
        }
        try {
            val recognizedText = processBitmapText(bitmap)
            val exactMatch = recognizedText.contains(targetText)
            val looseMatch = OcrTextMatcher.matches(
                recognizedText = recognizedText,
                targetText = targetText
            )
            Log.d(
                TAG,
                "detectText bitmap=${bitmap.width}x${bitmap.height} " +
                    "recognizedLength=${recognizedText.length} exactMatch=$exactMatch looseMatch=$looseMatch"
            )
            if (looseMatch) {
                Log.d(TAG, "detectText result: matched=true")
                return true
            }
            Log.d(TAG, "detectText result: matched=false")
            saveDebugCrop(bitmap)
            return false
        } finally {
            bitmap.recycle()
        }
    }

    fun recognizeText(
        area: Rect?,
        screenWidth: Int,
        screenHeight: Int,
        captureTimeoutMs: Long = OcrTimingPolicy.DEFAULT_CAPTURE_TIMEOUT_MS
    ): String {
        Log.d(
            TAG,
            "recognizeText start: area=${area?.toShortString() ?: "full"} " +
                "screen=${screenWidth}x${screenHeight} timeout=${captureTimeoutMs}ms"
        )
        val bitmap = captureAreaBitmap(
            area = area,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            timeoutMs = captureTimeoutMs
        ) ?: return ""
        return try {
            val text = recognizeTextFromBitmap(bitmap)
            Log.d(
                TAG,
                "recognizeText result: bitmap=${bitmap.width}x${bitmap.height} " +
                    "recognizedLength=${text.length}"
            )
            text
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
            // Log resolution mismatch between capture and requested screen dimensions
            if (image.width != screenWidth || image.height != screenHeight) {
                Log.w(
                    TAG,
                    "captureAreaBitmap resolution mismatch: " +
                        "capture=${image.width}x${image.height} vs requested=${screenWidth}x${screenHeight}"
                )
            } else {
                Log.d(
                    TAG,
                    "captureAreaBitmap resolution: capture=${image.width}x${image.height} matches requested"
                )
            }

            val captureArea = mapCaptureArea(
                area = area,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                captureWidth = image.width,
                captureHeight = image.height
            ) ?: run {
                Log.d(
                    TAG,
                    "captureAreaBitmap skipped: invalid mapped area. requested=${area?.toShortString() ?: "full"} " +
                        "screen=${screenWidth}x${screenHeight} capture=${image.width}x${image.height}"
                )
                return null
            }

            Log.d(
                TAG,
                "captureAreaBitmap mapped: requested=${area?.toShortString() ?: "full"} " +
                    "screen=${screenWidth}x${screenHeight} capture=${image.width}x${image.height} " +
                    "mapped=[${captureArea.left},${captureArea.top}][${captureArea.right},${captureArea.bottom}]"
            )

            val resultBitmap = if (
                captureArea.left == 0 &&
                captureArea.top == 0 &&
                captureArea.right == image.width &&
                captureArea.bottom == image.height
            ) {
                ScreenCaptureManager.imageToBitmap(image)
            } else {
                OcrImageCropper.cropImageAreaToBitmap(image, captureArea)
            }
            if (resultBitmap != null) {
                Log.d(TAG, "OCR crop bitmap: ${resultBitmap.width}x${resultBitmap.height} pixels")
            }
            resultBitmap
        } catch (e: Exception) {
            Log.w(TAG, "Failed to crop OCR bitmap", e)
            null
        } finally {
            image.close()
        }
    }

    fun recognizeTextFromBitmap(bitmap: Bitmap): String = processBitmapText(bitmap)

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
                processBitmapText(bitmap)
                Log.d(TAG, "OCR warm-up finished in ${SystemClock.uptimeMillis() - startedAt}ms")
            } catch (e: Exception) {
                Log.w(TAG, "OCR warm-up failed", e)
            } finally {
                bitmap.recycle()
            }
        }.start()
    }

    private fun processBitmapText(bitmap: Bitmap): String {
        when (val paddleResult = PaddleOcrHelper.recognizeTextFromBitmap(debugContext, bitmap)) {
            is PaddleOcrHelper.Result.Success -> {
                if (OcrDebugConfig.VERBOSE_LOGS) {
                    Log.d(
                        TAG,
                        "PaddleOCR result bitmap=${bitmap.width}x${bitmap.height} " +
                            "recognizedLength=${paddleResult.text.length} elapsed=${paddleResult.elapsedMs}ms"
                    )
                }
                return paddleResult.text
            }
            is PaddleOcrHelper.Result.Unavailable -> {
                if (lastPaddleUnavailableReason != paddleResult.reason) {
                    lastPaddleUnavailableReason = paddleResult.reason
                    Log.w(TAG, "OCR unavailable: PaddleOCR ${paddleResult.reason}")
                }
                return ""
            }
        }
    }

    private fun saveDebugCrop(bitmap: Bitmap) {
        val ctx = debugContext
        if (ctx == null) {
            Log.d(TAG, "OCR debug save skipped: debugContext not set")
            return
        }
        OcrDebugImageSaver.savePrefillFailureCrop(ctx, bitmap)
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
        return OcrAreaMapper.mapScreenAreaToCaptureAreaAllowingRotation(
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
