package com.fffcccdfgh.androidclicker

import android.view.Gravity
import android.view.View
import android.view.WindowManager

object FloatingWindowLayoutPolicy {
    fun applyCenteredSize(
        params: WindowManager.LayoutParams,
        screenWidthPx: Int,
        screenHeightPx: Int,
        size: FloatingWindowSize
    ) {
        params.width = size.widthPx
        params.height = size.heightPx
        applyCenteredPosition(params, screenWidthPx, screenHeightPx, size)
    }

    fun applyCenteredPosition(
        params: WindowManager.LayoutParams,
        screenWidthPx: Int,
        screenHeightPx: Int,
        size: FloatingWindowSize
    ) {
        val position = FloatingWindowSizePolicy.centeredPosition(screenWidthPx, screenHeightPx, size)
        params.gravity = Gravity.TOP or Gravity.START
        params.x = position.xPx
        params.y = position.yPx
    }

    fun applyCenterGravitySize(
        params: WindowManager.LayoutParams,
        size: FloatingWindowSize
    ) {
        params.width = size.widthPx
        params.height = size.heightPx
        params.gravity = Gravity.CENTER
        params.x = 0
        params.y = 0
    }

    fun updateIfAttached(
        windowManager: WindowManager?,
        view: View,
        params: WindowManager.LayoutParams
    ) {
        if (view.isAttachedToWindow) {
            windowManager?.updateViewLayout(view, params)
        }
    }
}
