package com.fffcccdfgh.androidclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaScriptCodecTest {
    @Test
    fun exportsActionSequenceAsReadableLua() {
        val script = ScriptStorage.SavedScript(
            name = "测试脚本",
            actions = listOf(
                ActionStep(
                    type = ActionStep.TYPE_TAP,
                    x = 100,
                    y = 200,
                    durationMs = 50,
                    conditionType = ActionStep.CONDITION_TEXT_CONTAINS,
                    conditionText = "重启",
                    conditionUseArea = true,
                    conditionLeft = 10,
                    conditionTop = 20,
                    conditionRight = 300,
                    conditionBottom = 400
                ),
                ActionStep(
                    type = ActionStep.TYPE_WAIT,
                    durationMs = 500
                )
            ),
            loopCount = 0,
            loopGapMs = 1000
        )

        val lua = LuaScriptCodec.exportScript(script)

        assertTrue(lua.contains("-- AndroidClicker Lua Script"))
        assertTrue(lua.contains("-- name: 测试脚本"))
        assertTrue(lua.contains("while true do"))
        assertTrue(lua.contains("if check_text(\"重启\", 10, 20, 300, 400) then"))
        assertTrue(lua.contains("tap(100, 200, 50)"))
        assertTrue(lua.contains("wait(500)"))
        assertTrue(lua.contains("wait(1000)"))
    }

    @Test
    fun importsLuaAsSingleProgramAction() {
        val lua = """
            -- AndroidClicker Lua Script
            -- name: 外部脚本

            while true do
                tap(100, 200)
                wait(500)
            end
        """.trimIndent()

        val script = LuaScriptCodec.importScript(lua, fallbackName = "默认名")

        assertEquals("外部脚本", script.name)
        assertEquals(1, script.actions.size)
        assertEquals(ActionStep.TYPE_PROGRAM, script.actions.single().type)
        assertTrue(script.actions.single().code!!.contains("tap(100, 200)"))
        assertEquals(1, script.loopCount)
        assertEquals(0L, script.loopGapMs)
    }

    @Test
    fun importsLuaWithFallbackNameWhenMetadataIsMissing() {
        val script = LuaScriptCodec.importScript("tap(1, 2)", fallbackName = "导入脚本")

        assertEquals("导入脚本", script.name)
        assertEquals(ActionStep.TYPE_PROGRAM, script.actions.single().type)
        assertEquals("tap(1, 2)", script.actions.single().code)
    }
}
