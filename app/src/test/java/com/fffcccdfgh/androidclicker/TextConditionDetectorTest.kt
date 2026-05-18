package com.fffcccdfgh.androidclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextConditionDetectorTest {
    @Test
    fun returnsTrueFromOcrWithoutCheckingAccessibility() {
        val calls = mutableListOf<String>()

        val found = TextConditionDetector.containsText(
            targetText = "开始",
            ocrLookup = {
                calls.add("ocr")
                true
            },
            accessibilityLookup = {
                calls.add("accessibility")
                false
            }
        )

        assertTrue(found)
        assertEquals(listOf("ocr"), calls)
    }

    @Test
    fun fallsBackToAccessibilityWhenOcrMisses() {
        val calls = mutableListOf<String>()

        val found = TextConditionDetector.containsText(
            targetText = "继续",
            ocrLookup = {
                calls.add("ocr")
                false
            },
            accessibilityLookup = {
                calls.add("accessibility")
                true
            }
        )

        assertTrue(found)
        assertEquals(listOf("ocr", "accessibility"), calls)
    }

    @Test
    fun returnsFalseWhenBothSourcesMiss() {
        val found = TextConditionDetector.containsText(
            targetText = "不存在",
            ocrLookup = { false },
            accessibilityLookup = { false }
        )

        assertFalse(found)
    }

    @Test
    fun emptyTargetMatchesExistingConditionBehavior() {
        val found = TextConditionDetector.containsText(
            targetText = "",
            ocrLookup = { false },
            accessibilityLookup = { false }
        )

        assertTrue(found)
    }

    @Test
    fun prefersRecognizedOcrTextForPrefill() {
        val text = TextConditionDetector.prefillText(
            ocrText = " 重启\n",
            accessibilityText = "按钮"
        )

        assertEquals("重启", text)
    }

    @Test
    fun fallsBackToAccessibilityTextForPrefillWhenOcrIsBlank() {
        val text = TextConditionDetector.prefillText(
            ocrText = "   ",
            accessibilityText = "重启"
        )

        assertEquals("重启", text)
    }
}
