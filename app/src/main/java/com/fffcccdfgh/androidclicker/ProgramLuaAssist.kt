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

    fun tapSnippet(x: Int, y: Int): String = "tap($x, $y)"

    fun coordinateSnippet(x: Int, y: Int): String = "$x, $y"

    fun textAreaSnippet(left: Int, top: Int, right: Int, bottom: Int): String {
        return "check_text(\"文字\", $left, $top, $right, $bottom)"
    }

    fun colorSnippet(hex: String, x: Int, y: Int, tolerance: Int = 10): String {
        return "check_color(\"$hex\", $tolerance, $x, $y)"
    }

    fun quickTemplates(): List<ProgramLuaTemplate> {
        val textPlaceholder = "\u6587\u5B57"
        return listOf(
            ProgramLuaTemplate(
                id = "tap",
                title = "\u70B9\u51FB",
                snippet = "tap(100, 200)"
            ),
            ProgramLuaTemplate(
                id = "wait",
                title = "\u7B49\u5F85",
                snippet = "wait(500)"
            ),
            ProgramLuaTemplate(
                id = "loop_forever",
                title = "\u65E0\u9650\u5FAA\u73AF",
                snippet = "while true do\n    wait(500)\nend"
            ),
            ProgramLuaTemplate(
                id = "check_text_tap",
                title = "\u6587\u5B57\u51FA\u73B0\u5C31\u70B9\u51FB",
                snippet = "if check_text(\"$textPlaceholder\", 0, 0, 100, 100) then\n    tap(100, 200)\nend"
            ),
            ProgramLuaTemplate(
                id = "ocr_text_tap",
                title = "OCR\u6587\u5B57\u51FA\u73B0\u5C31\u70B9\u51FB",
                snippet = "if ocr_text(\"$textPlaceholder\", 0, 0, 100, 100) then\n    tap(100, 200)\nend"
            ),
            ProgramLuaTemplate(
                id = "check_text_not_tap",
                title = "\u6587\u5B57\u6CA1\u51FA\u73B0\u5C31\u70B9\u51FB",
                snippet = "if check_text_not(\"$textPlaceholder\", 0, 0, 100, 100) then\n    tap(100, 200)\nend"
            ),
            ProgramLuaTemplate(
                id = "check_color_tap",
                title = "\u989C\u8272\u5339\u914D\u5C31\u70B9\u51FB",
                snippet = "if check_color(\"#FF0000\", 10, 100, 200) then\n    tap(100, 200)\nend"
            ),
            ProgramLuaTemplate(
                id = "parallel",
                title = "\u5E76\u884C\u6267\u884C",
                snippet = "parallel(\n    function()\n        tap(100, 200)\n    end,\n    function()\n        tap(300, 400)\n    end\n)"
            ),
            ProgramLuaTemplate(
                id = "loop_check_text_tap",
                title = "\u5FAA\u73AF\u68C0\u6D4B\u6587\u5B57\u5E76\u70B9\u51FB",
                snippet = "while true do\n    if check_text(\"$textPlaceholder\", 0, 0, 100, 100) then\n        tap(100, 200)\n    end\n    wait(500)\nend"
            )
        )
    }
}
