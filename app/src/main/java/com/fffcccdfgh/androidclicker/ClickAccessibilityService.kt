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

    /**
     * Evaluate a condition defined by a cond() command in program code.
     * Builds a temporary ActionStep and delegates to [checkCondition].
     */
    suspend fun checkConditionFromCmd(cmd: ProgramCommand.ConditionCmd): Boolean {
        val action = ActionStep(
            type = ActionStep.TYPE_PROGRAM,
            conditionType = cmd.conditionType,
            conditionText = cmd.conditionText,
            conditionUseArea = cmd.conditionLeft != null,
            conditionLeft = cmd.conditionLeft,
            conditionTop = cmd.conditionTop,
            conditionRight = cmd.conditionRight,
            conditionBottom = cmd.conditionBottom,
            conditionColorHex = cmd.conditionColorHex,
            conditionColorTolerance = cmd.conditionColorTolerance,
            conditionColorX = cmd.conditionColorX,
            conditionColorY = cmd.conditionColorY
        )
        return checkCondition(ProgramCoordinateAdapter.storedActionToRuntimePx(action, currentScreenSize()))
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

    /**
     * Collect text from all accessibility leaf nodes that have meaningful overlap
     * (≥ 25 % area ratio) with the given rect.
     * Joins them with a space for easy pre-filling into the condition editor.
     */
    private fun collectTextInTree(node: AccessibilityNodeInfo, area: Rect, sb: StringBuilder) {
        val nodeRect = Rect()
        node.getBoundsInScreen(nodeRect)
        // Require meaningful overlap: at least 25% of the node's area must be
        // inside the target rect, OR at least 25% of the target rect must be
        // covered by the node.  This is more robust than center‑point checks
        // (handles wide views) and stricter than bare intersects.
        val contained = if (nodeRect.isEmpty) {
            false
        } else {
            val overlap = Rect()
            val hasOverlap = overlap.setIntersect(nodeRect, area)
            if (!hasOverlap) {
                false
            } else {
                val overlapArea = overlap.width().toLong() * overlap.height().toLong()
                val nodeArea = nodeRect.width().toLong() * nodeRect.height().toLong()
                val areaArea = area.width().toLong() * area.height().toLong()
                // >= 25 % of the node or >= 25 % of the target area
                overlapArea * 4 >= nodeArea || overlapArea * 4 >= areaArea
            }
        }
        // Always recurse into nodes that intersect the area — a child may
        // be meaningfully inside even if its parent is only partially overlapping.
        val shouldRecurse = nodeRect.isEmpty || Rect.intersects(nodeRect, area)
        if (contained && node.childCount == 0) {
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
        if (shouldRecurse) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                collectTextInTree(child, area, sb)
                child.recycle()
            }
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
        val nodeRect = Rect()
        node.getBoundsInScreen(nodeRect)

        // Require meaningful overlap (≥ 25 % of node area or ≥ 25 % of target area)
        // before checking a node's text.  More robust than center‑point and
        // stricter than bare Rect.intersects.
        val matchesArea = if (area != null) {
            if (nodeRect.isEmpty) {
                false
            } else {
                val overlap = Rect()
                val hasOverlap = overlap.setIntersect(nodeRect, area)
                if (!hasOverlap) {
                    false
                } else {
                    val overlapArea = overlap.width().toLong() * overlap.height().toLong()
                    val nodeArea = nodeRect.width().toLong() * nodeRect.height().toLong()
                    val areaArea = area.width().toLong() * area.height().toLong()
                    overlapArea * 4 >= nodeArea || overlapArea * 4 >= areaArea
                }
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

        // Recurse into children that intersect the area — a child may
        // have meaningful overlap even if this node does not.
        val shouldRecurse = if (area != null) {
            nodeRect.isEmpty || Rect.intersects(nodeRect, area)
        } else {
            true
        }

        if (shouldRecurse) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (findTextInTree(child, text, area)) {
                    child.recycle()
                    return true
                }
                child.recycle()
            }
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

    private fun currentScreenSize(): ProgramScreenSize {
        ScreenCaptureManager.refreshDisplayMetrics(this)
        val captureWidth = ScreenCaptureManager.getCaptureWidth()
        val captureHeight = ScreenCaptureManager.getCaptureHeight()
        if (captureWidth > 0 && captureHeight > 0) {
            return ProgramScreenSize(captureWidth, captureHeight)
        }
        val display = ScreenCaptureDisplayReader.current(this)
        return ProgramScreenSize(display.width, display.height)
    }
}
