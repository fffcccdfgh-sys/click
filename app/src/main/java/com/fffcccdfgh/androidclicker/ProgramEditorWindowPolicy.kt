package com.fffcccdfgh.androidclicker

import android.view.WindowManager
import kotlin.math.roundToInt

data class ProgramEditorWindowSize(
    val width: Int,
    val height: Int
)

object ProgramEditorWindowPolicy {
    const val FLAGS: Int =
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    const val SOFT_INPUT_MODE: Int =
        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

    fun windowSize(screenWidthPx: Int, screenHeightPx: Int, density: Float): ProgramEditorWindowSize {
        val safeWidth = screenWidthPx.coerceAtLeast(1)
        val safeHeight = screenHeightPx.coerceAtLeast(1)
        val marginPx = dp(16f, density)
        val availableWidth = (safeWidth - marginPx * 2).coerceAtLeast(1)
        val availableHeight = (safeHeight - marginPx * 2).coerceAtLeast(1)
        val landscape = safeWidth > safeHeight

        val widthFraction = if (landscape) 0.86f else 0.92f
        val heightFraction = if (landscape) 0.92f else 0.84f

        return ProgramEditorWindowSize(
            width = (safeWidth * widthFraction).roundToInt().coerceAtMost(availableWidth),
            height = (safeHeight * heightFraction).roundToInt().coerceAtMost(availableHeight)
        )
    }

    fun windowSizeForCurrentDisplay(
        displayWidthPx: Int,
        displayHeightPx: Int,
        resourceWidthPx: Int,
        resourceHeightPx: Int,
        density: Float
    ): ProgramEditorWindowSize {
        val width = displayWidthPx.takeIf { it > 0 } ?: resourceWidthPx
        val height = displayHeightPx.takeIf { it > 0 } ?: resourceHeightPx
        return windowSize(width, height, density)
    }

    fun codePanelHeight(editorWidthPx: Int, editorHeightPx: Int, density: Float): Int {
        val reservedChromePx = dp(316f, density)
        val availableForCode = editorHeightPx - reservedChromePx
        if (availableForCode <= 0) return 0

        return if (editorWidthPx > editorHeightPx) {
            val landscapeMax = (editorHeightPx * 0.45f).roundToInt()
            availableForCode.coerceAtMost(landscapeMax)
        } else {
            availableForCode
        }
    }

    private fun dp(value: Float, density: Float): Int {
        val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        return (value * safeDensity).roundToInt()
    }
}
