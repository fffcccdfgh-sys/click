package com.fffcccdfgh.androidclicker.core.ocr

import java.util.Locale
import kotlin.math.min

object OcrTextMatcher {
    fun matches(recognizedText: String, targetText: String): Boolean {
        val target = normalize(targetText)
        if (target.isEmpty()) return true
        val recognized = normalize(recognizedText)
        if (recognized.isEmpty()) return false
        if (recognized.contains(target)) return true

        val maxDistance = maxAllowedDistance(target.length)
        if (maxDistance == 0) return false

        return hasApproximateSubstring(
            recognized = recognized,
            target = target,
            maxDistance = maxDistance
        )
    }

    fun normalize(text: String): String {
        return buildString(text.length) {
            for (rawChar in text.lowercase(Locale.ROOT)) {
                val char = normalizeWidth(rawChar)
                if (char.isLetterOrDigit()) {
                    append(char)
                }
            }
        }
    }

    private fun maxAllowedDistance(targetLength: Int): Int {
        return when {
            targetLength <= 1 -> 0
            targetLength <= 4 -> 1
            targetLength <= 8 -> 2
            targetLength <= 12 -> 3
            else -> 3
        }
    }

    private fun hasApproximateSubstring(
        recognized: String,
        target: String,
        maxDistance: Int
    ): Boolean {
        if (recognized.length <= target.length + maxDistance) {
            return editDistanceAtMost(recognized, target, maxDistance)
        }

        val minWindow = (target.length - maxDistance).coerceAtLeast(1)
        val maxWindow = target.length + maxDistance
        for (start in recognized.indices) {
            val maxEnd = min(recognized.length, start + maxWindow)
            for (end in start + minWindow..maxEnd) {
                if (editDistanceAtMost(recognized.substring(start, end), target, maxDistance)) {
                    return true
                }
            }
        }
        return false
    }

    private fun editDistanceAtMost(left: String, right: String, maxDistance: Int): Boolean {
        if (kotlin.math.abs(left.length - right.length) > maxDistance) return false

        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)
        for (i in 1..left.length) {
            current[0] = i
            var rowBest = current[0]
            for (j in 1..right.length) {
                val substitutionCost = if (left[i - 1] == right[j - 1]) 0 else 1
                current[j] = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + substitutionCost
                )
                rowBest = min(rowBest, current[j])
            }
            if (rowBest > maxDistance) return false
            val tmp = previous
            previous = current
            current = tmp
        }
        return previous[right.length] <= maxDistance
    }

    private fun normalizeWidth(char: Char): Char {
        return when (char) {
            '\u3000' -> ' '
            in '\uFF01'..'\uFF5E' -> (char.code - 0xFEE0).toChar()
            else -> char
        }
    }
}
