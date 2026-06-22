package com.fffcccdfgh.androidclicker

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgramCoordinateAdapterTest {
    @Test
    fun convertsStoredPercentActionCoordinatesToRuntimePixels() {
        val stored = ActionStep(
            type = ActionStep.TYPE_SWIPE,
            startX = 2500,
            startY = 1000,
            endX = 7500,
            endY = 9000,
            durationMs = 300,
            conditionType = ActionStep.CONDITION_TEXT_CONTAINS,
            conditionText = "ready",
            conditionUseArea = true,
            conditionLeft = 100,
            conditionTop = 200,
            conditionRight = 10000,
            conditionBottom = 10000,
            conditionColorHex = "#AABBCC",
            conditionColorTolerance = 10,
            conditionColorX = 5000,
            conditionColorY = 5000
        )

        val runtime = ProgramCoordinateAdapter.storedActionToRuntimePx(
            stored,
            ProgramScreenSize(width = 1440, height = 3200)
        )

        assertEquals(360, runtime.startX)
        assertEquals(320, runtime.startY)
        assertEquals(1080, runtime.endX)
        assertEquals(2880, runtime.endY)
        assertEquals(14, runtime.conditionLeft)
        assertEquals(64, runtime.conditionTop)
        assertEquals(1440, runtime.conditionRight)
        assertEquals(3200, runtime.conditionBottom)
        assertEquals(720, runtime.conditionColorX)
        assertEquals(1600, runtime.conditionColorY)
    }

    @Test
    fun formatsStoredPercentForLuaArgumentsWithoutPercentSign() {
        assertEquals("0.00", ProgramCoordinateAdapter.formatStoredPercentArg(0))
        assertEquals("12.34", ProgramCoordinateAdapter.formatStoredPercentArg(1234))
        assertEquals("100.00", ProgramCoordinateAdapter.formatStoredPercentArg(10000))
    }
}
