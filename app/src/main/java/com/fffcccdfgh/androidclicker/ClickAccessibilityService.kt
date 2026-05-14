package com.fffcccdfgh.androidclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
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
