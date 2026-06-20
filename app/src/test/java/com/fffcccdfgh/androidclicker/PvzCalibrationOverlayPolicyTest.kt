package com.fffcccdfgh.androidclicker

import com.fffcccdfgh.androidclicker.feature.pvz.calibration.PvzCalibrationAreaCorner
import com.fffcccdfgh.androidclicker.feature.pvz.calibration.PvzCalibrationAreaResizePolicy
import com.fffcccdfgh.androidclicker.feature.pvz.calibration.PvzCalibrationPointColorPreviewPolicy
import com.fffcccdfgh.androidclicker.feature.pvz.calibration.PvzCalibrationStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PvzCalibrationOverlayPolicyTest {

    @Test
    fun colorPreviewAppliesOnlyToBattleColorTextPoints() {
        assertTrue(
            PvzCalibrationPointColorPreviewPolicy.shouldShowColorPreview(
                PvzCalibrationStorage.CARDS_POKER
            )
        )
        assertTrue(
            PvzCalibrationPointColorPreviewPolicy.shouldShowColorPreview(
                PvzCalibrationStorage.FINAL_WAVE_RED
            )
        )

        assertFalse(
            PvzCalibrationPointColorPreviewPolicy.shouldShowColorPreview(
                PvzCalibrationStorage.ENDLESS_SUPPLY_BLUE_CONFIRM
            )
        )
        assertFalse(
            PvzCalibrationPointColorPreviewPolicy.shouldShowColorPreview(
                PvzCalibrationStorage.OTHER_SPEED_UP
            )
        )
    }

    @Test
    fun areaResizeAllowsTinyRectangleWithoutInvertingEdges() {
        val resized = PvzCalibrationAreaResizePolicy.resize(
            left = 100f,
            top = 80f,
            right = 200f,
            bottom = 160f,
            corner = PvzCalibrationAreaCorner.TopLeft,
            dx = 99.5f,
            dy = 79.5f,
            width = 300f,
            height = 300f
        )

        assertEquals(199.5f, resized.left, 0.001f)
        assertEquals(159.5f, resized.top, 0.001f)
        assertEquals(200f, resized.right, 0.001f)
        assertEquals(160f, resized.bottom, 0.001f)
    }
}
