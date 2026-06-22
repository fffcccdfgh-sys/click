package com.fffcccdfgh.androidclicker

import com.fffcccdfgh.androidclicker.core.ocr.OcrTextMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTextMatcherTest {
    @Test
    fun matchesAfterRemovingWhitespaceAndPunctuation() {
        assertTrue(
            OcrTextMatcher.matches(
                recognizedText = "开 始-挑 战",
                targetText = "开始挑战"
            )
        )
    }

    @Test
    fun matchesFullWidthAsciiAndCaseInsensitively() {
        assertTrue(
            OcrTextMatcher.matches(
                recognizedText = "ＡＢＣ １２３",
                targetText = "abc123"
            )
        )
    }

    @Test
    fun allowsOneWrongCharacterForShortPhrases() {
        assertTrue(
            OcrTextMatcher.matches(
                recognizedText = "开始挑站",
                targetText = "开始挑战"
            )
        )
    }

    @Test
    fun allowsMissingCharacterForLongerPhrases() {
        assertTrue(
            OcrTextMatcher.matches(
                recognizedText = "点击开始战斗",
                targetText = "点击开始挑战"
            )
        )
    }

    @Test
    fun doesNotFuzzSingleCharacterTargets() {
        assertFalse(
            OcrTextMatcher.matches(
                recognizedText = "大",
                targetText = "天"
            )
        )
    }

    @Test
    fun rejectsUnrelatedText() {
        assertFalse(
            OcrTextMatcher.matches(
                recognizedText = "返回主页",
                targetText = "开始挑战"
            )
        )
    }
}
