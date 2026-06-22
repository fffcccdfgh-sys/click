package com.fffcccdfgh.androidclicker

import com.fffcccdfgh.androidclicker.core.ocr.OcrBitmapQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrBitmapQualityTest {
    @Test
    fun flatImageHasNoEdgeScore() {
        val pixels = IntArray(4) { argb(120, 120, 120) }

        assertEquals(0.0, OcrBitmapQuality.edgeScore(pixels, width = 2, height = 2), 0.0)
    }

    @Test
    fun flatImageScoreIsProbablyBlank() {
        assertTrue(OcrBitmapQuality.isProbablyBlank(0.0))
    }

    @Test
    fun sharpEdgesScoreHigherThanSmoothArea() {
        val smooth = intArrayOf(
            argb(100, 100, 100), argb(105, 105, 105),
            argb(110, 110, 110), argb(115, 115, 115)
        )
        val sharp = intArrayOf(
            argb(0, 0, 0), argb(255, 255, 255),
            argb(255, 255, 255), argb(0, 0, 0)
        )

        assertTrue(
            OcrBitmapQuality.edgeScore(sharp, width = 2, height = 2) >
                OcrBitmapQuality.edgeScore(smooth, width = 2, height = 2)
        )
    }

    private fun argb(red: Int, green: Int, blue: Int): Int {
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
