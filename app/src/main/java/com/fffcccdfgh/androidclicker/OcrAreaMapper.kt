package com.fffcccdfgh.androidclicker

import kotlin.math.ceil
import kotlin.math.floor

object OcrAreaMapper {
    data class Area(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    fun mapScreenAreaToCaptureArea(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        screenWidth: Int,
        screenHeight: Int,
        captureWidth: Int,
        captureHeight: Int
    ): Area? {
        if (screenWidth <= 0 || screenHeight <= 0 || captureWidth <= 0 || captureHeight <= 0) {
            return null
        }
        if (right <= left || bottom <= top) return null

        val mappedLeft = scaleFloor(left, screenWidth, captureWidth)
            .coerceIn(0, captureWidth)
        val mappedTop = scaleFloor(top, screenHeight, captureHeight)
            .coerceIn(0, captureHeight)
        val mappedRight = scaleCeil(right, screenWidth, captureWidth)
            .coerceIn(0, captureWidth)
        val mappedBottom = scaleCeil(bottom, screenHeight, captureHeight)
            .coerceIn(0, captureHeight)

        if (mappedRight <= mappedLeft || mappedBottom <= mappedTop) return null
        return Area(mappedLeft, mappedTop, mappedRight, mappedBottom)
    }

    private fun scaleFloor(value: Int, sourceSize: Int, targetSize: Int): Int {
        return floor(value.toDouble() * targetSize / sourceSize).toInt()
    }

    private fun scaleCeil(value: Int, sourceSize: Int, targetSize: Int): Int {
        return ceil(value.toDouble() * targetSize / sourceSize).toInt()
    }
}
