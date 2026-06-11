package com.fffcccdfgh.androidclicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class SwipePickerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var coordinateFormatter: ((Int, Int, Int, Int, Boolean) -> String)? = null
        set(value) {
            field = value
            invalidate()
        }
    var onSaveSwipe: ((Int, Int, Int, Int) -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val idleHandler = Handler(Looper.getMainLooper())
    private val showButtonsRunnable = Runnable {
        buttonsVisible = true
        invalidate()
    }

    private val startPoint = PointF()
    private val endPoint = PointF()
    private var hasPoints = false
    private var activePoint = ActivePoint.START
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var dragDistance = 0f
    private var dragging = false
    private var buttonsVisible = true

    private val circleRadius = 18f * density
    private val dotRadius = 3f * density
    private val touchRadius = 32f * density
    private val controlHeight = 40f * density
    private val controlMargin = 12f * density
    private val smallButtonWidth = 62f * density
    private val smallButtonHeight = 30f * density

    private val saveRect = RectF()
    private val cancelRect = RectF()
    private val arrowPath = Path()
    private val locationOnScreen = IntArray(2)

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = Color.WHITE
    }
    private val inactiveCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = 0xB3FFFFFF.toInt()
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
    private val inactiveCircleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x222563EB
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF2563EB.toInt()
    }
    private val inactiveDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xB32563EB.toInt()
    }
    private val arrowShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0x80FFFFFF.toInt()
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xFF60A5FA.toInt()
    }
    private val arrowHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFACC15.toInt()
    }
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xD91E293B.toInt()
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
        if (!hasPoints && w > 0 && h > 0) {
            startPoint.set(w * 0.4f, h * 0.4f)
            endPoint.set(w * 0.6f, h * 0.6f)
            hasPoints = true
        } else if (w > 0 && h > 0) {
            clampPoint(startPoint)
            clampPoint(endPoint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!hasPoints) return
        drawArrow(canvas)
        drawPoint(canvas, startPoint, activePoint == ActivePoint.START)
        drawPoint(canvas, endPoint, activePoint == ActivePoint.END)
        if (buttonsVisible && !dragging) {
            drawControls(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                idleHandler.removeCallbacks(showButtonsRunnable)
                if (buttonsVisible && saveRect.contains(event.x, event.y)) {
                    onSaveSwipe?.invoke(screenX(startPoint), screenY(startPoint), screenX(endPoint), screenY(endPoint))
                    return true
                }
                if (buttonsVisible && cancelRect.contains(event.x, event.y)) {
                    onCancel?.invoke()
                    return true
                }
                selectPointIfTouched(event.x, event.y)
                lastTouchX = event.x
                lastTouchY = event.y
                dragDistance = 0f
                dragging = true
                invalidate()
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
                activePointRef().offset(dx, dy)
                clampPoint(activePointRef())
                lastTouchX = event.x
                lastTouchY = event.y
                postInvalidateOnAnimation()
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
        onSaveSwipe = null
        onCancel = null
        coordinateFormatter = null
    }

    private fun drawArrow(canvas: Canvas) {
        val dx = endPoint.x - startPoint.x
        val dy = endPoint.y - startPoint.y
        val distance = hypot(dx, dy)
        if (distance <= 1f) return

        val ux = dx / distance
        val uy = dy / distance
        val fromX = startPoint.x + ux * circleRadius
        val fromY = startPoint.y + uy * circleRadius
        val toX = endPoint.x - ux * circleRadius
        val toY = endPoint.y - uy * circleRadius

        canvas.drawLine(fromX, fromY, toX, toY, arrowShadowPaint)
        canvas.drawLine(fromX, fromY, toX, toY, arrowPaint)
        drawArrowHead(canvas, fromX, fromY, toX, toY)
    }

    private fun drawArrowHead(canvas: Canvas, fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val angle = atan2(toY - fromY, toX - fromX)
        val headLength = 14f * density
        val headSpread = 0.72f

        val leftX = toX - headLength * cos(angle - headSpread)
        val leftY = toY - headLength * sin(angle - headSpread)
        val rightX = toX - headLength * cos(angle + headSpread)
        val rightY = toY - headLength * sin(angle + headSpread)

        arrowPath.reset()
        arrowPath.moveTo(toX, toY)
        arrowPath.lineTo(leftX, leftY)
        arrowPath.lineTo(rightX, rightY)
        arrowPath.close()
        canvas.drawPath(arrowPath, arrowHeadPaint)
    }

    private fun drawPoint(canvas: Canvas, point: PointF, active: Boolean) {
        canvas.drawCircle(point.x, point.y, circleRadius, if (active) circleFillPaint else inactiveCircleFillPaint)
        canvas.drawCircle(point.x, point.y, circleRadius, if (active) circlePaint else inactiveCirclePaint)
        if (active) {
            canvas.drawCircle(point.x, point.y, circleRadius + 4f * density, selectedCirclePaint)
        }
        canvas.drawCircle(point.x, point.y, dotRadius, if (active) dotPaint else inactiveDotPaint)
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

    private fun selectPointIfTouched(x: Float, y: Float) {
        val startDistance = hypot(x - startPoint.x, y - startPoint.y)
        val endDistance = hypot(x - endPoint.x, y - endPoint.y)
        if (startDistance <= touchRadius || endDistance <= touchRadius) {
            activePoint = if (startDistance <= endDistance) ActivePoint.START else ActivePoint.END
        }
    }

    private fun activePointRef(): PointF {
        return if (activePoint == ActivePoint.START) startPoint else endPoint
    }

    private fun clampPoint(point: PointF) {
        if (width <= 0 || height <= 0) return
        point.x = point.x.coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat())
        point.y = point.y.coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat())
    }

    private fun coordinateText(): String {
        return coordinateFormatter?.invoke(
            screenX(startPoint),
            screenY(startPoint),
            screenX(endPoint),
            screenY(endPoint),
            activePoint == ActivePoint.START
        ) ?: "${screenX(activePointRef())}, ${screenY(activePointRef())}"
    }

    private fun screenX(point: PointF): Int {
        getLocationOnScreen(locationOnScreen)
        return (locationOnScreen[0] + point.x).toInt()
    }

    private fun screenY(point: PointF): Int {
        getLocationOnScreen(locationOnScreen)
        return (locationOnScreen[1] + point.y).toInt()
    }

    private enum class ActivePoint { START, END }

    companion object {
        private const val IDLE_SHOW_BUTTONS_MS = 350L
    }
}
