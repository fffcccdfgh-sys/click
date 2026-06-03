package com.fffcccdfgh.androidclicker

object OcrFilterMode {
    const val ORIGINAL = "original"
    const val GRAYSCALE = "grayscale"
    const val THRESHOLD = "threshold"
    const val THRESHOLD_INVERT = "threshold_invert"
    const val DEFAULT = THRESHOLD

    val ALL = listOf(ORIGINAL, GRAYSCALE, THRESHOLD, THRESHOLD_INVERT)

    fun normalize(value: String?): String {
        return if (value in ALL) value!! else DEFAULT
    }
}
