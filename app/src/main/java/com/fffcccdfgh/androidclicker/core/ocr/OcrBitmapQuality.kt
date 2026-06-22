package com.fffcccdfgh.androidclicker.core.ocr

import android.graphics.Bitmap
import kotlin.math.abs

object OcrBitmapQuality {
    private const val MIN_USABLE_EDGE_SCORE = 0.10

    fun edgeScore(bitmap: Bitmap): Double {
        if (bitmap.width <= 0 || bitmap.height <= 0) return 0.0

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return edgeScore(pixels, bitmap.width, bitmap.height)
    }

    fun isProbablyBlank(score: Double): Boolean = score <= MIN_USABLE_EDGE_SCORE

    fun edgeScore(
        pixels: IntArray,
        width: Int,
        height: Int
    ): Double {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return 0.0

        var total = 0L
        var comparisons = 0L
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val current = luminance(pixels[rowOffset + x])
                if (x + 1 < width) {
                    total += abs(current - luminance(pixels[rowOffset + x + 1]))
                    comparisons++
                }
                if (y + 1 < height) {
                    total += abs(current - luminance(pixels[rowOffset + width + x]))
                    comparisons++
                }
            }
        }

        return if (comparisons == 0L) {
            0.0
        } else {
            total.toDouble() / comparisons.toDouble()
        }
    }

    private fun luminance(pixel: Int): Int {
        val red = (pixel ushr 16) and 0xFF
        val green = (pixel ushr 8) and 0xFF
        val blue = pixel and 0xFF
        return ((red * 299) + (green * 587) + (blue * 114)) / 1000
    }
}
