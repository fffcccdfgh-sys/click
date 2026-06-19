package com.fffcccdfgh.androidclicker.core.ocr

import com.fffcccdfgh.androidclicker.core.execution.ActionStep

object OcrScreenCaptureReadinessPolicy {
    fun shouldRequestPermissionBeforeTextAreaSelection(
        conditionType: String?,
        captureReady: Boolean
    ): Boolean {
        return conditionType == ActionStep.CONDITION_TEXT_CONTAINS && !captureReady
    }
}
