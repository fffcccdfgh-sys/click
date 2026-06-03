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

    @Test
    fun editorWindowUsesMoreHeightInLandscape() {
        val size = ProgramEditorWindowPolicy.windowSize(
            screenWidthPx = 2560,
            screenHeightPx = 1600,
            density = 2f
        )

        assertEquals(2202, size.width)
        assertEquals(1472, size.height)
    }

    @Test
    fun editorWindowPrefersCurrentDisplaySizeOverResourceSize() {
        val size = ProgramEditorWindowPolicy.windowSizeForCurrentDisplay(
            displayWidthPx = 2560,
            displayHeightPx = 1600,
            resourceWidthPx = 1600,
            resourceHeightPx = 2560,
            density = 2f
        )

        assertEquals(2202, size.width)
        assertEquals(1472, size.height)
    }

    @Test
    fun editorWindowStaysInsideScreenMargins() {
        val size = ProgramEditorWindowPolicy.windowSize(
            screenWidthPx = 480,
            screenHeightPx = 360,
            density = 2f
        )

        assertTrue(size.width <= 416)
        assertTrue(size.height <= 296)
    }

    @Test
    fun landscapeCodePanelDoesNotPushControlsBelowFold() {
        val height = ProgramEditorWindowPolicy.codePanelHeight(
            editorWidthPx = 2202,
            editorHeightPx = 1472,
            density = 2f
        )

        assertEquals(662, height)
    }

    @Test
    fun portraitCodePanelAlsoLeavesRoomForEditorControls() {
        val size = ProgramEditorWindowPolicy.windowSize(
            screenWidthPx = 1600,
            screenHeightPx = 2560,
            density = 2f
        )

        assertEquals(2150, size.height)
        assertEquals(
            1518,
            ProgramEditorWindowPolicy.codePanelHeight(
                editorWidthPx = size.width,
                editorHeightPx = size.height,
                density = 2f
            )
        )
    }

    @Test
    fun tightScreensPreferVisibleControlsOverMinimumCodeHeight() {
        assertEquals(
            208,
            ProgramEditorWindowPolicy.codePanelHeight(
                editorWidthPx = 2202,
                editorHeightPx = 1472,
                density = 4f
            )
        )
    }

    private infix fun Int.hasFlag(flag: Int): Boolean = (this and flag) == flag
}
