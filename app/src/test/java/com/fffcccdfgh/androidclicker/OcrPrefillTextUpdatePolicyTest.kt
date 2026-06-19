package com.fffcccdfgh.androidclicker

import com.fffcccdfgh.androidclicker.core.ocr.OcrPrefillTextUpdatePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrPrefillTextUpdatePolicyTest {
    @Test
    fun appliesRecognizedTextWhenInputIsStillBlank() {
        assertTrue(OcrPrefillTextUpdatePolicy.shouldApply("", "开始"))
    }

    @Test
    fun doesNotOverwriteManualInput() {
        assertFalse(OcrPrefillTextUpdatePolicy.shouldApply("手动输入", "开始"))
    }

    @Test
    fun ignoresBlankRecognizedText() {
        assertFalse(OcrPrefillTextUpdatePolicy.shouldApply("", "   "))
    }
}
