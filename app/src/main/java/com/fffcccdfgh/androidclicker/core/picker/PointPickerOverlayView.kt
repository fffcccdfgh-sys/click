package com.fffcccdfgh.androidclicker.core.picker

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

class PointPickerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var instruction: String = ""
        set(value) {
            field = value
            invalidate()
        }
    var coordinateFormatter: ((Int, Int) -> String)? = null
        set(value) {
            field = value
            invalidate()
        }
    var onSavePoint: ((Int, Int) -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val idleHandler = Handler(Looper.getMainLooper())
    private val showButtonsRunnable = Runnable {
        buttonsVisible = true
        invalidate()
    }

    private var pointX = 0f
    private var pointY = 0f
    private var hasPoint = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var dragDistance = 0f
    private var dragging = false
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

    private val saveRect = RectF()
    private val cancelRect = RectF()
    private val locationOnScreen = IntArray(2)

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
    private val instructionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val coordinateTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xCC111827.toInt()
    }
    private val saveButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xE616A34A.toInt()
    }
    private val cancelButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xE6DC2626.toInt()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!hasPoint && w > 0 && h > 0) {
            pointX = w / 2f
            pointY = h / 2f
            hasPoint = true
        } else if (w > 0 && h > 0) {
            clampPoint()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!hasPoint) return
        drawInstruction(canvas)
        drawPoint(canvas)
        if (buttonsVisible && !dragging) {
            drawControls(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                idleHandler.removeCallbacks(showButtonsRunnable)
                if (buttonsVisible && saveRect.contains(event.x, event.y)) {
                    onSavePoint?.invoke(screenX(), screenY())
                    return true
                }
                if (buttonsVisible && cancelRect.contains(event.x, event.y)) {
                    onCancel?.invoke()
                    return true
                }
                lastTouchX = event.x
                lastTouchY = event.y
                dragDistance = 0f
                dragging = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return true
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                dragDistance += abs(dx) + abs(dy)
                if (dragDistance > touchSlop && buttonsVisible) {
                    buttonsVisible = false
                }
                pointX += dx
                pointY += dy
                clampPoint()
                lastTouchX = event.x
                lastTouchY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                idleHandler.postDelayed(showButtonsRunnable, IDLE_SHOW_BUTTONS_MS)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun cleanup() {
        idleHandler.removeCallbacks(showButtonsRunnable)
        onSavePoint = null
        onCancel = null
        coordinateFormatter = null
    }

    private fun drawInstruction(canvas: Canvas) {
        if (instruction.isBlank()) return
        val textPaddingX = 12f * density
        val textPaddingY = 7f * density
        val textWidth = instructionTextPaint.measureText(instruction)
        val textHeight = instructionTextPaint.textSize
        val width = min(width.toFloat() - 24f * density, textWidth + textPaddingX * 2f)
        val left = (this.width.toFloat() - width) / 2f
        val top = 18f * density
        val rect = RectF(left, top, left + width, top + textHeight + textPaddingY * 2f)
        canvas.drawRoundRect(rect, 14f * density, 14f * density, labelBgPaint)
        val baseline = rect.top + textPaddingY + textHeight * 0.78f
        canvas.drawText(instruction, rect.centerX(), baseline, instructionTextPaint)
    }

    private fun drawPoint(canvas: Canvas) {
        canvas.drawCircle(pointX, pointY, circleRadius, circleFillPaint)
        canvas.drawCircle(pointX, pointY, circleRadius, circlePaint)
        canvas.drawCircle(pointX, pointY, circleRadius + 4f * density, selectedCirclePaint)
        canvas.drawCircle(pointX, pointY, dotRadius, dotPaint)
        drawPointLabel(canvas, coordinateText())
    }

    private fun drawPointLabel(canvas: Canvas, text: String) {
        val textWidth = labelTextPaint.measureText(text)
        val textHeight = labelTextPaint.textSize
        val labelWidth = textWidth + labelPaddingX * 2f
        val labelHeight = textHeight + labelPaddingY * 2f
        val aboveTop = pointY - circleRadius - labelOffset - labelHeight
        val belowTop = pointY + circleRadius + labelOffset
        val top = if (aboveTop >= 0f) {
            aboveTop
        } else {
            min(belowTop, height.toFloat() - labelHeight)
        }
        val left = (pointX - labelWidth / 2f).coerceIn(0f, max(0f, width.toFloat() - labelWidth))
        val rect = RectF(left, top, left + labelWidth, top + labelHeight)
        canvas.drawRoundRect(rect, 10f * density, 10f * density, labelBgPaint)
        val baseline = rect.top + labelPaddingY + textHeight * 0.78f
        canvas.drawText(text, rect.left + labelPaddingX, baseline, labelTextPaint)
    }

    private fun drawControls(canvas: Canvas) {
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

        val buttonBaseline = cancelRect.centerY() -
            (coordinateTextPaint.ascent() + coordinateTextPaint.descent()) / 2f
        canvas.drawText(context.getString(R.string.cancel), cancelRect.centerX(), buttonBaseline, coordinateTextPaint)
        canvas.drawText(context.getString(R.string.save), saveRect.centerX(), buttonBaseline, coordinateTextPaint)
        canvas.drawText(coordinateText(), barRect.centerX(), buttonBaseline, coordinateTextPaint)
    }

    private fun clampPoint() {
        if (width <= 0 || height <= 0) return
        pointX = pointX.coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat())
        pointY = pointY.coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat())
    }

    private fun coordinateText(): String {
        val x = screenX()
        val y = screenY()
        return coordinateFormatter?.invoke(x, y) ?: "$x, $y"
    }

    private fun screenX(): Int {
        getLocationOnScreen(locationOnScreen)
        return (locationOnScreen[0] + pointX).toInt()
    }

    private fun screenY(): Int {
        getLocationOnScreen(locationOnScreen)
        return (locationOnScreen[1] + pointY).toInt()
    }

    companion object {
        private const val IDLE_SHOW_BUTTONS_MS = 350L
    }
}
