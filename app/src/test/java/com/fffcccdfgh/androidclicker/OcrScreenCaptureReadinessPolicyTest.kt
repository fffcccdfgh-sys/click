package com.fffcccdfgh.androidclicker

import com.fffcccdfgh.androidclicker.core.execution.ActionStep
import com.fffcccdfgh.androidclicker.core.ocr.OcrScreenCaptureReadinessPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrScreenCaptureReadinessPolicyTest {
    @Test
    fun textAreaSelectionNeedsCapturePermissionWhenCaptureIsNotReady() {
        assertTrue(
            OcrScreenCaptureReadinessPolicy.shouldRequestPermissionBeforeTextAreaSelection(
                conditionType = ActionStep.CONDITION_TEXT_CONTAINS,
                captureReady = false
            )
        )
    }

    @Test
    fun textAreaSelectionDoesNotRequestPermissionWhenCaptureIsReady() {
        assertFalse(
            OcrScreenCaptureReadinessPolicy.shouldRequestPermissionBeforeTextAreaSelection(
                conditionType = ActionStep.CONDITION_TEXT_CONTAINS,
                captureReady = true
            )
        )
    }

    @Test
    fun nonTextAreaSelectionDoesNotRequestCapturePermission() {
        assertFalse(
            OcrScreenCaptureReadinessPolicy.shouldRequestPermissionBeforeTextAreaSelection(
                conditionType = ActionStep.CONDITION_COLOR_MATCH,
                captureReady = false
            )
        )
        assertFalse(
            OcrScreenCaptureReadinessPolicy.shouldRequestPermissionBeforeTextAreaSelection(
                conditionType = null,
                captureReady = false
            )
        )
    }
}
