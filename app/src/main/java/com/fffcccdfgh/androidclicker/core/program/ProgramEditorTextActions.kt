package com.fffcccdfgh.androidclicker.core.program

data class ProgramEditorTextResult(
    val code: String,
    val cursor: Int
)

object ProgramEditorTextActions {
    fun selectedText(code: String, selectionStart: Int, selectionEnd: Int): String? {
        val range = normalizedSelection(code, selectionStart, selectionEnd) ?: return null
        return code.substring(range.first, range.second)
    }

    fun cutSelection(code: String, selectionStart: Int, selectionEnd: Int): ProgramEditorTextResult? {
        val range = normalizedSelection(code, selectionStart, selectionEnd) ?: return null
        val result = code.removeRange(range.first, range.second)
        return ProgramEditorTextResult(result, range.first)
    }

    fun pasteText(
        code: String,
        selectionStart: Int,
        selectionEnd: Int,
        pasteText: String
    ): ProgramEditorTextResult {
        val safeStart = selectionStart.coerceIn(0, code.length)
        val safeEnd = selectionEnd.coerceIn(0, code.length)
        val left = safeStart.coerceAtMost(safeEnd)
        val right = safeStart.coerceAtLeast(safeEnd)
        val result = buildString {
            append(code.substring(0, left))
            append(pasteText)
            append(code.substring(right))
        }
        return ProgramEditorTextResult(result, left + pasteText.length)
    }

    private fun normalizedSelection(
        code: String,
        selectionStart: Int,
        selectionEnd: Int
    ): Pair<Int, Int>? {
        val safeStart = selectionStart.coerceIn(0, code.length)
        val safeEnd = selectionEnd.coerceIn(0, code.length)
        val left = safeStart.coerceAtMost(safeEnd)
        val right = safeStart.coerceAtLeast(safeEnd)
        if (left == right) return null
        return left to right
    }
}
