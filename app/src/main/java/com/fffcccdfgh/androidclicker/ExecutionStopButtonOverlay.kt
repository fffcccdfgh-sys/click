package com.fffcccdfgh.androidclicker

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

data class OverlayPosition(val x: Int, val y: Int)

object ExecutionOverlayWindowPolicy {
    const val NORMAL_ALPHA = 1.0f
    const val TOUCH_THROUGH_ALPHA = 0.75f
}

object ExecutionStopButtonPositionPrefs {
    const val PREFS_NAME = "execution_stop_button_overlay"
    const val KEY_X = "x"
    const val KEY_Y = "y"
}

object ExecutionStopButtonPositioner {
    const val SIZE_DP = 56
    const val MARGIN_DP = 24

    fun defaultPosition(
        screenWidthPx: Int,
        screenHeightPx: Int,
        buttonSizePx: Int,
        marginPx: Int
    ): OverlayPosition {
        return clampPosition(
            x = screenWidthPx - buttonSizePx - marginPx,
            y = screenHeightPx - buttonSizePx - marginPx,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            buttonSizePx = buttonSizePx
        )
    }

    fun clampPosition(
        x: Int,
        y: Int,
        screenWidthPx: Int,
        screenHeightPx: Int,
        buttonSizePx: Int
    ): OverlayPosition {
        val maxX = (screenWidthPx - buttonSizePx).coerceAtLeast(0)
        val maxY = (screenHeightPx - buttonSizePx).coerceAtLeast(0)
        return OverlayPosition(
            x = x.coerceIn(0, maxX),
            y = y.coerceIn(0, maxY)
        )
    }
}

enum class StopButtonTouchDecision {
    NONE,
    PAUSE_DISPATCH,
    DRAG,
    STOP,
    RESUME_DISPATCH,
    SAVE_POSITION
}

enum class StopButtonTouchMode {
    EXECUTION,
    POSITIONING
}

class ExecutionStopButtonTouchState(
    private val touchSlopPx: Int,
    var mode: StopButtonTouchMode
) {
    private var dragged = false

    fun onDown(): StopButtonTouchDecision {
        dragged = false
        return if (mode == StopButtonTouchMode.EXECUTION) {
            StopButtonTouchDecision.PAUSE_DISPATCH
        } else {
            StopButtonTouchDecision.NONE
        }
    }

    fun onMove(dx: Float, dy: Float): StopButtonTouchDecision {
        if (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx) {
            dragged = true
            return StopButtonTouchDecision.DRAG
        }
        return StopButtonTouchDecision.NONE
    }

    fun onUp(): StopButtonTouchDecision {
        return if (dragged) {
            dragged = false
            if (mode == StopButtonTouchMode.EXECUTION) {
                StopButtonTouchDecision.RESUME_DISPATCH
            } else {
                StopButtonTouchDecision.SAVE_POSITION
            }
        } else if (mode == StopButtonTouchMode.EXECUTION) {
            StopButtonTouchDecision.STOP
        } else {
            StopButtonTouchDecision.NONE
        }
    }

    fun onCancel(): StopButtonTouchDecision {
        return if (dragged) {
            dragged = false
            if (mode == StopButtonTouchMode.EXECUTION) {
                StopButtonTouchDecision.RESUME_DISPATCH
            } else {
                StopButtonTouchDecision.SAVE_POSITION
            }
        } else if (mode == StopButtonTouchMode.EXECUTION) {
            StopButtonTouchDecision.STOP
        } else {
            StopButtonTouchDecision.NONE
        }
    }
}

class ExecutionStopButtonOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
    private val zoneKey: String,
    private val onStop: () -> Unit
) {
    private val prefs = context.getSharedPreferences(
        ExecutionStopButtonPositionPrefs.PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val density = context.resources.displayMetrics.density
    private val buttonSizePx = dp(ExecutionStopButtonPositioner.SIZE_DP)
    private val marginPx = dp(ExecutionStopButtonPositioner.MARGIN_DP)
    private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop
    private val touchState = ExecutionStopButtonTouchState(
        touchSlopPx = touchSlopPx,
        mode = StopButtonTouchMode.EXECUTION
    )

    private var stopView: TextView? = null
    private var stopParams: WindowManager.LayoutParams? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    fun show() {
        show(StopButtonTouchMode.EXECUTION)
    }

    fun showForPositioning() {
        show(StopButtonTouchMode.POSITIONING)
    }

    fun isShowing(): Boolean = stopView != null

    private fun show(mode: StopButtonTouchMode) {
        touchState.mode = mode
        if (stopView != null) return

        val view = TextView(context).apply {
            text = context.getString(R.string.stop_action)
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(0xE6D32F2F.toInt())
                setStroke(dp(1), 0x66FFFFFF)
            }
            elevation = dp(8).toFloat()
            isClickable = true
        }

        stopParams = createLayoutParams().apply {
            val position = loadPosition()
            x = position.x
            y = position.y
        }
        bindDrag(view)

        stopView = view
        windowManager.addView(view, stopParams)
        ControlZoneChecker.register(zoneKey) { controlZoneRect() }
        ScreenshotHider.register(
            zoneKey,
            hide = { stopView?.visibility = View.INVISIBLE },
            reveal = { stopView?.visibility = View.VISIBLE }
        )
    }

    fun hide() {
        ControlZoneChecker.unregister(zoneKey)
        ScreenshotHider.unregister(zoneKey)
        val view = stopView
        if (view != null) {
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
            }
        }
        stopView = null
        stopParams = null
    }

    private fun bindDrag(view: TextView) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val params = stopParams ?: return@setOnTouchListener false
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    handleTouchDecision(touchState.onDown())
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (touchState.onMove(dx, dy) == StopButtonTouchDecision.DRAG) {
                        moveTo(initialX + dx.toInt(), initialY + dy.toInt())
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val decision = touchState.onUp()
                    if (decision == StopButtonTouchDecision.RESUME_DISPATCH ||
                        decision == StopButtonTouchDecision.SAVE_POSITION
                    ) {
                        saveCurrentPosition()
                    }
                    handleTouchDecision(decision)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    val decision = touchState.onCancel()
                    if (decision == StopButtonTouchDecision.RESUME_DISPATCH ||
                        decision == StopButtonTouchDecision.SAVE_POSITION
                    ) {
                        saveCurrentPosition()
                    }
                    handleTouchDecision(decision)
                    true
                }
                else -> false
            }
        }
    }

    private fun handleTouchDecision(decision: StopButtonTouchDecision) {
        when (decision) {
            StopButtonTouchDecision.PAUSE_DISPATCH -> ExecutionTouchInterlock.pauseDispatch()
            StopButtonTouchDecision.STOP -> {
                try {
                    onStop()
                } finally {
                    ExecutionTouchInterlock.resumeDispatch()
                }
            }
            StopButtonTouchDecision.RESUME_DISPATCH -> ExecutionTouchInterlock.resumeDispatch()
            StopButtonTouchDecision.SAVE_POSITION -> Unit
            StopButtonTouchDecision.NONE,
            StopButtonTouchDecision.DRAG -> Unit
        }
    }

    private fun moveTo(x: Int, y: Int) {
        val params = stopParams ?: return
        val position = ExecutionStopButtonPositioner.clampPosition(
            x = x,
            y = y,
            screenWidthPx = screenWidthPx(),
            screenHeightPx = screenHeightPx(),
            buttonSizePx = buttonSizePx
        )
        params.x = position.x
        params.y = position.y
        val view = stopView ?: return
        if (view.isAttachedToWindow) {
            windowManager.updateViewLayout(view, params)
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            buttonSizePx,
            buttonSizePx,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun loadPosition(): OverlayPosition {
        val hasSavedPosition = prefs.contains(key(ExecutionStopButtonPositionPrefs.KEY_X)) &&
            prefs.contains(key(ExecutionStopButtonPositionPrefs.KEY_Y))
        val position = if (hasSavedPosition) {
            OverlayPosition(
                x = prefs.getInt(key(ExecutionStopButtonPositionPrefs.KEY_X), 0),
                y = prefs.getInt(key(ExecutionStopButtonPositionPrefs.KEY_Y), 0)
            )
        } else {
            ExecutionStopButtonPositioner.defaultPosition(
                screenWidthPx = screenWidthPx(),
                screenHeightPx = screenHeightPx(),
                buttonSizePx = buttonSizePx,
                marginPx = marginPx
            )
        }

        return ExecutionStopButtonPositioner.clampPosition(
            x = position.x,
            y = position.y,
            screenWidthPx = screenWidthPx(),
            screenHeightPx = screenHeightPx(),
            buttonSizePx = buttonSizePx
        )
    }

    private fun saveCurrentPosition() {
        val params = stopParams ?: return
        prefs.edit()
            .putInt(key(ExecutionStopButtonPositionPrefs.KEY_X), params.x)
            .putInt(key(ExecutionStopButtonPositionPrefs.KEY_Y), params.y)
            .apply()
    }

    private fun controlZoneRect(): Rect? {
        val params = stopParams ?: return null
        val view = stopView ?: return null
        val width = if (view.width > 0) view.width else params.width
        val height = if (view.height > 0) view.height else params.height
        if (width <= 0 || height <= 0) return null
        return Rect(params.x, params.y, params.x + width, params.y + height)
    }

    private fun screenWidthPx(): Int = context.resources.displayMetrics.widthPixels

    private fun screenHeightPx(): Int = context.resources.displayMetrics.heightPixels

    private fun dp(value: Int): Int = (value * density + 0.5f).toInt()

    private fun key(name: String): String = name
}
