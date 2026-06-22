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

    fun mapScreenAreaToCaptureAreaAllowingRotation(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        screenWidth: Int,
        screenHeight: Int,
        captureWidth: Int,
        captureHeight: Int
    ): Area? {
        val normal = mapScreenAreaToCaptureArea(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            captureWidth = captureWidth,
            captureHeight = captureHeight
        )
        if (!isLikelyRotated(screenWidth, screenHeight, captureWidth, captureHeight)) {
            return normal
        }

        // Percent-based script areas should still scale from the reported screen
        // size into the captured frame. Only fall back to capture-oriented
        // coordinates when the normal mapping is impossible.
        if (normal != null) {
            return normal
        }

        val rotated = mapScreenAreaToCaptureArea(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            screenWidth = captureWidth,
            screenHeight = captureHeight,
            captureWidth = captureWidth,
            captureHeight = captureHeight
        )
        return rotated
    }

    fun isLikelyRotated(
        screenWidth: Int,
        screenHeight: Int,
        captureWidth: Int,
        captureHeight: Int
    ): Boolean {
        if (screenWidth <= 0 || screenHeight <= 0 || captureWidth <= 0 || captureHeight <= 0) {
            return false
        }
        val screenLandscape = screenWidth >= screenHeight
        val captureLandscape = captureWidth >= captureHeight
        if (screenLandscape == captureLandscape) return false

        val directAspectDelta = aspectDelta(screenWidth, screenHeight, captureWidth, captureHeight)
        val swappedAspectDelta = aspectDelta(screenWidth, screenHeight, captureHeight, captureWidth)
        return swappedAspectDelta < directAspectDelta
    }

    private fun scaleFloor(value: Int, sourceSize: Int, targetSize: Int): Int {
        return floor(value.toDouble() * targetSize / sourceSize).toInt()
    }

    private fun scaleCeil(value: Int, sourceSize: Int, targetSize: Int): Int {
        return ceil(value.toDouble() * targetSize / sourceSize).toInt()
    }

    private fun aspectDelta(widthA: Int, heightA: Int, widthB: Int, heightB: Int): Double {
        val aspectA = widthA.toDouble() / heightA.toDouble()
        val aspectB = widthB.toDouble() / heightB.toDouble()
        return kotlin.math.abs(aspectA - aspectB)
    }
}
