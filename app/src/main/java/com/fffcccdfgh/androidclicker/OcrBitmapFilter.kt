package com.fffcccdfgh.androidclicker

import android.graphics.Bitmap
import kotlin.math.roundToInt

object OcrBitmapFilter {
    const val DEFAULT_THRESHOLD = 127

    fun apply(
        bitmap: Bitmap,
        mode: String?,
        threshold: Int = DEFAULT_THRESHOLD
    ): Bitmap {
        val normalizedMode = OcrFilterMode.normalize(mode)
        if (normalizedMode == OcrFilterMode.ORIGINAL) return bitmap

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val filtered = filterArgbPixels(pixels, normalizedMode, threshold)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(filtered, 0, width, 0, 0, width, height)
        }
    }

    fun filterArgbPixels(
        pixels: IntArray,
        mode: String?,
        threshold: Int = DEFAULT_THRESHOLD
    ): IntArray {
        val normalizedMode = OcrFilterMode.normalize(mode)
        if (normalizedMode == OcrFilterMode.ORIGINAL) return pixels.copyOf()

        val safeThreshold = threshold.coerceIn(0, 255)
        return IntArray(pixels.size) { index ->
            val pixel = pixels[index]
            val gray = luminance(pixel)
            val outputGray = when (normalizedMode) {
                OcrFilterMode.GRAYSCALE -> gray
                OcrFilterMode.THRESHOLD -> if (gray >= safeThreshold) 255 else 0
                OcrFilterMode.THRESHOLD_INVERT -> if (gray >= safeThreshold) 0 else 255
                else -> gray
            }
            argb(alpha(pixel), outputGray, outputGray, outputGray)
        }
    }

    private fun luminance(pixel: Int): Int {
        return (red(pixel) * 0.299 +
            green(pixel) * 0.587 +
            blue(pixel) * 0.114)
            .roundToInt()
            .coerceIn(0, 255)
    }

    private fun alpha(pixel: Int): Int = (pixel ushr 24) and 0xFF

    private fun red(pixel: Int): Int = (pixel ushr 16) and 0xFF

    private fun green(pixel: Int): Int = (pixel ushr 8) and 0xFF

    private fun blue(pixel: Int): Int = pixel and 0xFF

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int {
        return ((alpha and 0xFF) shl 24) or
            ((red and 0xFF) shl 16) or
            ((green and 0xFF) shl 8) or
            (blue and 0xFF)
    }
}
