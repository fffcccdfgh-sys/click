package com.fffcccdfgh.androidclicker.core.ocr

object TextConditionDetector {
    fun containsText(
        targetText: String,
        ocrLookup: () -> Boolean
    ): Boolean {
        if (targetText.isEmpty()) return true
        return ocrLookup()
    }

    fun prefillText(ocrText: String): String {
        return ocrText.trim()
    }
}
