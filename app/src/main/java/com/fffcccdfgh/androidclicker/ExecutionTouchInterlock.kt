package com.fffcccdfgh.androidclicker

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

object ExecutionTouchInterlock {
    @Volatile
    private var dispatchPaused = false

    fun pauseDispatch() {
        dispatchPaused = true
    }

    fun resumeDispatch() {
        dispatchPaused = false
    }

    suspend fun awaitIfPaused() {
        while (dispatchPaused && coroutineContext.isActive) {
            delay(16L)
        }
    }

    fun waitIfPausedBlocking(shouldStop: () -> Boolean) {
        while (dispatchPaused && !shouldStop()) {
            try {
                Thread.sleep(16L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }
}
