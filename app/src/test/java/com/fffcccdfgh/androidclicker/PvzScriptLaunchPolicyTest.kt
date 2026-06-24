package com.fffcccdfgh.androidclicker

import com.fffcccdfgh.androidclicker.feature.pvz.PvzScriptLaunchPolicy
import com.fffcccdfgh.androidclicker.feature.pvz.floating.PvzFloatingControlService
import org.junit.Assert.assertEquals
import org.junit.Test

class PvzScriptLaunchPolicyTest {
    @Test
    fun directRunUsesPvzFloatingServiceSoCalibrationGlobalsAreRegistered() {
        assertEquals(
            PvzFloatingControlService::class.java,
            PvzScriptLaunchPolicy.directRunServiceClass
        )
    }
}
