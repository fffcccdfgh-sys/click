package com.fffcccdfgh.androidclicker.core.picker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.fffcccdfgh.androidclicker.R

class AreaSelectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val rect = RectF()
    private var hasInitialRect = false

    private val edgeHitSize by lazy { (30 * resources.displayMetrics.density) }
    private val minSize by lazy { (20 * resources.displayMetrics.density) }
    private val touchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }

    private var touchMode = TouchMode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var dragMoveDistance = 0f
    private var buttonsHiddenForCurrentDrag = false

    private val idleHandler = Handler(Looper.getMainLooper())
    private val showButtonsRunnable = Runnable { finishInteraction() }

    private val fillPaint = Paint().apply {
        color = Color.parseColor("#3360A5FA")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val strokePaint = Paint().apply {
        color = Color.parseColor("#FF60A5FA")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        isAntiAlias = true
    }
    private val innerStrokePaint = Paint().apply {
        color = Color.parseColor("#CCDBEAFE")
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
        isAntiAlias = true
    }
    private val divisionLinePaint = Paint().apply {
        color = Color.parseColor("#E5FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
        pathEffect = android.graphics.DashPathEffect(
            floatArrayOf(
                8f * resources.displayMetrics.density,
                6f * resources.displayMetrics.density
            ),
            0f
        )
        isAntiAlias = true
    }
    private val handlePaint = Paint().apply {
        color = Color.parseColor("#FFBFDBFE")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val handleStrokePaint = Paint().apply {
        color = Color.parseColor("#FF1D4ED8")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
        isAntiAlias = true
    }

    var onInteractionStarted: (() -> Unit)? = null
    var onInteractionFinished: (() -> Unit)? = null
    var divisionCount: Int = 0
        set(value) {
            field = value.coerceAtLeast(0)
            invalidate()
        }
    var divisionOrientation: DivisionOrientation = DivisionOrientation.VERTICAL
        set(value) {
            field = value
            invalidate()
        }
    var divisionRows: Int = 0
        set(value) {
            field = value.coerceAtLeast(0)
            invalidate()
        }
    var divisionColumns: Int = 0
        set(value) {
            field = value.coerceAtLeast(0)
            invalidate()
        }

    enum class DivisionOrientation {
        VERTICAL,
        HORIZONTAL
    }

    private enum class TouchMode {
        NONE, MOVE,
        RESIZE_N, RESIZE_S, RESIZE_E, RESIZE_W,
        RESIZE_NE, RESIZE_NW, RESIZE_SE, RESIZE_SW
    }

    fun setInitialRect(left: Int?, top: Int?, right: Int?, bottom: Int?) {
        if (left != null && top != null && right != null && bottom != null
            && right > left && bottom > top
        ) {
            rect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
            hasInitialRect = true
        } else {
            hasInitialRect = false
        }
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (!hasInitialRect && width > 0 && height > 0) {
            val boxW = (200 * resources.displayMetrics.density)
            val boxH = (150 * resources.displayMetrics.density)
            val cx = width / 2f
            val cy = height / 2f
            rect.set(cx - boxW / 2, cy - boxH / 2, cx + boxW / 2, cy + boxH / 2)
            hasInitialRect = true
            invalidate()
        }
    }

    fun getSelectionRect(): Rect? {
        if (!hasInitialRect) return null
        val l = rect.left.toInt()
        val t = rect.top.toInt()
        val r = rect.right.toInt()
        val b = rect.bottom.toInt()
        if (r - l < minSize || b - t < minSize) return null
        return Rect(l, t, r, b)
    }

    fun cleanup() {
        idleHandler.removeCallbacks(showButtonsRunnable)
        onInteractionStarted = null
        onInteractionFinished = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!hasInitialRect) return false
                touchMode = detectTouchMode(x, y)
                if (touchMode == TouchMode.NONE) return false
                lastTouchX = x
                lastTouchY = y
                dragMoveDistance = 0f
                buttonsHiddenForCurrentDrag = false
                idleHandler.removeCallbacks(showButtonsRunnable)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchMode == TouchMode.NONE) return false
                val dx = x - lastTouchX
                val dy = y - lastTouchY
                dragMoveDistance += Math.abs(dx) + Math.abs(dy)
                if (!buttonsHiddenForCurrentDrag &&
                    PickerActionButtonsVisibilityPolicy.shouldHideWhileChanging(
                        dragMoveDistance,
                        touchSlop.toFloat()
                    )
                ) {
                    buttonsHiddenForCurrentDrag = true
                    onInteractionStarted?.invoke()
                    invalidate()
                }
                applyMove(dx, dy)
                lastTouchX = x
                lastTouchY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchMode = TouchMode.NONE
                when (
                    PickerActionButtonsVisibilityPolicy.showTimingAfterTouchEnd(
                        dragMoveDistance,
                        touchSlop.toFloat()
                    )
                ) {
                    PickerActionButtonsVisibilityPolicy.ShowTiming.NOW -> finishInteraction()
                    PickerActionButtonsVisibilityPolicy.ShowTiming.AFTER_IDLE_DELAY -> {
                        idleHandler.postDelayed(showButtonsRunnable, IDLE_SHOW_BUTTONS_MS)
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun detectTouchMode(x: Float, y: Float): TouchMode {
        val onLeft = Math.abs(x - rect.left) < edgeHitSize
        val onRight = Math.abs(x - rect.right) < edgeHitSize
        val onTop = Math.abs(y - rect.top) < edgeHitSize
        val onBottom = Math.abs(y - rect.bottom) < edgeHitSize

        // Corners (checked first so they take priority over single edges)
        if (onLeft && onTop) return TouchMode.RESIZE_NW
        if (onRight && onTop) return TouchMode.RESIZE_NE
        if (onLeft && onBottom) return TouchMode.RESIZE_SW
        if (onRight && onBottom) return TouchMode.RESIZE_SE

        // Single edges (only when also inside the opposite axis range)
        if (onLeft && y > rect.top && y < rect.bottom) return TouchMode.RESIZE_W
        if (onRight && y > rect.top && y < rect.bottom) return TouchMode.RESIZE_E
        if (onTop && x > rect.left && x < rect.right) return TouchMode.RESIZE_N
        if (onBottom && x > rect.left && x < rect.right) return TouchMode.RESIZE_S

        // Everything else: move the entire box
        return TouchMode.MOVE
    }

    private fun applyMove(dx: Float, dy: Float) {
        when (touchMode) {
            TouchMode.MOVE -> {
                rect.offset(dx, dy)
                if (rect.left < 0) rect.offset(-rect.left, 0f)
                if (rect.top < 0) rect.offset(0f, -rect.top)
                if (rect.right > width) rect.offset(width - rect.right, 0f)
                if (rect.bottom > height) rect.offset(0f, height - rect.bottom)
            }
            TouchMode.RESIZE_N -> {
                rect.top = (rect.top + dy).coerceIn(0f, rect.bottom - minSize)
            }
            TouchMode.RESIZE_S -> {
                rect.bottom = (rect.bottom + dy).coerceIn(rect.top + minSize, height.toFloat())
            }
            TouchMode.RESIZE_W -> {
                rect.left = (rect.left + dx).coerceIn(0f, rect.right - minSize)
            }
            TouchMode.RESIZE_E -> {
                rect.right = (rect.right + dx).coerceIn(rect.left + minSize, width.toFloat())
            }
            TouchMode.RESIZE_NW -> {
                rect.top = (rect.top + dy).coerceIn(0f, rect.bottom - minSize)
                rect.left = (rect.left + dx).coerceIn(0f, rect.right - minSize)
            }
            TouchMode.RESIZE_NE -> {
                rect.top = (rect.top + dy).coerceIn(0f, rect.bottom - minSize)
                rect.right = (rect.right + dx).coerceIn(rect.left + minSize, width.toFloat())
            }
            TouchMode.RESIZE_SW -> {
                rect.bottom = (rect.bottom + dy).coerceIn(rect.top + minSize, height.toFloat())
                rect.left = (rect.left + dx).coerceIn(0f, rect.right - minSize)
            }
            TouchMode.RESIZE_SE -> {
                rect.bottom = (rect.bottom + dy).coerceIn(rect.top + minSize, height.toFloat())
                rect.right = (rect.right + dx).coerceIn(rect.left + minSize, width.toFloat())
            }
            TouchMode.NONE -> {}
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!hasInitialRect) return

        val corner = 10f * resources.displayMetrics.density
        canvas.drawRoundRect(rect, corner, corner, fillPaint)
        canvas.drawRoundRect(rect, corner, corner, strokePaint)
        val inset = 4f * resources.displayMetrics.density
        canvas.drawRoundRect(
            rect.left + inset,
            rect.top + inset,
            rect.right - inset,
            rect.bottom - inset,
            corner,
            corner,
            innerStrokePaint
        )
        drawDivisionLines(canvas)

        // Corner handles
        val r = edgeHitSize / 3
        drawHandle(canvas, rect.left, rect.top, r)
        drawHandle(canvas, rect.right, rect.top, r)
        drawHandle(canvas, rect.left, rect.bottom, r)
        drawHandle(canvas, rect.right, rect.bottom, r)
    }

    private fun drawHandle(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        canvas.drawCircle(cx, cy, radius, handlePaint)
        canvas.drawCircle(cx, cy, radius, handleStrokePaint)
    }

    private fun finishInteraction() {
        buttonsHiddenForCurrentDrag = false
        onInteractionFinished?.invoke()
        invalidate()
    }

    private fun drawDivisionLines(canvas: Canvas) {
        if (buttonsHiddenForCurrentDrag || touchMode != TouchMode.NONE) return
        if (divisionRows > 1 || divisionColumns > 1) {
            drawGridDivisionLines(canvas)
            return
        }
        if (divisionCount <= 1) return
        if (divisionOrientation == DivisionOrientation.HORIZONTAL) {
            val step = rect.height() / divisionCount.toFloat()
            for (index in 1 until divisionCount) {
                val y = rect.top + step * index
                canvas.drawLine(rect.left, y, rect.right, y, divisionLinePaint)
            }
        } else {
            val step = rect.width() / divisionCount.toFloat()
            for (index in 1 until divisionCount) {
                val x = rect.left + step * index
                canvas.drawLine(x, rect.top, x, rect.bottom, divisionLinePaint)
            }
        }
    }

    private fun drawGridDivisionLines(canvas: Canvas) {
        if (divisionRows > 1) {
            val rowHeight = rect.height() / divisionRows.toFloat()
            for (row in 1 until divisionRows) {
                val y = rect.top + rowHeight * row
                canvas.drawLine(rect.left, y, rect.right, y, divisionLinePaint)
            }
        }
        if (divisionColumns > 1) {
            val columnWidth = rect.width() / divisionColumns.toFloat()
            for (column in 1 until divisionColumns) {
                val x = rect.left + columnWidth * column
                canvas.drawLine(x, rect.top, x, rect.bottom, divisionLinePaint)
            }
        }
    }

    companion object {
        private const val IDLE_SHOW_BUTTONS_MS = 400L
    }
}
