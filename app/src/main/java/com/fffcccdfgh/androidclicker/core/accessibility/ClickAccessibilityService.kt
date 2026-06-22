package com.fffcccdfgh.androidclicker.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.fffcccdfgh.androidclicker.R
import com.fffcccdfgh.androidclicker.core.execution.ActionStep
import com.fffcccdfgh.androidclicker.core.ocr.OcrHelper
import com.fffcccdfgh.androidclicker.core.ocr.TextConditionDetector
import com.fffcccdfgh.androidclicker.core.program.ProgramCoordinateAdapter
import com.fffcccdfgh.androidclicker.core.program.ProgramScreenSize
import com.fffcccdfgh.androidclicker.core.program.ProgramScreenSizePolicy
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenCaptureDisplayReader
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenCaptureManager
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenCapturePointMapper
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class ClickAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ClickA11yService"
        var isRunning = false
            private set
        var instance: ClickAccessibilityService? = null
            private set
    }

    suspend fun checkCondition(action: ActionStep): Boolean {
        val conditionType = action.conditionType ?: return true

        return when (conditionType) {
            ActionStep.CONDITION_TEXT_CONTAINS -> checkTextCondition(action, false)
            ActionStep.CONDITION_TEXT_NOT_CONTAINS -> checkTextCondition(action, true)
            ActionStep.CONDITION_COLOR_MATCH -> checkColorCondition(action, false)
            ActionStep.CONDITION_COLOR_NOT_MATCH -> checkColorCondition(action, true)
            else -> true
        }
    }

    private suspend fun checkTextCondition(action: ActionStep, invert: Boolean): Boolean {
        val conditionText = action.conditionText
        if (conditionText.isNullOrEmpty()) return true

        val useArea = action.conditionUseArea == true
        val areaRect: Rect? = if (useArea) {
            val l = action.conditionLeft ?: return false
            val t = action.conditionTop ?: return false
            val r = action.conditionRight ?: return false
            val b = action.conditionBottom ?: return false
            Rect(l, t, r, b)
        } else {
            null
        }

        val foundByOcr = detectConditionTextWithOcr(conditionText, areaRect)
        val found = TextConditionDetector.containsText(
            targetText = conditionText,
            ocrLookup = { foundByOcr }
        )
        return if (invert) !found else found
    }

    private suspend fun detectConditionTextWithOcr(
        text: String,
        area: Rect?
    ): Boolean {
        ScreenCaptureManager.refreshDisplayMetrics(this)
        val display = ScreenCaptureDisplayReader.current(this)
        val screenWidth = ScreenCaptureManager.getCaptureWidth()
            .takeIf { it > 0 }
            ?: display.width
        val screenHeight = ScreenCaptureManager.getCaptureHeight()
            .takeIf { it > 0 }
            ?: display.height
        if (screenWidth <= 0 || screenHeight <= 0) {
            Log.d(TAG, "detectConditionTextWithOcr skipped: invalid screen size")
            return false
        }
        Log.d(
            TAG,
            "detectConditionTextWithOcr start: targetLength=${text.length} " +
                "area=${area?.toShortString() ?: "full"} screen=${screenWidth}x${screenHeight}"
        )
        return withContext(Dispatchers.Default) {
            val found = OcrHelper.detectText(
                targetText = text,
                area = area,
                screenWidth = screenWidth,
                screenHeight = screenHeight
            )
            Log.d(TAG, "detectConditionTextWithOcr result: found=$found")
            found
        }
    }

    private suspend fun checkColorCondition(action: ActionStep, invert: Boolean): Boolean {
        if (!ScreenCaptureManager.isReady) return false

        val hex = action.conditionColorHex ?: return false
        val tolerancePercent = action.conditionColorTolerance?.coerceIn(0, 100) ?: 10
        val colorX = action.conditionColorX ?: return false
        val colorY = action.conditionColorY ?: return false

        val targetColor: Int
        try {
            targetColor = Color.parseColor(hex)
        } catch (_: Exception) {
            return false
        }

        ScreenCaptureManager.refreshDisplayMetrics(this)
        if (!ScreenCaptureManager.isReady) return false

        val image = withContext(Dispatchers.Default) {
            ScreenCaptureManager.captureFrameSync(2000L)
        } ?: return false

        return try {
            val point = ScreenCapturePointMapper.mapScreenPointToCapturePoint(
                screenX = colorX,
                screenY = colorY,
                screenWidth = ScreenCaptureManager.getCaptureWidth(),
                screenHeight = ScreenCaptureManager.getCaptureHeight(),
                captureWidth = image.width,
                captureHeight = image.height
            ) ?: return false

            val channelTolerance = Math.round(255f * tolerancePercent / 100f)

            val found = withContext(Dispatchers.Default) {
                val pixel = ScreenCaptureManager.readPixel(image, point.x, point.y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                Math.abs(r - Color.red(targetColor)) <= channelTolerance &&
                    Math.abs(g - Color.green(targetColor)) <= channelTolerance &&
                    Math.abs(b - Color.blue(targetColor)) <= channelTolerance
            }

            if (invert) !found else found
        } finally {
            image.close()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
        isRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isRunning = false
        Log.i(TAG, "Accessibility service destroyed")
    }

    suspend fun dispatchGestureAwait(action: ActionStep): Boolean {
        return try {
            suspendCancellableCoroutine { cont ->
                val callback = object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        cont.resume(true)
                    }
                }

                val ok = when (action.type) {
                    ActionStep.TYPE_TAP -> performTap(
                        action.x!!, action.y!!,
                        action.durationMs ?: 1, callback
                    )
                    ActionStep.TYPE_SWIPE -> performSwipe(
                        action.startX!!, action.startY!!,
                        action.endX!!, action.endY!!,
                        action.durationMs ?: 1, callback
                    )
                    else -> false
                }

                if (!ok) {
                    cont.resume(false)
                }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "dispatchGestureAwait failed", e)
            false
        }
    }

    fun performTap(x: Int, y: Int): Boolean {
        return performTap(x, y, 1, null)
    }

    fun performTap(x: Int, y: Int, callback: GestureResultCallback?): Boolean {
        return performTap(x, y, 1, callback)
    }

    fun performTap(x: Int, y: Int, durationMs: Long, callback: GestureResultCallback?): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()
        return dispatchGesture(gesture, callback, null)
    }

    fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 300): Boolean {
        return performSwipe(startX, startY, endX, endY, durationMs, null)
    }

    fun performSwipe(
        startX: Int, startY: Int, endX: Int, endY: Int,
        durationMs: Long = 300, callback: GestureResultCallback?
    ): Boolean {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()
        return dispatchGesture(gesture, callback, null)
    }

    private fun currentScreenSize(): ProgramScreenSize {
        ScreenCaptureManager.refreshDisplayMetrics(this)
        val display = ScreenCaptureDisplayReader.current(this)
        return ProgramScreenSizePolicy.choose(
            captureWidth = ScreenCaptureManager.getCaptureWidth(),
            captureHeight = ScreenCaptureManager.getCaptureHeight(),
            displayWidth = display.width,
            displayHeight = display.height,
            fallbackWidth = resources.displayMetrics.widthPixels,
            fallbackHeight = resources.displayMetrics.heightPixels
        )
    }
}
