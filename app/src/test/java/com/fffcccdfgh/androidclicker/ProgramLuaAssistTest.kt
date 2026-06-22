package com.fffcccdfgh.androidclicker

import com.fffcccdfgh.androidclicker.core.program.ProgramCoordinateAdapter
import com.fffcccdfgh.androidclicker.core.program.ProgramLuaAssist
import org.junit.Assert.assertEquals
import org.junit.Test
import org.luaj.vm2.LuaValue

class ProgramLuaAssistTest {
    @Test
    fun insertsSnippetAtCursorWithSpacing() {
        val result = ProgramLuaAssist.insertSnippet(
            code = "while true do\nend",
            cursor = 14,
            snippet = "tap(50.00, 50.00)"
        )

        assertEquals("while true do\ntap(50.00, 50.00)\nend", result.code)
        assertEquals(31, result.cursor)
    }

    @Test
    fun buildsCoordinateAndConditionSnippetsAsPercentValues() {
        assertEquals("tap(25.00, 50.00)", ProgramLuaAssist.tapSnippet(250, 1000, 1000, 2000))
        assertEquals(
            "swipe(10.00, 10.00, 30.00, 20.00)",
            ProgramLuaAssist.swipeSnippet(100, 200, 300, 400, 1000, 2000)
        )
        assertEquals("12.30, 22.80", ProgramLuaAssist.coordinateSnippet(123, 456, 1000, 2000))
        assertEquals(
            "check_text(\"文字\", 1.00, 1.00, 30.00, 20.00)",
            ProgramLuaAssist.textAreaSnippet(10, 20, 300, 400, 1000, 2000)
        )
        assertEquals(
            "check_color(\"#AABBCC\", 10, 12.30, 22.80)",
            ProgramLuaAssist.colorSnippet("#AABBCC", 123, 456, 1000, 2000)
        )
    }

    @Test
    fun convertsPercentArgsToCurrentScreenPixels() {
        assertEquals(360, ProgramCoordinateAdapter.xArgToPointPx(LuaValue.valueOf(25.00), 1440))
        assertEquals(1600, ProgramCoordinateAdapter.yArgToPointPx(LuaValue.valueOf(50.00), 3200))
        assertEquals(1439, ProgramCoordinateAdapter.xArgToPointPx(LuaValue.valueOf(100.00), 1440))
        assertEquals(1440, ProgramCoordinateAdapter.xArgToEdgePx(LuaValue.valueOf(100.00), 1440))
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
        assertEquals("tap(50.00, 50.00)", templates.first { it.id == "tap" }.snippet)
        assertEquals(
            "while true do\nwait(500)\nend",
            templates.first { it.id == "loop_forever" }.snippet
        )
        assertEquals(
            "if check_color(\"#FF0000\", 10, 50.00, 50.00) then\ntap(50.00, 50.00)\nend",
            templates.first { it.id == "check_color_tap" }.snippet
        )
    }
}
