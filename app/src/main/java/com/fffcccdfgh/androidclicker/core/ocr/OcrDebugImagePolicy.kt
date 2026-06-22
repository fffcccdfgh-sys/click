package com.fffcccdfgh.androidclicker.core.ocr

object OcrDebugImagePolicy {
    fun shouldSavePrefillFailureCrop(
        recognizedText: String,
        captureFailure: Boolean,
        debugImagesEnabled: Boolean = OcrDebugConfig.SAVE_DEBUG_IMAGES
    ): Boolean {
        return debugImagesEnabled && (captureFailure || recognizedText.isBlank())
    }

    fun cropFileName(
        timestampMs: Long,
        width: Int,
        height: Int
    ): String {
        return "ocr_prefill_${timestampMs}_${width}x${height}_original.png"
    }
}
