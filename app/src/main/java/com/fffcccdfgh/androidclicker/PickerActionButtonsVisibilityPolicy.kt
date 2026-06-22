package com.fffcccdfgh.androidclicker

object PickerActionButtonsVisibilityPolicy {
    const val SHOW_WHEN_IDLE = true

    enum class ShowTiming {
        NOW,
        AFTER_IDLE_DELAY
    }

    fun shouldHideWhileChanging(moveDistance: Float, touchSlop: Float): Boolean {
        return moveDistance > touchSlop
    }

    fun showTimingAfterTouchEnd(moveDistance: Float, touchSlop: Float): ShowTiming {
        return if (shouldHideWhileChanging(moveDistance, touchSlop)) {
            ShowTiming.AFTER_IDLE_DELAY
        } else {
            ShowTiming.NOW
        }
    }
}
