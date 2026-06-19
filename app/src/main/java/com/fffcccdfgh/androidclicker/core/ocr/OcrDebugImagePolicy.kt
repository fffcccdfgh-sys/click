package com.fffcccdfgh.androidclicker.core.ocr

object OcrDebugImagePolicy {
    fun shouldSavePrefillFailureCrop(
        recognizedText: String,
        captureFailure: Boolean
    ): Boolean {
        return captureFailure || recognizedText.isBlank()
    }

    fun cropFileName(
        timestampMs: Long,
        width: Int,
        height: Int
    ): String {
        return "ocr_prefill_${timestampMs}_${width}x${height}_original.png"
    }
}
