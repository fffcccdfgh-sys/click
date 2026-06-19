package com.fffcccdfgh.androidclicker.core.picker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.fffcccdfgh.androidclicker.R

class ColorPickerOverlayView(context: Context) : FrameLayout(context) {

    companion object {
        private const val CIRCLE_RADIUS_DP = 14f
        private const val DOT_RADIUS_DP = 2f
        private const val HEX_LABEL_OFFSET_DP = 36f
        private const val COLOR_PREVIEW_SIZE_DP = 12f
        private const val COLOR_PREVIEW_GAP_DP = 6f
        private const val IDLE_SHOW_BUTTONS_MS = 500L
        private const val SAMPLE_THROTTLE_MS = 80L
        private const val BUTTON_BOTTOM_MARGIN_DP = 12f
        private const val BUTTON_WIDTH_DP = 62f
        private const val BUTTON_HEIGHT_DP = 30f
        private const val BUTTON_BAR_HEIGHT_DP = 40f
    }

    private var markerCx = 0f
    private var markerCy = 0f
    private var markerScreenX = 0
    private var markerScreenY = 0
    private var currentHex = ""
    private var parsedPreviewColor = Color.WHITE

    private var touchStartedOnButton = false
    private var startTouchRawX = 0f
    private var startTouchRawY = 0f
    private var startMarkerX = 0f
    private var startMarkerY = 0f
    private var dragMoveDistance = 0f
    private var buttonsVisible = true
    private val touchSlop: Float

    private val confirmHitRect = Rect()
    private val cancelHitRect = Rect()

    private val dimPaint: Paint
    private val circleStrokePaint: Paint
    private val dotFillPaint: Paint
    private val dotStrokePaint: Paint
    private val hexBgPaint: Paint
    private val hexTextPaint: Paint
    private val colorPreviewPaint: Paint
    private val colorPreviewBorderPaint: Paint
    private val hintBgPaint: Paint
    private val hintTextPaint: Paint

    private val circleRadius: Float
    private val dotRadius: Float
    private val hexLabelOffset: Float
    private val colorPreviewSize: Float
    private val colorPreviewGap: Float
    private val density: Float

    private val confirmBtn: TextView
    private val cancelBtn: TextView
    private val buttonContainer: LinearLayout

    private val idleHandler = Handler(Looper.getMainLooper())
    private val showButtonsRunnable = Runnable {
        showButtons()
    }

    private var lastSampleTime = 0L

    var onConfirm: ((screenX: Int, screenY: Int, hex: String) -> Unit)? = null
    var onCancel: (() -> Unit)? = null
    var colorSampler: ((screenX: Int, screenY: Int) -> String?)? = null

    init {
        density = context.resources.displayMetrics.density
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

        circleRadius = CIRCLE_RADIUS_DP * density
        dotRadius = DOT_RADIUS_DP * density
        hexLabelOffset = HEX_LABEL_OFFSET_DP * density
        colorPreviewSize = COLOR_PREVIEW_SIZE_DP * density
        colorPreviewGap = COLOR_PREVIEW_GAP_DP * density

        setBackgroundColor(Color.TRANSPARENT)
        setWillNotDraw(false)

        dimPaint = Paint().apply {
            style = Paint.Style.FILL
            color = 0x33000000
        }

        circleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            color = Color.WHITE
            setShadowLayer(3f * density, 0f, 0f, Color.BLACK)
        }

        dotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.RED
        }
        dotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
            color = Color.WHITE
            setShadowLayer(2f * density, 0f, 0f, Color.BLACK)
        }

        hexBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xDD1A1A1A.toInt()
        }
        hexTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 13f * density
            typeface = Typeface.MONOSPACE
        }
        colorPreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        colorPreviewBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
            color = Color.WHITE
        }
        hintBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xAA000000.toInt()
        }
        hintTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f * density
            typeface = Typeface.DEFAULT_BOLD
        }

        val btnCorner = 8f * density

        confirmBtn = TextView(context).apply {
            text = context.getString(R.string.save)
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xE616A34A.toInt())
                cornerRadius = btnCorner
            }
        }
        cancelBtn = TextView(context).apply {
            text = context.getString(R.string.cancel)
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xE6DC2626.toInt())
                cornerRadius = btnCorner
            }
        }

        buttonContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(
                (5 * density).toInt(),
                (5 * density).toInt(),
                (5 * density).toInt(),
                (5 * density).toInt()
            )
            background = GradientDrawable().apply {
                setColor(0xCC111827.toInt())
                cornerRadius = 12f * density
            }
        }
        buttonContainer.addView(cancelBtn, LinearLayout.LayoutParams(
            (BUTTON_WIDTH_DP * density).toInt(), (BUTTON_HEIGHT_DP * density).toInt()
        ))
        buttonContainer.addView(View(context), LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
        ))
        buttonContainer.addView(confirmBtn, LinearLayout.LayoutParams(
            (BUTTON_WIDTH_DP * density).toInt(), (BUTTON_HEIGHT_DP * density).toInt()
        ))

        addView(buttonContainer, LayoutParams(
            LayoutParams.MATCH_PARENT, (BUTTON_BAR_HEIGHT_DP * density).toInt(),
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            bottomMargin = (BUTTON_BOTTOM_MARGIN_DP * density).toInt()
            leftMargin = (BUTTON_BOTTOM_MARGIN_DP * density).toInt()
            rightMargin = (BUTTON_BOTTOM_MARGIN_DP * density).toInt()
        })

        setOnTouchListener { _, event -> handleTouch(event) }
    }

    fun setInitialPosition(x: Int, y: Int, hex: String) {
        markerCx = x.toFloat()
        markerCy = y.toFloat()
        markerScreenX = x
        markerScreenY = y
        currentHex = hex
        parsedPreviewColor = parseHexSafe(hex)
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        confirmBtn.getHitRect(confirmHitRect)
        confirmHitRect.offset(buttonContainer.left, buttonContainer.top)

        cancelBtn.getHitRect(cancelHitRect)
        cancelHitRect.offset(buttonContainer.left, buttonContainer.top)

        updateMarkerScreenPosition()
        sampleCurrentMarkerColor(force = true)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        drawHint(canvas)

        canvas.drawCircle(markerCx, markerCy, circleRadius, circleStrokePaint)
        canvas.drawCircle(markerCx, markerCy, dotRadius, dotFillPaint)
        canvas.drawCircle(markerCx, markerCy, dotRadius, dotStrokePaint)

        if (currentHex.isNotEmpty()) {
            drawHexLabel(canvas)
        }
    }

    private fun drawHint(canvas: Canvas) {
        val label = "拖动圆心取色"
        val padH = 8f * density
        val padV = 5f * density
        val textW = hintTextPaint.measureText(label)
        val fm = hintTextPaint.fontMetrics
        val h = (fm.descent - fm.ascent) + padV * 2
        val left = 8f * density
        val top = 8f * density
        canvas.drawRoundRect(
            left,
            top,
            left + textW + padH * 2,
            top + h,
            6f * density,
            6f * density,
            hintBgPaint
        )
        val textY = top + h / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(label, left + padH, textY, hintTextPaint)
    }

    private fun drawHexLabel(canvas: Canvas) {
        val hexText = currentHex
        val hexTextWidth = hexTextPaint.measureText(hexText)
        val fm = hexTextPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val padH = 10f * density
        val padV = 6f * density

        val totalWidth = padH + colorPreviewSize + colorPreviewGap + hexTextWidth + padH
        val labelHeight = textHeight + padV * 2

        val screenW = width.toFloat()

        val bgLeft: Float
        if (markerCx + totalWidth / 2 > screenW) {
            bgLeft = screenW - totalWidth - 4f * density
        } else if (markerCx - totalWidth / 2 < 0) {
            bgLeft = 4f * density
        } else {
            bgLeft = markerCx - totalWidth / 2
        }
        val bgRight = bgLeft + totalWidth

        val prefBgTop = markerCy - circleRadius - hexLabelOffset
        val bgTop: Float
        val bgBottom: Float
        if (prefBgTop < 0) {
            bgTop = markerCy + circleRadius + hexLabelOffset
            bgBottom = bgTop + labelHeight
        } else {
            bgTop = prefBgTop
            bgBottom = bgTop + labelHeight
        }

        val corner = 6f * density
        canvas.drawRoundRect(bgLeft, bgTop, bgRight, bgBottom, corner, corner, hexBgPaint)

        val previewLeft = bgLeft + padH
        val previewTop = bgTop + (labelHeight - colorPreviewSize) / 2f
        val previewRight = previewLeft + colorPreviewSize
        val previewBottom = previewTop + colorPreviewSize

        colorPreviewPaint.color = parsedPreviewColor
        canvas.drawRect(previewLeft, previewTop, previewRight, previewBottom, colorPreviewPaint)
        canvas.drawRect(previewLeft, previewTop, previewRight, previewBottom, colorPreviewBorderPaint)

        val textX = previewRight + colorPreviewGap
        val textCenterY = (bgTop + bgBottom) / 2f
        val textY = textCenterY - (fm.ascent + fm.descent) / 2f
        canvas.drawText(hexText, textX, textY, hexTextPaint)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartedOnButton = buttonsVisible && isPointOnButtons(event.x, event.y)
                if (touchStartedOnButton) {
                    return true
                }

                startTouchRawX = event.rawX
                startTouchRawY = event.rawY
                startMarkerX = markerCx
                startMarkerY = markerCy
                dragMoveDistance = 0f
                idleHandler.removeCallbacks(showButtonsRunnable)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchStartedOnButton) return true

                val dx = event.rawX - startTouchRawX
                val dy = event.rawY - startTouchRawY
                dragMoveDistance = Math.abs(dx) + Math.abs(dy)

                if (buttonsVisible &&
                    PickerActionButtonsVisibilityPolicy.shouldHideWhileChanging(
                        dragMoveDistance,
                        touchSlop
                    )
                ) {
                    hideButtons()
                }

                val newX = startMarkerX + dx
                val newY = startMarkerY + dy
                updateMarkerPosition(newX, newY)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (touchStartedOnButton) {
                    if (event.action == MotionEvent.ACTION_UP) {
                        if (confirmHitRect.contains(event.x.toInt(), event.y.toInt())) {
                            onConfirm?.invoke(markerScreenX, markerScreenY, currentHex)
                        } else if (cancelHitRect.contains(event.x.toInt(), event.y.toInt())) {
                            onCancel?.invoke()
                        }
                    }
                    return true
                }

                when (
                    PickerActionButtonsVisibilityPolicy.showTimingAfterTouchEnd(
                        dragMoveDistance,
                        touchSlop
                    )
                ) {
                    PickerActionButtonsVisibilityPolicy.ShowTiming.NOW -> showButtons()
                    PickerActionButtonsVisibilityPolicy.ShowTiming.AFTER_IDLE_DELAY -> {
                        idleHandler.postDelayed(showButtonsRunnable, IDLE_SHOW_BUTTONS_MS)
                    }
                }
                return true
            }
        }
        return false
    }

    private fun showButtons() {
        buttonsVisible = true
        buttonContainer.visibility = View.VISIBLE
    }

    private fun hideButtons() {
        buttonsVisible = false
        buttonContainer.visibility = View.GONE
    }

    private fun isPointOnButtons(x: Float, y: Float): Boolean {
        return confirmHitRect.contains(x.toInt(), y.toInt()) ||
                cancelHitRect.contains(x.toInt(), y.toInt())
    }

    private fun updateMarkerPosition(x: Float, y: Float) {
        markerCx = x.coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat())
        markerCy = y.coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat())
        updateMarkerScreenPosition()
        sampleCurrentMarkerColor(force = false)
        invalidate()
    }

    private fun updateMarkerScreenPosition() {
        val location = IntArray(2)
        getLocationOnScreen(location)
        markerScreenX = Math.round(markerCx + location[0])
        markerScreenY = Math.round(markerCy + location[1])
    }

    private fun sampleCurrentMarkerColor(force: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastSampleTime < SAMPLE_THROTTLE_MS) {
            return
        }
        lastSampleTime = now
        colorSampler?.invoke(markerScreenX, markerScreenY)?.let { hex ->
            currentHex = hex
            parsedPreviewColor = parseHexSafe(hex)
        }
    }

    private fun parseHexSafe(hex: String): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            Color.WHITE
        }
    }

    fun cleanup() {
        idleHandler.removeCallbacks(showButtonsRunnable)
        onConfirm = null
        onCancel = null
        colorSampler = null
    }
}
