package com.fffcccdfgh.androidclicker.core.program

object ProgramScreenSizePolicy {
    fun choose(
        captureWidth: Int,
        captureHeight: Int,
        displayWidth: Int,
        displayHeight: Int,
        fallbackWidth: Int,
        fallbackHeight: Int
    ): ProgramScreenSize {
        if (captureWidth > 0 && captureHeight > 0) {
            return ProgramScreenSize(captureWidth, captureHeight)
        }
        if (displayWidth > 0 && displayHeight > 0) {
            return ProgramScreenSize(displayWidth, displayHeight)
        }
        return ProgramScreenSize(
            fallbackWidth.coerceAtLeast(1),
            fallbackHeight.coerceAtLeast(1)
        )
    }
}
