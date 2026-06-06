package com.fffcccdfgh.androidclicker

import org.junit.Assert.assertTrue
import org.junit.Test

class OcrPrefillCapturePolicyTest {
    @Test
    fun prefillCapturesMoreThanOneFrameToAvoidStaleOrBlurredFirstFrame() {
        assertTrue(OcrPrefillCapturePolicy.CAPTURE_ATTEMPT_COUNT >= 2)
    }

    @Test
    fun prefillWaitsBrieflyBeforeTheFirstCapture() {
        assertTrue(OcrPrefillCapturePolicy.INITIAL_CAPTURE_DELAY_MS > 0)
    }

    @Test
    fun retriesWaitForANewFrame() {
        assertTrue(OcrPrefillCapturePolicy.BETWEEN_ATTEMPTS_DELAY_MS > 0)
    }
}
