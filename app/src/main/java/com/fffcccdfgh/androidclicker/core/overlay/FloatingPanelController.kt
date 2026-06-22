package com.fffcccdfgh.androidclicker.core.overlay

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.fffcccdfgh.androidclicker.core.execution.ControlZoneChecker
import com.fffcccdfgh.androidclicker.core.screencapture.ScreenshotHider

class FloatingPanelController(
    private val windowManagerProvider: () -> WindowManager?,
    private val touchThroughProvider: () -> Boolean
) {
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    fun bindDrag(
        handle: View,
        panel: View,
        params: WindowManager.LayoutParams,
        onMoved: () -> Unit = {}
    ) {
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManagerProvider()?.updateViewLayout(panel, params)
                    onMoved()
                    true
                }
                else -> false
            }
        }
    }

    fun remove(
        view: View?,
        zoneKey: String,
        onRemoved: () -> Unit
    ) {
        val wm = windowManagerProvider()
        if (view != null && wm != null) {
            try {
                wm.removeView(view)
            } catch (_: Exception) {
            }
        }
        ScreenshotHider.unregister(zoneKey)
        ControlZoneChecker.unregister(zoneKey)
        onRemoved()
    }

    fun zoneRect(view: View?, params: WindowManager.LayoutParams?): Rect? {
        if (touchThroughProvider()) return null
        if (view == null || params == null) return null
        if (view.width <= 0 || view.height <= 0) return null
        return Rect(params.x, params.y, params.x + view.width, params.y + view.height)
    }

    fun registerVisibility(
        zoneKey: String,
        viewProvider: () -> View?,
        paramsProvider: () -> WindowManager.LayoutParams?
    ) {
        ControlZoneChecker.register(zoneKey) {
            zoneRect(viewProvider(), paramsProvider())
        }
        ScreenshotHider.register(
            zoneKey,
            hide = { viewProvider()?.visibility = View.INVISIBLE },
            reveal = { viewProvider()?.visibility = View.VISIBLE }
        )
    }

    fun show(
        panel: View,
        params: WindowManager.LayoutParams,
        zoneKey: String,
        viewProvider: () -> View?,
        paramsProvider: () -> WindowManager.LayoutParams?
    ) {
        windowManagerProvider()?.addView(panel, params)
        registerVisibility(
            zoneKey = zoneKey,
            viewProvider = viewProvider,
            paramsProvider = paramsProvider
        )
    }
}
