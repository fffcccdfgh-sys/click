package com.fffcccdfgh.androidclicker

object TextConditionDetector {
    fun containsText(
        targetText: String,
        ocrLookup: () -> Boolean,
        accessibilityLookup: () -> Boolean
    ): Boolean {
        if (targetText.isEmpty()) return true
        if (ocrLookup()) return true
        return accessibilityLookup()
    }

    fun prefillText(ocrText: String, accessibilityText: String): String {
        val normalizedOcr = ocrText.trim()
        if (normalizedOcr.isNotEmpty()) return normalizedOcr
        return accessibilityText.trim()
    }
}
