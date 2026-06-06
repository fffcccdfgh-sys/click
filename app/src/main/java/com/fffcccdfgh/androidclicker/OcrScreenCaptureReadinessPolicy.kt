package com.fffcccdfgh.androidclicker

object OcrScreenCaptureReadinessPolicy {
    fun shouldRequestPermissionBeforeTextAreaSelection(
        conditionType: String?,
        captureReady: Boolean
    ): Boolean {
        return conditionType == ActionStep.CONDITION_TEXT_CONTAINS && !captureReady
    }
}
