package com.fffcccdfgh.androidclicker.core.execution

import android.graphics.Rect
import com.fffcccdfgh.androidclicker.core.accessibility.ClickAccessibilityService
import com.fffcccdfgh.androidclicker.core.ocr.OcrHelper
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenCaptureManager
import kotlinx.coroutines.runBlocking

/**
 * Bridge object that exposes the clicker API to Lua scripts via LuaJ.
 * All methods are blocking from Lua's perspective.
 */
object ClickerBridge {

    @Volatile
    var stopped = false

    private fun checkStopped() {
        if (stopped) throw StopExecutionException()
    }

    @JvmStatic
    fun tap(x: Int, y: Int, durationMs: Long) {
        checkStopped()
        if (!ClickAccessibilityService.isRunning) return
        val service = ClickAccessibilityService.instance ?: return
        ExecutionTouchInterlock.waitIfPausedBlocking { stopped }
        checkStopped()
        val action = ActionStep(type = ActionStep.TYPE_TAP, x = x, y = y, durationMs = durationMs)
        runBlocking { service.dispatchGestureAwait(action) }
        checkStopped()
    }

    @JvmStatic
    fun swipe(sx: Int, sy: Int, ex: Int, ey: Int, durationMs: Long) {
        checkStopped()
        if (!ClickAccessibilityService.isRunning) return
        val service = ClickAccessibilityService.instance ?: return
        ExecutionTouchInterlock.waitIfPausedBlocking { stopped }
        checkStopped()
        val action = ActionStep(
            type = ActionStep.TYPE_SWIPE,
            startX = sx, startY = sy,
            endX = ex, endY = ey,
            durationMs = durationMs
        )
        runBlocking { service.dispatchGestureAwait(action) }
        checkStopped()
    }

    @JvmStatic
    fun wait_(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        checkStopped()
    }

    @JvmStatic
    fun checkText(text: String, left: Int, top: Int, right: Int, bottom: Int): Boolean {
        checkStopped()
        if (!ClickAccessibilityService.isRunning) return false
        val action = ActionStep(
            type = ActionStep.TYPE_PROGRAM,
            conditionType = ActionStep.CONDITION_TEXT_CONTAINS,
            conditionText = text,
            conditionUseArea = true,
            conditionLeft = left,
            conditionTop = top,
            conditionRight = right,
            conditionBottom = bottom
        )
        return runBlocking {
            ClickAccessibilityService.instance?.checkCondition(action) ?: false
        }
    }

    @JvmStatic
    fun checkTextNot(text: String, left: Int, top: Int, right: Int, bottom: Int): Boolean {
        checkStopped()
        if (!ClickAccessibilityService.isRunning) return false
        val action = ActionStep(
            type = ActionStep.TYPE_PROGRAM,
            conditionType = ActionStep.CONDITION_TEXT_NOT_CONTAINS,
            conditionText = text,
            conditionUseArea = true,
            conditionLeft = left,
            conditionTop = top,
            conditionRight = right,
            conditionBottom = bottom
        )
        return runBlocking {
            ClickAccessibilityService.instance?.checkCondition(action) ?: false
        }
    }

    @JvmStatic
    fun checkColor(hex: String, tolerance: Int, x: Int, y: Int): Boolean {
        checkStopped()
        if (!ClickAccessibilityService.isRunning) return false
        val action = ActionStep(
            type = ActionStep.TYPE_PROGRAM,
            conditionType = ActionStep.CONDITION_COLOR_MATCH,
            conditionColorHex = hex,
            conditionColorTolerance = tolerance,
            conditionColorX = x,
            conditionColorY = y
        )
        return runBlocking {
            ClickAccessibilityService.instance?.checkCondition(action) ?: false
        }
    }

    @JvmStatic
    fun checkColorNot(hex: String, tolerance: Int, x: Int, y: Int): Boolean {
        checkStopped()
        if (!ClickAccessibilityService.isRunning) return false
        val action = ActionStep(
            type = ActionStep.TYPE_PROGRAM,
            conditionType = ActionStep.CONDITION_COLOR_NOT_MATCH,
            conditionColorHex = hex,
            conditionColorTolerance = tolerance,
            conditionColorX = x,
            conditionColorY = y
        )
        return runBlocking {
            ClickAccessibilityService.instance?.checkCondition(action) ?: false
        }
    }

    @JvmStatic
    fun ocrText(text: String, left: Int, top: Int, right: Int, bottom: Int): Boolean {
        checkStopped()
        if (!ClickAccessibilityService.isRunning) return false
        val service = ClickAccessibilityService.instance ?: return false
        ScreenCaptureManager.refreshDisplayMetrics(service)
        if (!ScreenCaptureManager.isReady) return false
        return OcrHelper.detectText(
            targetText = text,
            area = Rect(left, top, right, bottom),
            screenWidth = ScreenCaptureManager.getCaptureWidth(),
            screenHeight = ScreenCaptureManager.getCaptureHeight()
        )
    }

    @JvmStatic
    fun ocrTextNot(text: String, left: Int, top: Int, right: Int, bottom: Int): Boolean {
        checkStopped()
        if (!ClickAccessibilityService.isRunning) return false
        val service = ClickAccessibilityService.instance ?: return false
        ScreenCaptureManager.refreshDisplayMetrics(service)
        if (!ScreenCaptureManager.isReady) return false
        return !OcrHelper.detectText(
            targetText = text,
            area = Rect(left, top, right, bottom),
            screenWidth = ScreenCaptureManager.getCaptureWidth(),
            screenHeight = ScreenCaptureManager.getCaptureHeight()
        )
    }
}

/** Thrown when execution is stopped by the user. */
class StopExecutionException : RuntimeException()
