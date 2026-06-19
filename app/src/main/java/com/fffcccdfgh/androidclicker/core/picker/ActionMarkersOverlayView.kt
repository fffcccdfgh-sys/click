package com.fffcccdfgh.androidclicker.core.picker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
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

class ActionMarkersOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class PointType { TAP, SWIPE_START, SWIPE_END, WAIT, PROGRAM }

    data class Marker(
        val actionIndex: Int,
        val pointType: PointType,
        var x: Float,
        var y: Float,
        val label: String
    )

    var onMarkerClick: ((Int) -> Unit)? = null
    var onMarkerMoved: ((Int, PointType, Int, Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val circleRadius = 18f * density
    private val selectedRadius = circleRadius + 4f * density
    private val dotRadius = 3f * density
    private val labelOffset = 10f * density
    private val labelPaddingX = 8f * density
    private val labelPaddingY = 5f * density
    private val hitRadius = 32f * density
    private val arrowPath = Path()
    private val labelRect = RectF()
    private var markers: List<Marker> = emptyList()
    private var activeMarker: Marker? = null
    private var selectedMarker: Marker? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragDistance = 0f

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
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * density
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }

    fun setMarkers(newMarkers: List<Marker>) {
        markers = newMarkers
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawSwipeArrows(canvas)
        for (marker in markers) {
            drawMarker(canvas, marker, marker == activeMarker || marker == selectedMarker)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = hitMarker(event.x, event.y)
                if (hit != null) {
                    selectedMarker = hit
                }
                val marker = hit ?: selectedMarker ?: return false
                activeMarker = marker
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                dragDistance = 0f
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val marker = activeMarker ?: return false
                val dx = event.x - lastX
                val dy = event.y - lastY
                dragDistance += abs(dx) + abs(dy)
                marker.x = (marker.x + dx).coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat())
                marker.y = (marker.y + dy).coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat())
                lastX = event.x
                lastY = event.y
                postInvalidateOnAnimation()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val marker = activeMarker ?: return false
                activeMarker = null
                val moved = dragDistance > touchSlop ||
                    abs(event.x - downX) > touchSlop ||
                    abs(event.y - downY) > touchSlop
                if (moved) {
                    onMarkerMoved?.invoke(
                        marker.actionIndex,
                        marker.pointType,
                        marker.x.toInt(),
                        marker.y.toInt()
                    )
                } else {
                    onMarkerClick?.invoke(marker.actionIndex)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                activeMarker = null
                invalidate()
                return true
            }
        }
        return false
    }

    fun cleanup() {
        onMarkerClick = null
        onMarkerMoved = null
        markers = emptyList()
        activeMarker = null
        selectedMarker = null
    }

    private fun drawSwipeArrows(canvas: Canvas) {
        val grouped = markers.groupBy { it.actionIndex }
        for (group in grouped.values) {
            val start = group.firstOrNull { it.pointType == PointType.SWIPE_START } ?: continue
            val end = group.firstOrNull { it.pointType == PointType.SWIPE_END } ?: continue
            drawArrow(canvas, PointF(start.x, start.y), PointF(end.x, end.y))
        }
    }

    private fun drawArrow(canvas: Canvas, start: PointF, end: PointF) {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val distance = hypot(dx, dy)
        if (distance <= 1f) return

        val ux = dx / distance
        val uy = dy / distance
        val fromX = start.x + ux * circleRadius
        val fromY = start.y + uy * circleRadius
        val toX = end.x - ux * circleRadius
        val toY = end.y - uy * circleRadius

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

    private fun drawMarker(canvas: Canvas, marker: Marker, active: Boolean) {
        canvas.drawCircle(marker.x, marker.y, circleRadius, circleFillPaint)
        canvas.drawCircle(marker.x, marker.y, circleRadius, circlePaint)
        if (active || marker.pointType == PointType.WAIT || marker.pointType == PointType.PROGRAM) {
            canvas.drawCircle(marker.x, marker.y, selectedRadius, selectedCirclePaint)
        } else {
            canvas.drawCircle(marker.x, marker.y, selectedRadius, selectedCirclePaint)
        }
        canvas.drawCircle(marker.x, marker.y, dotRadius, dotPaint)
        drawLabel(canvas, marker)
    }

    private fun drawLabel(canvas: Canvas, marker: Marker) {
        labelRect.set(labelRectFor(marker))
        canvas.drawRoundRect(labelRect, 10f * density, 10f * density, labelBgPaint)
        val textHeight = labelTextPaint.textSize
        val baseline = labelRect.top + labelPaddingY + textHeight * 0.78f
        canvas.drawText(marker.label, labelRect.left + labelPaddingX, baseline, labelTextPaint)
    }

    private fun hitMarker(x: Float, y: Float): Marker? {
        return markers.asReversed().firstOrNull { marker ->
            hypot(x - marker.x, y - marker.y) <= hitRadius || hitLabel(marker, x, y)
        }
    }

    private fun hitLabel(marker: Marker, x: Float, y: Float): Boolean {
        val rect = labelRectFor(marker)
        return rect.contains(x, y)
    }

    private fun labelRectFor(marker: Marker): RectF {
        val textWidth = labelTextPaint.measureText(marker.label)
        val textHeight = labelTextPaint.textSize
        val labelWidth = textWidth + labelPaddingX * 2f
        val labelHeight = textHeight + labelPaddingY * 2f
        val rightLeft = marker.x + selectedRadius + labelOffset
        val leftLeft = marker.x - selectedRadius - labelOffset - labelWidth
        val left = if (rightLeft + labelWidth <= width) {
            rightLeft
        } else if (leftLeft >= 0f) {
            leftLeft
        } else {
            (marker.x - labelWidth / 2f).coerceIn(0f, max(0f, width.toFloat() - labelWidth))
        }
        val top = (marker.y - labelHeight / 2f).coerceIn(0f, max(0f, height.toFloat() - labelHeight))
        return RectF(left, top, left + labelWidth, top + labelHeight)
    }
}
