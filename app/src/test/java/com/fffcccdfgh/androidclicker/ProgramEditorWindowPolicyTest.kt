package com.fffcccdfgh.androidclicker

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramEditorWindowPolicyTest {
    @Test
    fun editorWindowAllowsTextSelectionAndClipboardActions() {
        val flags = ProgramEditorWindowPolicy.FLAGS

        assertFalse(flags hasFlag WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        assertTrue(flags hasFlag WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        assertTrue(flags hasFlag WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
        assertTrue(flags hasFlag WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
    }

    @Test
    fun editorWindowUsesTextEditingSoftInputMode() {
        val mode = ProgramEditorWindowPolicy.SOFT_INPUT_MODE

        assertEquals(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE,
            mode and WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE
        )
        assertEquals(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            mode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        )
    }

    private infix fun Int.hasFlag(flag: Int): Boolean = (this and flag) == flag
}
