package com.fffcccdfgh.androidclicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class AreaSelectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var isDragging = false
    private var hasSelection = false

    private val fillPaint = Paint().apply {
        color = Color.parseColor("#334CAF50")
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint().apply {
        color = Color.parseColor("#FF4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    var onSelectionComplete: ((left: Int, top: Int, right: Int, bottom: Int) -> Unit)? = null

    private val minSizePx by lazy {
        (20 * resources.displayMetrics.density).toInt()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                endX = startX
                endY = startY
                isDragging = true
                hasSelection = false
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    endX = event.x
                    endY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                    endX = event.x
                    endY = event.y
                    hasSelection = true
                    invalidate()

                    val left = startX.coerceAtMost(endX).toInt()
                    val top = startY.coerceAtMost(endY).toInt()
                    val right = startX.coerceAtLeast(endX).toInt()
                    val bottom = startY.coerceAtLeast(endY).toInt()
                    val width = right - left
                    val height = bottom - top

                    if (width < minSizePx || height < minSizePx) {
                        onSelectionComplete?.invoke(-1, -1, -1, -1)
                    } else {
                        onSelectionComplete?.invoke(left, top, right, bottom)
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun getSelectionRect(): Rect? {
        if (!hasSelection) return null
        val left = startX.coerceAtMost(endX).toInt()
        val top = startY.coerceAtMost(endY).toInt()
        val right = startX.coerceAtLeast(endX).toInt()
        val bottom = startY.coerceAtLeast(endY).toInt()
        if (right - left < minSizePx || bottom - top < minSizePx) return null
        return Rect(left, top, right, bottom)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isDragging && !hasSelection) return

        val left = startX.coerceAtMost(endX)
        val top = startY.coerceAtMost(endY)
        val right = startX.coerceAtLeast(endX)
        val bottom = startY.coerceAtLeast(endY)

        if (left == right || top == bottom) return

        canvas.drawRect(left, top, right, bottom, fillPaint)
        canvas.drawRect(left, top, right, bottom, strokePaint)
    }
}
