package com.fffcccdfgh.androidclicker

import com.fffcccdfgh.androidclicker.core.ocr.OcrAreaMapper
import com.fffcccdfgh.androidclicker.core.ocr.OcrCaptureAreaPaddingPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrCaptureAreaPaddingPolicyTest {
    @Test
    fun expandsTightTextCropBeforePaddleDetection() {
        val expanded = OcrCaptureAreaPaddingPolicy.expandForTextDetection(
            area = OcrAreaMapper.Area(
                left = 930,
                top = 1190,
                right = 1301,
                bottom = 1306
            ),
            captureWidth = 3440,
            captureHeight = 1440
        )

        assertEquals(
            OcrAreaMapper.Area(
                left = 866,
                top = 1167,
                right = 1365,
                bottom = 1329
            ),
            expanded
        )
    }

    @Test
    fun clampsExpandedCropToCaptureBounds() {
        val expanded = OcrCaptureAreaPaddingPolicy.expandForTextDetection(
            area = OcrAreaMapper.Area(
                left = 2,
                top = 3,
                right = 30,
                bottom = 20
            ),
            captureWidth = 100,
            captureHeight = 80
        )

        assertEquals(
            OcrAreaMapper.Area(
                left = 0,
                top = 0,
                right = 46,
                bottom = 36
            ),
            expanded
        )
    }
}
