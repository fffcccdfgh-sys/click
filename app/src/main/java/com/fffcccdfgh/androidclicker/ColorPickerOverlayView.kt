package com.fffcccdfgh.androidclicker

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

class ColorPickerOverlayView(context: Context) : FrameLayout(context) {

    companion object {
        private const val CIRCLE_RADIUS_DP = 14f
        private const val DOT_RADIUS_DP = 2f
        private const val HEX_LABEL_OFFSET_DP = 36f
        private const val COLOR_PREVIEW_SIZE_DP = 12f
        private const val COLOR_PREVIEW_GAP_DP = 6f
        private const val IDLE_SHOW_BUTTONS_MS = 500L
        private const val SAMPLE_THROTTLE_MS = 80L
        private const val BUTTON_BOTTOM_MARGIN_DP = 48f
        private const val BUTTON_SPACING_DP = 28f
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

    private val circleStrokePaint: Paint
    private val dotFillPaint: Paint
    private val dotStrokePaint: Paint
    private val hexBgPaint: Paint
    private val hexTextPaint: Paint
    private val colorPreviewPaint: Paint
    private val colorPreviewBorderPaint: Paint

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
        buttonsVisible = true
        buttonContainer.visibility = View.VISIBLE
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

        val btnPadH = (16 * density).toInt()
        val btnPadV = (10 * density).toInt()
        val btnCorner = 8f * density

        confirmBtn = TextView(context).apply {
            text = "确认"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
            background = GradientDrawable().apply {
                setColor(0xEE4CAF50.toInt())
                cornerRadius = btnCorner
            }
        }
        cancelBtn = TextView(context).apply {
            text = "取消"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
            background = GradientDrawable().apply {
                setColor(0xEEF44336.toInt())
                cornerRadius = btnCorner
            }
        }

        buttonContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        buttonContainer.addView(confirmBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginEnd = (BUTTON_SPACING_DP * density).toInt()
        })
        buttonContainer.addView(cancelBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        addView(buttonContainer, LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            bottomMargin = (BUTTON_BOTTOM_MARGIN_DP * density).toInt()
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

        // getHitRect() returns each button's bounds in buttonContainer coordinates,
        // while MotionEvent.x/y are in this overlay's coordinate space.
        // Offset the rects into the overlay coordinate space so button taps are detected.
        confirmBtn.getHitRect(confirmHitRect)
        confirmHitRect.offset(buttonContainer.left, buttonContainer.top)

        cancelBtn.getHitRect(cancelHitRect)
        cancelHitRect.offset(buttonContainer.left, buttonContainer.top)

        updateMarkerScreenPosition()
        sampleCurrentMarkerColor(force = true)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawCircle(markerCx, markerCy, circleRadius, circleStrokePaint)

        canvas.drawCircle(markerCx, markerCy, dotRadius, dotFillPaint)
        canvas.drawCircle(markerCx, markerCy, dotRadius, dotStrokePaint)

        if (currentHex.isNotEmpty()) {
            drawHexLabel(canvas)
        }
    }

    private fun drawHexLabel(canvas: Canvas) {
        val hexText = currentHex
        val hexTextWidth = hexTextPaint.measureText(hexText)
        val fm = hexTextPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val padH = 10f * density
        val padV = 6f * density

        // Total label: [padH][colorSquare][gap][hexText][padH]
        val totalWidth = padH + colorPreviewSize + colorPreviewGap + hexTextWidth + padH
        val labelHeight = textHeight + padV * 2

        val screenW = width.toFloat()

        // Horizontal position with edge avoidance
        val bgLeft: Float
        if (markerCx + totalWidth / 2 > screenW) {
            bgLeft = screenW - totalWidth - 4f * density
        } else if (markerCx - totalWidth / 2 < 0) {
            bgLeft = 4f * density
        } else {
            bgLeft = markerCx - totalWidth / 2
        }
        val bgRight = bgLeft + totalWidth

        // Vertical: prefer above the circle, fall back to below
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

        // Color preview square
        val previewLeft = bgLeft + padH
        val previewTop = bgTop + (labelHeight - colorPreviewSize) / 2f
        val previewRight = previewLeft + colorPreviewSize
        val previewBottom = previewTop + colorPreviewSize

        colorPreviewPaint.color = parsedPreviewColor
        canvas.drawRect(previewLeft, previewTop, previewRight, previewBottom, colorPreviewPaint)
        canvas.drawRect(previewLeft, previewTop, previewRight, previewBottom, colorPreviewBorderPaint)

        // Hex text
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

                if (dragMoveDistance > touchSlop && buttonsVisible) {
                    buttonsVisible = false
                    buttonContainer.visibility = View.GONE
                }

                val newX = startMarkerX + dx
                val newY = startMarkerY + dy
                updateMarkerPosition(newX, newY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (touchStartedOnButton) {
                    if (confirmHitRect.contains(event.x.toInt(), event.y.toInt())) {
                        onConfirm?.invoke(markerScreenX, markerScreenY, currentHex)
                    } else if (cancelHitRect.contains(event.x.toInt(), event.y.toInt())) {
                        onCancel?.invoke()
                    }
                    return true
                }

                if (dragMoveDistance >= touchSlop) {
                    idleHandler.postDelayed(showButtonsRunnable, IDLE_SHOW_BUTTONS_MS)
                }
                return true
            }
        }
        return false
    }

    private fun isPointOnButtons(x: Float, y: Float): Boolean {
        return confirmHitRect.contains(x.toInt(), y.toInt()) ||
                cancelHitRect.contains(x.toInt(), y.toInt())
    }

    private fun updateMarkerPosition(x: Float, y: Float) {
        markerCx = x.coerceIn(circleRadius, width.toFloat() - circleRadius)
        markerCy = y.coerceIn(circleRadius, height.toFloat() - circleRadius)
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
