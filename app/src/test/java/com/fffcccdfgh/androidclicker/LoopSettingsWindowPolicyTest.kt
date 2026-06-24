package com.fffcccdfgh.androidclicker

import android.view.WindowManager
import com.fffcccdfgh.androidclicker.feature.clicker.floating.LoopSettingsWindowPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopSettingsWindowPolicyTest {
    @Test
    fun loopSettingsPanelAllowsKeyboardInput() {
        val flags = LoopSettingsWindowPolicy.FLAGS

        assertFalse(flags hasFlag WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        assertTrue(flags hasFlag WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
    }

    @Test
    fun loopSettingsPanelResizesForSoftKeyboard() {
        val mode = LoopSettingsWindowPolicy.SOFT_INPUT_MODE

        assertEquals(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            mode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        )
    }
}

private infix fun Int.hasFlag(flag: Int): Boolean = this and flag == flag
