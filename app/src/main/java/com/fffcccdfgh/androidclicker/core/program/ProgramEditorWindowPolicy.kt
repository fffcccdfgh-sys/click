package com.fffcccdfgh.androidclicker.core.program

import android.view.WindowManager

object ProgramEditorWindowPolicy {
    const val CODE_PANEL_HEIGHT_RATIO: Float = 0.40f

    const val FLAGS: Int =
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    const val SOFT_INPUT_MODE: Int =
        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

    fun openCursorPosition(restoreCursor: Int?, codeLength: Int): Int {
        return restoreCursor?.coerceIn(0, codeLength.coerceAtLeast(0)) ?: 0
    }
}
