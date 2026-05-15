package com.fffcccdfgh.androidclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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

    private fun checkTextCondition(action: ActionStep, invert: Boolean): Boolean {
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

        val found = findConditionText(conditionText, areaRect)
        return if (invert) !found else found
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

        val image = withContext(Dispatchers.Default) {
            ScreenCaptureManager.captureFrameSync(2000L)
        } ?: return false

        return try {
            val capW = ScreenCaptureManager.getCaptureWidth()
            val capH = ScreenCaptureManager.getCaptureHeight()
            val px = (colorX.toFloat() * capW / resources.displayMetrics.widthPixels).toInt()
                .coerceIn(0, capW - 1)
            val py = (colorY.toFloat() * capH / resources.displayMetrics.heightPixels).toInt()
                .coerceIn(0, capH - 1)

            val channelTolerance = Math.round(255f * tolerancePercent / 100f)

            val found = withContext(Dispatchers.Default) {
                val pixel = ScreenCaptureManager.readPixel(image, px, py)
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

    /**
     * Collect text from all accessibility nodes that intersect the given rect.
     * Joins them with a space for easy pre-filling into the condition editor.
     */
    fun collectTextInArea(area: Rect): String {
        val sb = StringBuilder()
        for (window in windows) {
            val root = window.root ?: continue
            try {
                if (root.packageName?.toString() == packageName) continue
                collectTextInTree(root, area, sb)
            } finally {
                root.recycle()
            }
        }
        if (sb.isEmpty()) {
            val root = rootInActiveWindow ?: return ""
            try {
                if (root.packageName?.toString() != packageName) {
                    collectTextInTree(root, area, sb)
                }
            } finally {
                root.recycle()
            }
        }
        return sb.toString().trim()
    }

    private fun collectTextInTree(node: AccessibilityNodeInfo, area: Rect, sb: StringBuilder) {
        val nodeRect = Rect()
        node.getBoundsInScreen(nodeRect)
        if (!nodeRect.isEmpty && Rect.intersects(nodeRect, area)) {
            val text = node.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(text)
            }
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            if (desc.isNotEmpty() && desc != text) {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(desc)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTextInTree(child, area, sb)
            child.recycle()
        }
    }

    private fun findConditionText(text: String, area: Rect?): Boolean {
        var checkedExternalWindow = false

        for (window in windows) {
            val root = window.root ?: continue
            try {
                if (root.packageName?.toString() == packageName) {
                    continue
                }

                checkedExternalWindow = true
                if (findTextInTree(root, text, area)) {
                    return true
                }
            } finally {
                root.recycle()
            }
        }

        if (checkedExternalWindow) {
            return false
        }

        val root = rootInActiveWindow ?: return false
        return try {
            if (root.packageName?.toString() == packageName) {
                false
            } else {
                findTextInTree(root, text, area)
            }
        } finally {
            root.recycle()
        }
    }

    private fun findTextInTree(
        node: AccessibilityNodeInfo,
        text: String,
        area: Rect?
    ): Boolean {
        val matchesArea = if (area != null) {
            val nodeRect = Rect()
            node.getBoundsInScreen(nodeRect)
            if (nodeRect.isEmpty) {
                false
            } else {
                Rect.intersects(nodeRect, area)
            }
        } else {
            true
        }

        if (matchesArea) {
            val nodeText = node.text?.toString().orEmpty()
            val nodeDesc = node.contentDescription?.toString().orEmpty()
            if (nodeText.contains(text) || nodeDesc.contains(text)) {
                return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findTextInTree(child, text, area)) {
                child.recycle()
                return true
            }
            child.recycle()
        }

        return false
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
}