package com.fffcccdfgh.androidclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTimingPolicyTest {
    @Test
    fun prefillCaptureTimeoutStaysShortEnoughForInteractiveUse() {
        assertTrue(OcrTimingPolicy.PREFILL_CAPTURE_TIMEOUT_MS <= 800L)
    }

    @Test
    fun regularCaptureTimeoutRemainsLongerThanPrefillTimeout() {
        assertEquals(3000L, OcrTimingPolicy.DEFAULT_CAPTURE_TIMEOUT_MS)
        assertTrue(OcrTimingPolicy.DEFAULT_CAPTURE_TIMEOUT_MS > OcrTimingPolicy.PREFILL_CAPTURE_TIMEOUT_MS)
    }
}
