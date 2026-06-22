package com.fffcccdfgh.androidclicker

import com.fffcccdfgh.androidclicker.core.ocr.OcrDebugImagePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrDebugImagePolicyTest {
    @Test
    fun doesNotSavePrefillCropByDefault() {
        assertFalse(
            OcrDebugImagePolicy.shouldSavePrefillFailureCrop(
                recognizedText = "",
                captureFailure = false
            )
        )

        assertFalse(
            OcrDebugImagePolicy.shouldSavePrefillFailureCrop(
                recognizedText = "",
                captureFailure = true
            )
        )
    }

    @Test
    fun savesPrefillCropOnlyForFailuresWhenDebugImagesEnabled() {
        assertTrue(
            OcrDebugImagePolicy.shouldSavePrefillFailureCrop(
                recognizedText = "",
                captureFailure = false,
                debugImagesEnabled = true
            )
        )

        assertFalse(
            OcrDebugImagePolicy.shouldSavePrefillFailureCrop(
                recognizedText = "ready",
                captureFailure = false,
                debugImagesEnabled = true
            )
        )

        assertTrue(
            OcrDebugImagePolicy.shouldSavePrefillFailureCrop(
                recognizedText = "",
                captureFailure = true,
                debugImagesEnabled = true
            )
        )
    }

    @Test
    fun buildsReadableCropFileName() {
        assertEquals(
            "ocr_prefill_12345_700x525_original.png",
            OcrDebugImagePolicy.cropFileName(
                timestampMs = 12345L,
                width = 700,
                height = 525
            )
        )
    }
}
