package com.fffcccdfgh.androidclicker.core.screencapture

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager

data class ScreenCaptureDisplayInfo(
    val width: Int,
    val height: Int,
    val densityDpi: Int
) {
    val isValid: Boolean get() = width > 0 && height > 0 && densityDpi > 0
}

data class ScreenCapturePoint(
    val x: Int,
    val y: Int
)

object ScreenCapturePointMapper {
    fun mapScreenPointToCapturePoint(
        screenX: Int,
        screenY: Int,
        screenWidth: Int,
        screenHeight: Int,
        captureWidth: Int,
        captureHeight: Int
    ): ScreenCapturePoint? {
        if (screenWidth <= 0 || screenHeight <= 0 || captureWidth <= 0 || captureHeight <= 0) {
            return null
        }

        val x = (screenX.toDouble() * captureWidth / screenWidth)
            .toInt()
            .coerceIn(0, captureWidth - 1)
        val y = (screenY.toDouble() * captureHeight / screenHeight)
            .toInt()
            .coerceIn(0, captureHeight - 1)
        return ScreenCapturePoint(x, y)
    }
}

object ScreenCaptureDisplayReader {
    fun current(context: Context): ScreenCaptureDisplayInfo {
        return readDefaultDisplayMetrics(context)
            ?: readWindowManagerMetrics(context)
            ?: readResourceMetrics(context)
    }

    @Suppress("DEPRECATION")
    private fun readDefaultDisplayMetrics(context: Context): ScreenCaptureDisplayInfo? {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            ?: return null
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        return metrics.toCaptureInfo().takeIf { it.isValid }
    }

    @Suppress("DEPRECATION")
    private fun readWindowManagerMetrics(context: Context): ScreenCaptureDisplayInfo? {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return null
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics.toCaptureInfo().takeIf { it.isValid }
    }

    private fun readResourceMetrics(context: Context): ScreenCaptureDisplayInfo {
        return context.resources.displayMetrics.toCaptureInfo()
    }

    private fun DisplayMetrics.toCaptureInfo(): ScreenCaptureDisplayInfo {
        return ScreenCaptureDisplayInfo(
            width = widthPixels,
            height = heightPixels,
            densityDpi = densityDpi
        )
    }
}
