package com.fffcccdfgh.androidclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaScriptCodecTest {
    @Test
    fun exportsActionSequenceAsReadableLua() {
        val script = ScriptStorage.SavedScript(
            name = "Test script",
            actions = listOf(
                ActionStep(
                    type = ActionStep.TYPE_TAP,
                    x = 2500,
                    y = 5000,
                    durationMs = 50,
                    conditionType = ActionStep.CONDITION_TEXT_CONTAINS,
                    conditionText = "ready",
                    conditionUseArea = true,
                    conditionLeft = 100,
                    conditionTop = 100,
                    conditionRight = 3000,
                    conditionBottom = 2000
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
        assertTrue(lua.contains("-- name: Test script"))
        assertTrue(lua.contains("while true do"))
        assertTrue(lua.contains("if check_text(\"ready\", 1.00, 1.00, 30.00, 20.00) then"))
        assertTrue(lua.contains("tap(25.00, 50.00, 50)"))
        assertTrue(lua.contains("wait(500)"))
        assertTrue(lua.contains("wait(1000)"))
    }

    @Test
    fun importsLuaAsSingleProgramAction() {
        val lua = """
            -- AndroidClicker Lua Script
            -- name: External script

            while true do
                tap(100, 200)
                wait(500)
            end
        """.trimIndent()

        val script = LuaScriptCodec.importScript(lua, fallbackName = "Default name")

        assertEquals("External script", script.name)
        assertEquals(1, script.actions.size)
        assertEquals(ActionStep.TYPE_PROGRAM, script.actions.single().type)
        assertTrue(script.actions.single().code!!.contains("tap(100, 200)"))
        assertEquals(1, script.loopCount)
        assertEquals(0L, script.loopGapMs)
    }

    @Test
    fun importsLuaWithFallbackNameWhenMetadataIsMissing() {
        val script = LuaScriptCodec.importScript("tap(1, 2)", fallbackName = "Imported script")

        assertEquals("Imported script", script.name)
        assertEquals(ActionStep.TYPE_PROGRAM, script.actions.single().type)
        assertEquals("tap(1, 2)", script.actions.single().code)
    }
}
