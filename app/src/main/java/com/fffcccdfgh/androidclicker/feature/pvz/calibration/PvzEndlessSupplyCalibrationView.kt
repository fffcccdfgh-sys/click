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
import kotlin.math.roundToInt

data class PvzCalibrationArea(
    val label: String,
    val rect: RectF
)

class PvzEndlessSupplyCalibrationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onSave: ((PvzCalibrationArea, List<PvzCalibrationPoint>) -> Unit)? = null
    var onCancel: (() -> Unit)? = null
    var colorSampler: ((Int, Int) -> Int?)? = null
        set(value) {
            field = value
            refreshPointPreviewColors()
        }

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val idleHandler = Handler(Looper.getMainLooper())
    private val showButtonsRunnable = Runnable {
        buttonsVisible = true
        invalidate()
    }

    private var area = PvzCalibrationArea("", RectF())
    private val points = mutableListOf<PvzCalibrationPoint>()
    private var activeTarget: ActiveTarget = ActiveTarget.None
    private var areaSelected = false
    private var selectedPointIndex = -1
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var dragDistance = 0f
    private var buttonsVisible = true

    private val circleRadius = 18f * density
    private val dotRadius = 3f * density
    private val labelOffset = 12f * density
    private val labelPaddingX = 8f * density
    private val labelPaddingY = 5f * density
    private val colorPreviewSize = 12f * density
    private val colorPreviewGap = 6f * density
    private val handleRadius = 11f * density
    private val controlHeight = 40f * density
    private val controlMargin = 12f * density
    private val smallButtonWidth = 62f * density
    private val smallButtonHeight = 30f * density

    private val areaFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x2214B8A6
    }
    private val areaStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = 0xFF14B8A6.toInt()
    }
    private val areaSelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density
        color = 0xFFFACC15.toInt()
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF14B8A6.toInt()
    }
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
    private val colorPreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val colorPreviewBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = 0x99FFFFFF.toInt()
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

    fun setCalibration(newArea: PvzCalibrationArea, newPoints: List<PvzCalibrationPoint>) {
        area = PvzCalibrationArea(newArea.label, RectF(newArea.rect))
        points.clear()
        points.addAll(newPoints.map { it.copy() })
        refreshPointPreviewColors()
        selectedPointIndex = selectedPointIndex.takeIf { it in points.indices } ?: -1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawArea(canvas)
        for ((index, point) in points.withIndex()) {
            drawPoint(canvas, point, index == selectedPointIndex)
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
                    onSave?.invoke(
                        PvzCalibrationArea(area.label, RectF(area.rect)),
                        points.map { it.copy() }
                    )
                    return true
                }
                if (buttonsVisible && cancelRect.contains(event.x, event.y)) {
                    onCancel?.invoke()
                    return true
                }
                val touchedTarget = hitTarget(event.x, event.y)
                activeTarget = when (touchedTarget) {
                    is ActiveTarget.Point -> {
                        selectedPointIndex = touchedTarget.index
                        areaSelected = false
                        touchedTarget
                    }
                    ActiveTarget.Area -> {
                        selectedPointIndex = -1
                        areaSelected = true
                        touchedTarget
                    }
                    is ActiveTarget.Corner -> {
                        selectedPointIndex = -1
                        areaSelected = true
                        touchedTarget
                    }
                    ActiveTarget.None -> {
                        when {
                            selectedPointIndex in points.indices ->
                                ActiveTarget.Point(selectedPointIndex)
                            areaSelected -> ActiveTarget.Area
                            else -> ActiveTarget.None
                        }
                    }
                }
                if (activeTarget == ActiveTarget.None) return false
                lastTouchX = event.x
                lastTouchY = event.y
                dragDistance = 0f
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                dragDistance += abs(dx) + abs(dy)
                if (dragDistance > touchSlop && buttonsVisible) {
                    buttonsVisible = false
                }
                moveActiveTarget(dx, dy)
                lastTouchX = event.x
                lastTouchY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeTarget = ActiveTarget.None
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
        colorSampler = null
    }

    private fun drawArea(canvas: Canvas) {
        canvas.drawRect(area.rect, areaFillPaint)
        canvas.drawRect(area.rect, areaStrokePaint)
        if (areaSelected || activeTarget is ActiveTarget.Area || activeTarget is ActiveTarget.Corner) {
            canvas.drawRect(area.rect, areaSelectedPaint)
        }
        drawAreaHandles(canvas)
        drawAreaLabel(canvas)
    }

    private fun drawAreaHandles(canvas: Canvas) {
        val rect = area.rect
        canvas.drawCircle(rect.left, rect.top, handleRadius, handlePaint)
        canvas.drawCircle(rect.right, rect.top, handleRadius, handlePaint)
        canvas.drawCircle(rect.left, rect.bottom, handleRadius, handlePaint)
        canvas.drawCircle(rect.right, rect.bottom, handleRadius, handlePaint)
    }

    private fun drawAreaLabel(canvas: Canvas) {
        val rect = area.rect
        val textWidth = labelTextPaint.measureText(area.label)
        val textHeight = labelTextPaint.textSize
        val labelWidth = textWidth + labelPaddingX * 2f
        val labelHeight = textHeight + labelPaddingY * 2f
        val left = rect.left.coerceIn(0f, max(0f, width.toFloat() - labelWidth))
        val top = (rect.top - labelOffset - labelHeight).takeIf { it >= 0f }
            ?: (rect.bottom + labelOffset).coerceAtMost(height.toFloat() - labelHeight)
        val labelRect = RectF(left, top, left + labelWidth, top + labelHeight)
        canvas.drawRoundRect(labelRect, 10f * density, 10f * density, labelBgPaint)
        val baseline = labelRect.top + labelPaddingY + textHeight * 0.78f
        canvas.drawText(area.label, labelRect.left + labelPaddingX, baseline, labelTextPaint)
    }

    private fun drawPoint(canvas: Canvas, point: PvzCalibrationPoint, selected: Boolean) {
        canvas.drawCircle(point.x, point.y, circleRadius, circleFillPaint)
        canvas.drawCircle(point.x, point.y, circleRadius, circlePaint)
        if (selected) {
            canvas.drawCircle(point.x, point.y, circleRadius + 4f * density, selectedCirclePaint)
        }
        canvas.drawCircle(point.x, point.y, dotRadius, dotPaint)
        drawPointLabel(canvas, point)
    }

    private fun drawPointLabel(canvas: Canvas, point: PvzCalibrationPoint) {
        val previewColor = point.previewColor
        val textWidth = labelTextPaint.measureText(point.label)
        val textHeight = labelTextPaint.textSize
        val previewWidth = if (previewColor != null) {
            colorPreviewSize + colorPreviewGap
        } else {
            0f
        }
        val labelWidth = previewWidth + textWidth + labelPaddingX * 2f
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
        val textX = if (previewColor != null) {
            val previewLeft = rect.left + labelPaddingX
            val previewTop = rect.top + (labelHeight - colorPreviewSize) / 2f
            colorPreviewPaint.color = previewColor
            canvas.drawRect(
                previewLeft,
                previewTop,
                previewLeft + colorPreviewSize,
                previewTop + colorPreviewSize,
                colorPreviewPaint
            )
            canvas.drawRect(
                previewLeft,
                previewTop,
                previewLeft + colorPreviewSize,
                previewTop + colorPreviewSize,
                colorPreviewBorderPaint
            )
            previewLeft + colorPreviewSize + colorPreviewGap
        } else {
            rect.left + labelPaddingX
        }
        val baseline = rect.top + labelPaddingY + textHeight * 0.78f
        canvas.drawText(point.label, textX, baseline, labelTextPaint)
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

    private fun hitTarget(x: Float, y: Float): ActiveTarget {
        hitPoint(x, y).takeIf { it >= 0 }?.let { return ActiveTarget.Point(it) }
        hitCorner(x, y)?.let { return ActiveTarget.Corner(it) }
        if (area.rect.contains(x, y)) return ActiveTarget.Area
        return ActiveTarget.None
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

    private fun hitCorner(x: Float, y: Float): Corner? {
        val rect = area.rect
        val hitRadius = handleRadius * 2f
        return when {
            abs(x - rect.left) <= hitRadius && abs(y - rect.top) <= hitRadius -> Corner.TopLeft
            abs(x - rect.right) <= hitRadius && abs(y - rect.top) <= hitRadius -> Corner.TopRight
            abs(x - rect.left) <= hitRadius && abs(y - rect.bottom) <= hitRadius -> Corner.BottomLeft
            abs(x - rect.right) <= hitRadius && abs(y - rect.bottom) <= hitRadius -> Corner.BottomRight
            else -> null
        }
    }

    private fun moveActiveTarget(dx: Float, dy: Float) {
        when (val target = activeTarget) {
            ActiveTarget.Area -> moveArea(dx, dy)
            is ActiveTarget.Corner -> resizeArea(target.corner, dx, dy)
            is ActiveTarget.Point -> movePoint(target.index, dx, dy)
            ActiveTarget.None -> Unit
        }
    }

    private fun moveArea(dx: Float, dy: Float) {
        val rect = area.rect
        val clampedDx = dx.coerceIn(-rect.left, width.toFloat() - rect.right)
        val clampedDy = dy.coerceIn(-rect.top, height.toFloat() - rect.bottom)
        rect.offset(clampedDx, clampedDy)
    }

    private fun resizeArea(corner: Corner, dx: Float, dy: Float) {
        val rect = area.rect
        val resized = PvzCalibrationAreaResizePolicy.resize(
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
            corner = corner.toPolicyCorner(),
            dx = dx,
            dy = dy,
            width = width.toFloat(),
            height = height.toFloat()
        )
        rect.set(resized.left, resized.top, resized.right, resized.bottom)
    }

    private fun movePoint(index: Int, dx: Float, dy: Float) {
        if (index !in points.indices) return
        points[index].x = (points[index].x + dx).coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat())
        points[index].y = (points[index].y + dy).coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat())
        refreshPointPreviewColor(index)
    }

    private fun refreshPointPreviewColors() {
        for (index in points.indices) {
            refreshPointPreviewColor(index)
        }
        invalidate()
    }

    private fun refreshPointPreviewColor(index: Int) {
        val sampler = colorSampler
        val point = points.getOrNull(index) ?: return
        if (!PvzCalibrationPointColorPreviewPolicy.shouldShowColorPreview(point.key)) {
            points[index] = point.copy(previewColor = null)
            return
        }
        points[index] = point.copy(
            previewColor = sampler?.invoke(point.x.roundToInt(), point.y.roundToInt())
        )
    }

    private sealed class ActiveTarget {
        data object None : ActiveTarget()
        data object Area : ActiveTarget()
        data class Corner(val corner: PvzEndlessSupplyCalibrationView.Corner) : ActiveTarget()
        data class Point(val index: Int) : ActiveTarget()
    }

    private enum class Corner {
        TopLeft,
        TopRight,
        BottomLeft,
        BottomRight
    }

    private fun Corner.toPolicyCorner(): PvzCalibrationAreaCorner {
        return when (this) {
            Corner.TopLeft -> PvzCalibrationAreaCorner.TopLeft
            Corner.TopRight -> PvzCalibrationAreaCorner.TopRight
            Corner.BottomLeft -> PvzCalibrationAreaCorner.BottomLeft
            Corner.BottomRight -> PvzCalibrationAreaCorner.BottomRight
        }
    }

    companion object {
        private const val IDLE_SHOW_BUTTONS_MS = 350L
    }
}
