package com.fffcccdfgh.androidclicker.core.ocr

import kotlin.math.roundToInt

object OcrCaptureAreaPaddingPolicy {
    private const val PADDING_RATIO = 0.20
    private const val MIN_PADDING_PX = 16
    private const val MAX_PADDING_PX = 64

    fun expandForTextDetection(
        area: OcrAreaMapper.Area,
        captureWidth: Int,
        captureHeight: Int
    ): OcrAreaMapper.Area {
        if (captureWidth <= 0 || captureHeight <= 0) return area
        if (area.width <= 0 || area.height <= 0) return area

        val horizontalPadding = paddingFor(area.width)
        val verticalPadding = paddingFor(area.height)
        return OcrAreaMapper.Area(
            left = (area.left - horizontalPadding).coerceAtLeast(0),
            top = (area.top - verticalPadding).coerceAtLeast(0),
            right = (area.right + horizontalPadding).coerceAtMost(captureWidth),
            bottom = (area.bottom + verticalPadding).coerceAtMost(captureHeight)
        )
    }

    private fun paddingFor(size: Int): Int {
        return (size.toDouble() * PADDING_RATIO)
            .roundToInt()
            .coerceIn(MIN_PADDING_PX, MAX_PADDING_PX)
    }
}
