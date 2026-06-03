package com.fffcccdfgh.androidclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramActionParserTest {
    @Test
    fun parsesPercentCoordinatesIntoStoredPercentValues() {
        val result = ProgramActionParser.parse(
            """
            tap(25.00, 50.00)
            swipe(10.00, 20.00, 30.00, 40.00, 500)
            cond("text_contains", "ready", 0.00, 0.00, 100.00, 100.00)
            cond("color_match", "#AABBCC", 10, 12.34, 56.78)
            """.trimIndent()
        )

        assertTrue(result.isSuccess)
        val commands = result.getOrThrow()

        assertEquals(ProgramCommand.TapCmd(x = 2500, y = 5000, durationMs = 1), commands[0])
        assertEquals(
            ProgramCommand.SwipeCmd(
                startX = 1000,
                startY = 2000,
                endX = 3000,
                endY = 4000,
                durationMs = 500
            ),
            commands[1]
        )
        assertEquals(
            ProgramCommand.ConditionCmd(
                conditionType = "text_contains",
                conditionText = "ready",
                conditionLeft = 0,
                conditionTop = 0,
                conditionRight = 10000,
                conditionBottom = 10000,
                conditionColorHex = null,
                conditionColorTolerance = null,
                conditionColorX = null,
                conditionColorY = null
            ),
            commands[2]
        )
        assertEquals(
            ProgramCommand.ConditionCmd(
                conditionType = "color_match",
                conditionText = null,
                conditionLeft = null,
                conditionTop = null,
                conditionRight = null,
                conditionBottom = null,
                conditionColorHex = "#AABBCC",
                conditionColorTolerance = 10,
                conditionColorX = 1234,
                conditionColorY = 5678
            ),
            commands[3]
        )
    }
}
