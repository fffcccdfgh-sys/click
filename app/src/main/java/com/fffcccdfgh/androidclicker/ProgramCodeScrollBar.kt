package com.fffcccdfgh.androidclicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ScrollView
import kotlin.math.max
import kotlin.math.roundToInt

data class ScrollThumb(
    val top: Int,
    val height: Int
)

object ProgramCodeScrollBarMath {
    fun thumb(
        trackHeight: Int,
        contentHeight: Int,
        viewportHeight: Int,
        scrollY: Int
    ): ScrollThumb {
        if (trackHeight <= 0 || contentHeight <= viewportHeight || viewportHeight <= 0) {
            return ScrollThumb(0, trackHeight.coerceAtLeast(0))
        }

        val thumbHeight = (trackHeight.toFloat() * viewportHeight / contentHeight)
            .roundToInt()
            .coerceIn(32, trackHeight)
        val maxThumbTop = (trackHeight - thumbHeight).coerceAtLeast(0)
        val maxScrollY = (contentHeight - viewportHeight).coerceAtLeast(1)
        val thumbTop = (scrollY.coerceIn(0, maxScrollY).toFloat() * maxThumbTop / maxScrollY)
            .toInt()
            .coerceIn(0, maxThumbTop)
        return ScrollThumb(thumbTop, thumbHeight)
    }

    fun scrollYForThumbTop(
        thumbTop: Int,
        trackHeight: Int,
        contentHeight: Int,
        viewportHeight: Int
    ): Int {
        if (trackHeight <= 0 || contentHeight <= viewportHeight || viewportHeight <= 0) {
            return 0
        }

        val thumbHeight = thumb(
            trackHeight = trackHeight,
            contentHeight = contentHeight,
            viewportHeight = viewportHeight,
            scrollY = 0
        ).height
        val maxThumbTop = (trackHeight - thumbHeight).coerceAtLeast(1)
        val maxScrollY = (contentHeight - viewportHeight).coerceAtLeast(0)
        return (thumbTop.coerceIn(0, maxThumbTop).toFloat() * maxScrollY / maxThumbTop)
            .roundToInt()
            .coerceIn(0, maxScrollY)
    }
}

object ProgramTemplateMenuLayout {
    fun popupHeight(
        itemCount: Int,
        itemHeightPx: Int,
        verticalPaddingPx: Int,
        maxVisibleRows: Int
    ): Int {
        val visibleRows = itemCount.coerceAtMost(maxVisibleRows).coerceAtLeast(0)
        return visibleRows * itemHeightPx + verticalPaddingPx
    }
}

object ProgramLineNumberMath {
    fun lineCount(text: CharSequence): Int {
        if (text.isEmpty()) return 1
        var count = 1
        for (char in text) {
            if (char == '\n') count++
        }
        return count
    }
}

class ProgramLineNumberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF64748B.toInt()
        textAlign = Paint.Align.RIGHT
        textSize = 12f * resources.displayMetrics.scaledDensity
        typeface = android.graphics.Typeface.MONOSPACE
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1F2937.toInt()
        strokeWidth = resources.displayMetrics.density
    }

    private var editText: EditText? = null

    fun attachTo(input: EditText) {
        editText = input
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                invalidate()
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        input.viewTreeObserver.addOnScrollChangedListener { invalidate() }
        input.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> invalidate() }
        input.post { invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val input = editText ?: return
        canvas.drawLine(width - 1f, 0f, width - 1f, height.toFloat(), dividerPaint)

        val layout = input.layout
        val lineCount = max(ProgramLineNumberMath.lineCount(input.text), layout?.lineCount ?: 1)
        val textX = width - 8f * resources.displayMetrics.density
        val viewportTop = input.scrollY - input.compoundPaddingTop
        val viewportBottom = viewportTop + input.height

        for (line in 0 until lineCount) {
            val baseline = if (layout != null && line < layout.lineCount) {
                layout.getLineBaseline(line)
            } else {
                val lineHeight = input.lineHeight.coerceAtLeast(1)
                input.compoundPaddingTop + line * lineHeight + input.paint.fontMetricsInt.descent * -1
            }
            if (baseline < viewportTop - input.lineHeight || baseline > viewportBottom + input.lineHeight) {
                continue
            }
            val y = (baseline - input.scrollY + input.compoundPaddingTop).toFloat()
            canvas.drawText((line + 1).toString(), textX, y, textPaint)
        }
    }
}

class ProgramCodeEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : EditText(context, attrs, defStyleAttr) {
    fun verticalScrollRangePx(): Int = computeVerticalScrollRange()
    fun verticalScrollExtentPx(): Int = computeVerticalScrollExtent()
    fun verticalScrollOffsetPx(): Int = computeVerticalScrollOffset()
}

class ProgramCodeScrollBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22000000
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAA555555.toInt()
    }
    private val activeThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC222222.toInt()
    }

    private var editText: EditText? = null
    private var dragging = false
    private var dragOffsetY = 0f

    private data class EditScrollMetrics(
        val range: Int,
        val extent: Int,
        val offset: Int
    )

    fun attachTo(input: EditText) {
        editText = input
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                input.post {
                    clampScrollToContent(input)
                    invalidate()
                }
            }
        })
        input.setOnScrollChangeListener { _, _, _, _, _ -> invalidate() }
        input.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> invalidate() }
        input.post { invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = width / 2f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, trackPaint)

        val thumb = currentThumb()
        if (thumb.height >= height) return
        val paint = if (dragging) activeThumbPaint else thumbPaint
        canvas.drawRoundRect(
            0f,
            thumb.top.toFloat(),
            width.toFloat(),
            (thumb.top + thumb.height).toFloat(),
            radius,
            radius,
            paint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val input = editText ?: return false
        if (!canScroll(input)) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                dragging = true
                val thumb = currentThumb()
                dragOffsetY = if (event.y.toInt() in thumb.top..(thumb.top + thumb.height)) {
                    event.y - thumb.top
                } else {
                    thumb.height / 2f
                }
                scrollToThumbTop((event.y - dragOffsetY).roundToInt())
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                scrollToThumbTop((event.y - dragOffsetY).roundToInt())
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return false
    }

    private fun scrollToThumbTop(thumbTop: Int) {
        val input = editText ?: return
        val metrics = scrollMetrics(input)
        val scrollY = ProgramCodeScrollBarMath.scrollYForThumbTop(
            thumbTop = thumbTop,
            trackHeight = height,
            contentHeight = metrics.range,
            viewportHeight = metrics.extent
        )
        input.scrollTo(0, scrollY)
    }

    private fun currentThumb(): ScrollThumb {
        val input = editText ?: return ScrollThumb(0, height)
        val metrics = scrollMetrics(input)
        return ProgramCodeScrollBarMath.thumb(
            trackHeight = height,
            contentHeight = metrics.range,
            viewportHeight = metrics.extent,
            scrollY = metrics.offset
        )
    }

    private fun canScroll(input: EditText): Boolean {
        val metrics = scrollMetrics(input)
        return metrics.range > metrics.extent ||
            input.canScrollVertically(1) ||
            input.canScrollVertically(-1)
    }

    private fun clampScrollToContent(input: EditText) {
        val metrics = scrollMetrics(input)
        val maxScrollY = (metrics.range - metrics.extent).coerceAtLeast(0)
        if (input.scrollY > maxScrollY) {
            input.scrollTo(0, maxScrollY)
        }
    }

    private fun scrollMetrics(input: EditText): EditScrollMetrics {
        if (input is ProgramCodeEditText) {
            val range = input.verticalScrollRangePx().coerceAtLeast(input.height)
            val extent = input.verticalScrollExtentPx().coerceAtLeast(1)
            return EditScrollMetrics(
                range = range,
                extent = extent,
                offset = input.verticalScrollOffsetPx().coerceAtLeast(0)
            )
        }
        return EditScrollMetrics(
            range = contentHeight(input),
            extent = viewportHeight(input),
            offset = input.scrollY
        )
    }

    private fun contentHeight(input: EditText): Int {
        val layout = input.layout
        val layoutLineHeight = if (layout != null && layout.lineCount > 0) {
            layout.getLineBottom(layout.lineCount - 1)
        } else {
            0
        }
        val measuredLineHeight = (layout?.lineCount ?: 0) * input.lineHeight
        val textLineHeight = ProgramLineNumberMath.lineCount(input.text) * input.lineHeight
        val textHeight = max(max(layout?.height ?: 0, layoutLineHeight), max(measuredLineHeight, textLineHeight))
        return (textHeight + input.compoundPaddingTop + input.compoundPaddingBottom)
            .coerceAtLeast(input.height)
    }

    private fun viewportHeight(input: EditText): Int {
        return (input.height - input.compoundPaddingTop - input.compoundPaddingBottom).coerceAtLeast(1)
    }
}

class ProgramTemplateMenuScrollBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22FFFFFF
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAAFFFFFF.toInt()
    }
    private val activeThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    private var scrollView: ScrollView? = null
    private var dragging = false
    private var dragOffsetY = 0f

    fun attachTo(scrollView: ScrollView) {
        this.scrollView = scrollView
        scrollView.setOnScrollChangeListener { _, _, _, _, _ -> invalidate() }
        scrollView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> invalidate() }
        scrollView.post { invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = width / 2f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, trackPaint)
        val thumb = currentThumb()
        if (thumb.height >= height) return
        canvas.drawRoundRect(
            0f,
            thumb.top.toFloat(),
            width.toFloat(),
            (thumb.top + thumb.height).toFloat(),
            radius,
            radius,
            if (dragging) activeThumbPaint else thumbPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scroll = scrollView ?: return false
        if (!canScroll(scroll)) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                dragging = true
                val thumb = currentThumb()
                dragOffsetY = if (event.y.toInt() in thumb.top..(thumb.top + thumb.height)) {
                    event.y - thumb.top
                } else {
                    thumb.height / 2f
                }
                scrollToThumbTop((event.y - dragOffsetY).roundToInt())
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                scrollToThumbTop((event.y - dragOffsetY).roundToInt())
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return false
    }

    private fun scrollToThumbTop(thumbTop: Int) {
        val scroll = scrollView ?: return
        val scrollY = ProgramCodeScrollBarMath.scrollYForThumbTop(
            thumbTop = thumbTop,
            trackHeight = height,
            contentHeight = contentHeight(scroll),
            viewportHeight = viewportHeight(scroll)
        )
        scroll.scrollTo(0, scrollY)
    }

    private fun currentThumb(): ScrollThumb {
        val scroll = scrollView ?: return ScrollThumb(0, height)
        return ProgramCodeScrollBarMath.thumb(
            trackHeight = height,
            contentHeight = contentHeight(scroll),
            viewportHeight = viewportHeight(scroll),
            scrollY = scroll.scrollY
        )
    }

    private fun canScroll(scroll: ScrollView): Boolean {
        return contentHeight(scroll) > viewportHeight(scroll)
    }

    private fun contentHeight(scroll: ScrollView): Int {
        return (scroll.getChildAt(0)?.height ?: 0).coerceAtLeast(scroll.height)
    }

    private fun viewportHeight(scroll: ScrollView): Int = scroll.height.coerceAtLeast(1)
}
