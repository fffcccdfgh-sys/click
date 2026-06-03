package com.fffcccdfgh.androidclicker

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrBitmapFilterTest {
    @Test
    fun normalizesMissingFilterToDefaultBlackWhite() {
        assertEquals(OcrFilterMode.THRESHOLD, OcrFilterMode.normalize("unknown"))
        assertEquals(OcrFilterMode.THRESHOLD, OcrFilterMode.normalize(null))
    }

    @Test
    fun thresholdFilterTurnsDarkPixelsBlackAndBrightPixelsWhite() {
        val filtered = OcrBitmapFilter.filterArgbPixels(
            pixels = intArrayOf(argb(20, 20, 20), argb(230, 230, 230)),
            mode = OcrFilterMode.THRESHOLD,
            threshold = 127
        )

        assertArrayEquals(
            intArrayOf(argb(0, 0, 0), argb(255, 255, 255)),
            filtered
        )
    }

    @Test
    fun invertedThresholdFilterTurnsDarkPixelsWhiteAndBrightPixelsBlack() {
        val filtered = OcrBitmapFilter.filterArgbPixels(
            pixels = intArrayOf(argb(20, 20, 20), argb(230, 230, 230)),
            mode = OcrFilterMode.THRESHOLD_INVERT,
            threshold = 127
        )

        assertArrayEquals(
            intArrayOf(argb(255, 255, 255), argb(0, 0, 0)),
            filtered
        )
    }

    private fun argb(red: Int, green: Int, blue: Int): Int {
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
