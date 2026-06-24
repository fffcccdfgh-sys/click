package com.fffcccdfgh.androidclicker

import com.fffcccdfgh.androidclicker.feature.pvz.PvzScriptLaunchPolicy
import com.fffcccdfgh.androidclicker.feature.pvz.floating.PvzFloatingControlService
import com.fffcccdfgh.androidclicker.feature.pvz.floating.PvzRunExecutionPolicy
import com.fffcccdfgh.androidclicker.feature.pvz.floating.PvzRunFloatingControlService
import com.fffcccdfgh.androidclicker.feature.pvz.calibration.PvzCalibrationLuaBindings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PvzScriptLaunchPolicyTest {
    @Test
    fun directRunUsesCompactPvzRunService() {
        assertEquals(
            PvzRunFloatingControlService::class.java,
            PvzScriptLaunchPolicy.directRunServiceClass
        )
    }

    @Test
    fun editUsesPvzEditorFloatingService() {
        assertEquals(
            PvzFloatingControlService::class.java,
            PvzScriptLaunchPolicy.editorServiceClass
        )
    }

    @Test
    fun compactPvzRunServiceRegistersCalibrationGlobals() {
        assertTrue(PvzCalibrationLuaBindings in PvzRunExecutionPolicy.extraLuaGlobalRegistrars)
    }

    @Test
    fun compactRunAndEditorServicesUseSeparateNotifications() {
        assertNotEquals(
            PvzFloatingControlService.NOTIFICATION_ID,
            PvzRunFloatingControlService.NOTIFICATION_ID
        )
    }
}
