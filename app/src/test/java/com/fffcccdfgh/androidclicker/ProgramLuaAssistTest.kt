package com.fffcccdfgh.androidclicker

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgramLuaAssistTest {
    @Test
    fun insertsSnippetAtCursorWithSpacing() {
        val result = ProgramLuaAssist.insertSnippet(
            code = "while true do\nend",
            cursor = 14,
            snippet = "tap(100, 200)"
        )

        assertEquals("while true do\ntap(100, 200)\nend", result.code)
        assertEquals(27, result.cursor)
    }

    @Test
    fun buildsCoordinateAndConditionSnippets() {
        assertEquals("tap(100, 200)", ProgramLuaAssist.tapSnippet(100, 200))
        assertEquals("swipe(100, 200, 300, 400)", ProgramLuaAssist.swipeSnippet(100, 200, 300, 400))
        assertEquals("100, 200", ProgramLuaAssist.coordinateSnippet(100, 200))
        assertEquals(
            "check_text(\"文字\", 10, 20, 300, 400)",
            ProgramLuaAssist.textAreaSnippet(10, 20, 300, 400)
        )
        assertEquals(
            "check_color(\"#AABBCC\", 10, 123, 456)",
            ProgramLuaAssist.colorSnippet("#AABBCC", 123, 456)
        )
    }

    @Test
    fun providesQuickTemplatesForCommonLuaPatterns() {
        val templates = ProgramLuaAssist.quickTemplates()

        assertEquals(
            listOf(
                "tap",
                "wait",
                "loop_forever",
                "check_text_tap",
                "ocr_text_tap",
                "check_text_not_tap",
                "check_color_tap",
                "parallel",
                "loop_check_text_tap"
            ),
            templates.map { it.id }
        )
        assertEquals("tap(100, 200)", templates.first { it.id == "tap" }.snippet)
        assertEquals(
            "if check_color(\"#FF0000\", 10, 100, 200) then\n    tap(100, 200)\nend",
            templates.first { it.id == "check_color_tap" }.snippet
        )
    }
}
