package com.fffcccdfgh.androidclicker

data class ProgramLuaInsertResult(
    val code: String,
    val cursor: Int
)

data class ProgramLuaTemplate(
    val id: String,
    val title: String,
    val snippet: String
)

object ProgramLuaAssist {
    fun insertSnippet(code: String, cursor: Int, snippet: String): ProgramLuaInsertResult {
        val safeCursor = cursor.coerceIn(0, code.length)
        val before = code.substring(0, safeCursor)
        val after = code.substring(safeCursor)
        val prefix = if (before.isEmpty() || before.endsWith('\n')) "" else "\n"
        val suffix = if (after.isEmpty() || after.startsWith('\n')) "" else "\n"
        val inserted = prefix + snippet + suffix
        return ProgramLuaInsertResult(
            code = before + inserted + after,
            cursor = safeCursor + prefix.length + snippet.length
        )
    }

    fun tapSnippet(x: Int, y: Int, screenWidth: Int, screenHeight: Int): String {
        val (px, py) = ProgramCoordinateAdapter.pointPercentSnippet(x, y, screenWidth, screenHeight)
        return "tap($px, $py)"
    }

    fun swipeSnippet(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        screenWidth: Int,
        screenHeight: Int
    ): String {
        val (sx, sy) = ProgramCoordinateAdapter.pointPercentSnippet(startX, startY, screenWidth, screenHeight)
        val (ex, ey) = ProgramCoordinateAdapter.pointPercentSnippet(endX, endY, screenWidth, screenHeight)
        return "swipe($sx, $sy, $ex, $ey)"
    }

    fun coordinateSnippet(x: Int, y: Int, screenWidth: Int, screenHeight: Int): String {
        val (px, py) = ProgramCoordinateAdapter.pointPercentSnippet(x, y, screenWidth, screenHeight)
        return "$px, $py"
    }

    fun textAreaSnippet(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        screenWidth: Int,
        screenHeight: Int
    ): String {
        val (leftPct, topPct) = ProgramCoordinateAdapter.pointPercentSnippet(left, top, screenWidth, screenHeight)
        val (rightPct, bottomPct) = ProgramCoordinateAdapter.pointPercentSnippet(right, bottom, screenWidth, screenHeight)
        return "check_text(\"\u6587\u5B57\", $leftPct, $topPct, $rightPct, $bottomPct)"
    }

    fun colorSnippet(
        hex: String,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        tolerance: Int = 10
    ): String {
        val (px, py) = ProgramCoordinateAdapter.pointPercentSnippet(x, y, screenWidth, screenHeight)
        return "check_color(\"$hex\", $tolerance, $px, $py)"
    }

    fun quickTemplates(): List<ProgramLuaTemplate> {
        val textPlaceholder = "\u6587\u5B57"
        return listOf(
            ProgramLuaTemplate(
                id = "tap",
                title = "\u70B9\u51FB",
                snippet = "tap(50.00, 50.00)"
            ),
            ProgramLuaTemplate(
                id = "wait",
                title = "\u7B49\u5F85",
                snippet = "wait(500)"
            ),
            ProgramLuaTemplate(
                id = "loop_forever",
                title = "\u65E0\u9650\u5FAA\u73AF",
                snippet = "while true do\nwait(500)\nend"
            ),
            ProgramLuaTemplate(
                id = "check_text_tap",
                title = "\u6587\u5B57\u51FA\u73B0\u5C31\u70B9\u51FB",
                snippet = "if check_text(\"$textPlaceholder\", 0.00, 0.00, 100.00, 100.00) then\ntap(50.00, 50.00)\nend"
            ),
            ProgramLuaTemplate(
                id = "ocr_text_tap",
                title = "OCR\u6587\u5B57\u51FA\u73B0\u5C31\u70B9\u51FB",
                snippet = "if ocr_text(\"$textPlaceholder\", 0.00, 0.00, 100.00, 100.00) then\ntap(50.00, 50.00)\nend"
            ),
            ProgramLuaTemplate(
                id = "check_text_not_tap",
                title = "\u6587\u5B57\u6CA1\u51FA\u73B0\u5C31\u70B9\u51FB",
                snippet = "if check_text_not(\"$textPlaceholder\", 0.00, 0.00, 100.00, 100.00) then\ntap(50.00, 50.00)\nend"
            ),
            ProgramLuaTemplate(
                id = "check_color_tap",
                title = "\u989C\u8272\u5339\u914D\u5C31\u70B9\u51FB",
                snippet = "if check_color(\"#FF0000\", 10, 50.00, 50.00) then\ntap(50.00, 50.00)\nend"
            ),
            ProgramLuaTemplate(
                id = "parallel",
                title = "\u5E76\u884C\u6267\u884C",
                snippet = "parallel(\nfunction()\ntap(25.00, 50.00)\nend,\nfunction()\ntap(75.00, 50.00)\nend\n)"
            ),
            ProgramLuaTemplate(
                id = "loop_check_text_tap",
                title = "\u5FAA\u73AF\u68C0\u6D4B\u6587\u5B57\u5E76\u70B9\u51FB",
                snippet = "while true do\nif check_text(\"$textPlaceholder\", 0.00, 0.00, 100.00, 100.00) then\ntap(50.00, 50.00)\nend\nwait(500)\nend"
            )
        )
    }
}
