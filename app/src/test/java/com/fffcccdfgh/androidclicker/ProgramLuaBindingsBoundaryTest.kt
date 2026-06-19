package com.fffcccdfgh.androidclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramLuaBindingsBoundaryTest {
    @Test
    fun genericProgramExecutorDoesNotDependOnPvzCalibrationBindings() {
        val source = File(
            "src/main/java/com/fffcccdfgh/androidclicker/core/program/ProgramActionExecutor.kt"
        ).readText()

        assertFalse(source.contains("PvzCalibrationStorage"))
        assertFalse(source.contains("registerPvzCalibrationGlobals"))
    }

    @Test
    fun pvzExecutionFlowPassesPvzCalibrationBindingsExplicitly() {
        val source = File(
            "src/main/java/com/fffcccdfgh/androidclicker/feature/pvz/floating/PvzExecutionController.kt"
        ).readText()

        assertTrue(source.contains("PvzCalibrationLuaBindings"))
        assertTrue(source.contains("extraLuaGlobalRegistrars = listOf(PvzCalibrationLuaBindings)"))
    }
}
