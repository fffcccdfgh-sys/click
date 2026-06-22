package com.fffcccdfgh.androidclicker

import com.fffcccdfgh.androidclicker.core.picker.PickerActionButtonsVisibilityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerActionButtonsVisibilityPolicyTest {
    @Test
    fun buttonsAreVisibleWhenPickerIsIdle() {
        assertTrue(PickerActionButtonsVisibilityPolicy.SHOW_WHEN_IDLE)
    }

    @Test
    fun buttonsHideOnlyAfterPointerMovementExceedsTouchSlop() {
        assertFalse(
            PickerActionButtonsVisibilityPolicy.shouldHideWhileChanging(
                moveDistance = 7f,
                touchSlop = 8f
            )
        )
        assertTrue(
            PickerActionButtonsVisibilityPolicy.shouldHideWhileChanging(
                moveDistance = 9f,
                touchSlop = 8f
            )
        )
    }

    @Test
    fun buttonsShowImmediatelyAfterTapAndAfterIdleDelayAfterDrag() {
        assertEquals(
            PickerActionButtonsVisibilityPolicy.ShowTiming.NOW,
            PickerActionButtonsVisibilityPolicy.showTimingAfterTouchEnd(
                moveDistance = 7f,
                touchSlop = 8f
            )
        )
        assertEquals(
            PickerActionButtonsVisibilityPolicy.ShowTiming.AFTER_IDLE_DELAY,
            PickerActionButtonsVisibilityPolicy.showTimingAfterTouchEnd(
                moveDistance = 9f,
                touchSlop = 8f
            )
        )
    }
}
