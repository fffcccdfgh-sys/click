package com.fffcccdfgh.androidclicker.feature.clicker.floating

import android.view.WindowManager

object LoopSettingsWindowPolicy {
    const val FLAGS: Int = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

    const val SOFT_INPUT_MODE: Int =
        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
}
