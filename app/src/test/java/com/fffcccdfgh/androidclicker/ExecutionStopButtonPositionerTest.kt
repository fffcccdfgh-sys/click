package com.fffcccdfgh.androidclicker

import org.junit.Assert.assertEquals
import org.junit.Test

class ExecutionStopButtonPositionerTest {
    @Test
    fun touchThroughAlphaStaysBelowAndroidObscuringLimit() {
        assertEquals(0.75f, ExecutionOverlayWindowPolicy.TOUCH_THROUGH_ALPHA, 0.001f)
    }

    @Test
    fun defaultPositionKeepsButtonNearBottomRight() {
        val position = ExecutionStopButtonPositioner.defaultPosition(
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            buttonSizePx = 120,
            marginPx = 48
        )

        assertEquals(912, position.x)
        assertEquals(2232, position.y)
    }

    @Test
    fun clampPositionKeepsButtonOnScreen() {
        val negative = ExecutionStopButtonPositioner.clampPosition(
            x = -50,
            y = -30,
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            buttonSizePx = 120
        )
        val tooFar = ExecutionStopButtonPositioner.clampPosition(
            x = 1200,
            y = 2600,
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            buttonSizePx = 120
        )

        assertEquals(0, negative.x)
        assertEquals(0, negative.y)
        assertEquals(960, tooFar.x)
        assertEquals(2280, tooFar.y)
    }

    @Test
    fun stopButtonClickStopsEvenIfTouchIsCancelled() {
        val touch = ExecutionStopButtonTouchState(
            touchSlopPx = 8,
            mode = StopButtonTouchMode.EXECUTION
        )

        assertEquals(StopButtonTouchDecision.PAUSE_DISPATCH, touch.onDown())
        assertEquals(StopButtonTouchDecision.STOP, touch.onCancel())
    }

    @Test
    fun stopButtonDragResumesInsteadOfStopping() {
        val touch = ExecutionStopButtonTouchState(
            touchSlopPx = 8,
            mode = StopButtonTouchMode.EXECUTION
        )

        assertEquals(StopButtonTouchDecision.PAUSE_DISPATCH, touch.onDown())
        assertEquals(StopButtonTouchDecision.DRAG, touch.onMove(dx = 12f, dy = 0f))
        assertEquals(StopButtonTouchDecision.RESUME_DISPATCH, touch.onUp())
    }

    @Test
    fun positioningModeDragSavesPositionWithoutStopping() {
        val touch = ExecutionStopButtonTouchState(
            touchSlopPx = 8,
            mode = StopButtonTouchMode.POSITIONING
        )

        assertEquals(StopButtonTouchDecision.NONE, touch.onDown())
        assertEquals(StopButtonTouchDecision.DRAG, touch.onMove(dx = 12f, dy = 0f))
        assertEquals(StopButtonTouchDecision.SAVE_POSITION, touch.onUp())
    }

    @Test
    fun stopButtonPositionPrefsAreGlobalAcrossAllScripts() {
        assertEquals("execution_stop_button_overlay", ExecutionStopButtonPositionPrefs.PREFS_NAME)
        assertEquals("x", ExecutionStopButtonPositionPrefs.KEY_X)
        assertEquals("y", ExecutionStopButtonPositionPrefs.KEY_Y)
    }
}
