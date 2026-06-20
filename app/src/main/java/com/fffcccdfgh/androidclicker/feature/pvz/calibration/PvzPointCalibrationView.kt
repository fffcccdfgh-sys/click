package com.fffcccdfgh.androidclicker.feature.pvz.calibration

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.fffcccdfgh.androidclicker.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class PvzCalibrationPoint(
    val key: String,
    val label: String,
    var x: Float,
    var y: Float,
    val previewColor: Int? = null
)

class PvzPointCalibrationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onSave: ((List<PvzCalibrationPoint>) -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val idleHandler = Handler(Looper.getMainLooper())
    private val showButtonsRunnable = Runnable {
        buttonsVisible = true
        invalidate()
    }

    private val points = mutableListOf<PvzCalibrationPoint>()
    private var activeIndex = -1
    private var selectedIndex = -1
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var dragDistance = 0f
    private var buttonsVisible = true

    private val circleRadius = 18f * density
    private val dotRadius = 3f * density
    private val labelOffset = 12f * density
    private val labelPaddingX = 8f * density
    private val labelPaddingY = 5f * density
    private val controlHeight = 40f * density
    private val controlMargin = 12f * density
    private val smallButtonWidth = 62f * density
    private val smallButtonHeight = 30f * density

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = Color.WHITE
    }
    private val selectedCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density
        color = 0xFFFACC15.toInt()
    }
    private val circleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x332563EB
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF2563EB.toInt()
    }
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xD91E293B.toInt()
    }
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * density
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }
    private val saveButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xE616A34A.toInt()
    }
    private val cancelButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xE6DC2626.toInt()
    }
    private val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xCC111827.toInt()
    }

    private val saveRect = RectF()
    private val cancelRect = RectF()

    fun setPoints(newPoints: List<PvzCalibrationPoint>) {
        points.clear()
        points.addAll(newPoints.map { it.copy() })
        selectedIndex = selectedIndex.takeIf { it in points.indices } ?: -1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for ((index, point) in points.withIndex()) {
            drawPoint(canvas, point, index == selectedIndex)
        }
        if (buttonsVisible) {
            drawButtons(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                idleHandler.removeCallbacks(showButtonsRunnable)
                if (buttonsVisible && saveRect.contains(event.x, event.y)) {
                    onSave?.invoke(points.map { it.copy() })
                    return true
                }
                if (buttonsVisible && cancelRect.contains(event.x, event.y)) {
                    onCancel?.invoke()
                    return true
                }
                val hitIndex = hitPoint(event.x, event.y)
                if (hitIndex >= 0) {
                    selectedIndex = hitIndex
                    activeIndex = hitIndex
                    invalidate()
                } else {
                    activeIndex = selectedIndex.takeIf { it in points.indices } ?: -1
                    if (activeIndex < 0) return false
                }
                lastTouchX = event.x
                lastTouchY = event.y
                dragDistance = 0f
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val index = activeIndex
                if (index < 0) return false
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                dragDistance += abs(dx) + abs(dy)
                if (dragDistance > touchSlop && buttonsVisible) {
                    buttonsVisible = false
                }
                points[index].x = (points[index].x + dx).coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat())
                points[index].y = (points[index].y + dy).coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat())
                lastTouchX = event.x
                lastTouchY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeIndex = -1
                idleHandler.postDelayed(showButtonsRunnable, IDLE_SHOW_BUTTONS_MS)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun cleanup() {
        idleHandler.removeCallbacks(showButtonsRunnable)
        onSave = null
        onCancel = null
    }

    private fun drawPoint(canvas: Canvas, point: PvzCalibrationPoint, selected: Boolean) {
        canvas.drawCircle(point.x, point.y, circleRadius, circleFillPaint)
        canvas.drawCircle(point.x, point.y, circleRadius, circlePaint)
        if (selected) {
            canvas.drawCircle(point.x, point.y, circleRadius + 4f * density, selectedCirclePaint)
        }
        canvas.drawCircle(point.x, point.y, dotRadius, dotPaint)
        drawLabel(canvas, point)
    }

    private fun drawLabel(canvas: Canvas, point: PvzCalibrationPoint) {
        val textWidth = labelTextPaint.measureText(point.label)
        val textHeight = labelTextPaint.textSize
        val labelWidth = textWidth + labelPaddingX * 2f
        val labelHeight = textHeight + labelPaddingY * 2f

        val aboveTop = point.y - circleRadius - labelOffset - labelHeight
        val belowTop = point.y + circleRadius + labelOffset
        val top = if (aboveTop >= 0f) {
            aboveTop
        } else {
            min(belowTop, height.toFloat() - labelHeight)
        }
        val left = (point.x - labelWidth / 2f).coerceIn(0f, max(0f, width.toFloat() - labelWidth))
        val rect = RectF(left, top, left + labelWidth, top + labelHeight)
        canvas.drawRoundRect(rect, 10f * density, 10f * density, labelBgPaint)
        val baseline = rect.top + labelPaddingY + textHeight * 0.78f
        canvas.drawText(point.label, rect.left + labelPaddingX, baseline, labelTextPaint)
    }

    private fun drawButtons(canvas: Canvas) {
        val barLeft = controlMargin
        val barRight = width.toFloat() - controlMargin
        val barBottom = height.toFloat() - controlMargin
        val barTop = barBottom - controlHeight
        val barRect = RectF(barLeft, barTop, barRight, barBottom)
        canvas.drawRoundRect(barRect, 12f * density, 12f * density, barPaint)

        val buttonTop = barTop + (controlHeight - smallButtonHeight) / 2f
        cancelRect.set(
            barLeft + 5f * density,
            buttonTop,
            barLeft + 5f * density + smallButtonWidth,
            buttonTop + smallButtonHeight
        )
        saveRect.set(
            barRight - 5f * density - smallButtonWidth,
            buttonTop,
            barRight - 5f * density,
            buttonTop + smallButtonHeight
        )
        canvas.drawRoundRect(cancelRect, 8f * density, 8f * density, cancelButtonPaint)
        canvas.drawRoundRect(saveRect, 8f * density, 8f * density, saveButtonPaint)
        val baseline = cancelRect.centerY() - (buttonTextPaint.ascent() + buttonTextPaint.descent()) / 2f
        canvas.drawText(context.getString(R.string.cancel), cancelRect.centerX(), baseline, buttonTextPaint)
        canvas.drawText(context.getString(R.string.save), saveRect.centerX(), baseline, buttonTextPaint)
    }

    private fun hitPoint(x: Float, y: Float): Int {
        val hitRadius = circleRadius * 1.6f
        for (index in points.indices.reversed()) {
            val point = points[index]
            if (abs(x - point.x) <= hitRadius && abs(y - point.y) <= hitRadius) {
                return index
            }
        }
        return -1
    }

    companion object {
        private const val IDLE_SHOW_BUTTONS_MS = 350L
    }
}
