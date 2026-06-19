package com.fffcccdfgh.androidclicker.core.ocr

object OcrPrefillTextUpdatePolicy {
    fun shouldApply(currentInput: String, recognizedText: String): Boolean {
        return currentInput.trim().isEmpty() && recognizedText.trim().isNotEmpty()
    }
}
