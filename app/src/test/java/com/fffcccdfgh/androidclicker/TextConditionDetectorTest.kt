package com.fffcccdfgh.androidclicker

import com.fffcccdfgh.androidclicker.core.ocr.TextConditionDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextConditionDetectorTest {
    @Test
    fun returnsTrueFromOcr() {
        val found = TextConditionDetector.containsText(
            targetText = "start",
            ocrLookup = { true }
        )

        assertTrue(found)
    }

    @Test
    fun returnsFalseWhenOcrMisses() {
        val found = TextConditionDetector.containsText(
            targetText = "missing",
            ocrLookup = { false }
        )

        assertFalse(found)
    }

    @Test
    fun emptyTargetMatchesExistingConditionBehavior() {
        val found = TextConditionDetector.containsText(
            targetText = "",
            ocrLookup = { false }
        )

        assertTrue(found)
    }

    @Test
    fun prefillTextUsesOnlyRecognizedOcrText() {
        val text = TextConditionDetector.prefillText(" restart\n")

        assertEquals("restart", text)
    }

    @Test
    fun prefillTextReturnsBlankWhenOcrIsBlank() {
        val text = TextConditionDetector.prefillText("   ")

        assertEquals("", text)
    }
}
