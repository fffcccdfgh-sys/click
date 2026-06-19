package com.fffcccdfgh.androidclicker

import android.view.WindowManager
import com.fffcccdfgh.androidclicker.core.overlay.FloatingWindowSizePolicy
import com.fffcccdfgh.androidclicker.core.program.ProgramEditorWindowPolicy
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

    @Test
    fun editorWindowUsesMoreHeightInLandscape() {
        val size = FloatingWindowSizePolicy.programEditorSize(
            screenWidthPx = 2560,
            screenHeightPx = 1600,
            density = 2f
        )

        assertEquals(1536, size.widthPx)
        assertEquals(1280, size.heightPx)
    }

    @Test
    fun editorWindowPrefersCurrentDisplaySizeOverResourceSize() {
        val size = FloatingWindowSizePolicy.programEditorSizeForDisplay(
            displayWidthPx = 2560,
            displayHeightPx = 1600,
            resourceWidthPx = 1600,
            resourceHeightPx = 2560,
            density = 2f
        )

        assertEquals(1536, size.widthPx)
        assertEquals(1280, size.heightPx)
    }

    @Test
    fun editorWindowStaysInsideScreenMargins() {
        val size = FloatingWindowSizePolicy.programEditorSize(
            screenWidthPx = 480,
            screenHeightPx = 360,
            density = 2f
        )

        assertTrue(size.widthPx <= 416)
        assertTrue(size.heightPx <= 296)
    }

    @Test
    fun landscapeTemplateWindowUsesHalfEditorWidthAndFullEditorHeight() {
        val size = FloatingWindowSizePolicy.programTemplateSize(
            screenWidthPx = 2560,
            screenHeightPx = 1600,
            density = 2f
        )

        assertEquals(768, size.widthPx)
        assertEquals(1280, size.heightPx)
    }

    @Test
    fun portraitTemplateWindowUsesHalfEditorWidthAndTwoThirdsEditorHeight() {
        val size = FloatingWindowSizePolicy.programTemplateSize(
            screenWidthPx = 1600,
            screenHeightPx = 2560,
            density = 2f
        )

        assertEquals(640, size.widthPx)
        assertEquals(1024, size.heightPx)
    }

    private infix fun Int.hasFlag(flag: Int): Boolean = (this and flag) == flag
}
