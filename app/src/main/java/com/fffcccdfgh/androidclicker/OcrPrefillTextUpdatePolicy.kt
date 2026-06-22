package com.fffcccdfgh.androidclicker

object OcrPrefillTextUpdatePolicy {
    fun shouldApply(currentInput: String, recognizedText: String): Boolean {
        return currentInput.trim().isEmpty() && recognizedText.trim().isNotEmpty()
    }
}
