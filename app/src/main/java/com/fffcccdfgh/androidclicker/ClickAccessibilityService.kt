package com.fffcccdfgh.androidclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class ClickAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ClickA11yService"
        var isRunning = false
            private set
        var instance: ClickAccessibilityService? = null
            private set
    }

    fun checkCondition(action: ActionStep): Boolean {
        val conditionType = action.conditionType ?: return true
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

        return when (conditionType) {
            ActionStep.CONDITION_TEXT_CONTAINS -> found
            ActionStep.CONDITION_TEXT_NOT_CONTAINS -> !found
            else -> true
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
