package com.fffcccdfgh.androidclicker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

object ProgramActionExecutor {

    suspend fun execute(
        service: ClickAccessibilityService,
        commands: List<ProgramCommand>,
        canDispatchAction: ((ActionStep) -> Boolean)?,
        onBlocked: (() -> Unit)?
    ) {
        for (cmd in commands) {
            if (!coroutineContext.isActive) return
            executeCommand(service, cmd, canDispatchAction, onBlocked)
            if (!coroutineContext.isActive) return
        }
    }

    private suspend fun executeCommand(
        service: ClickAccessibilityService,
        cmd: ProgramCommand,
        canDispatchAction: ((ActionStep) -> Boolean)?,
        onBlocked: (() -> Unit)?
    ) {
        when (cmd) {
            is ProgramCommand.TapCmd -> {
                val action = ActionStep(
                    type = ActionStep.TYPE_TAP,
                    x = cmd.x, y = cmd.y,
                    durationMs = cmd.durationMs
                )
                val guard = canDispatchAction
                if (guard != null && !guard(action)) {
                    withContext(Dispatchers.Main) { onBlocked?.invoke() }
                    ActionSequenceExecutor.stop()
                    return
                }
                service.dispatchGestureAwait(action)
            }
            is ProgramCommand.SwipeCmd -> {
                val action = ActionStep(
                    type = ActionStep.TYPE_SWIPE,
                    startX = cmd.startX, startY = cmd.startY,
                    endX = cmd.endX, endY = cmd.endY,
                    durationMs = cmd.durationMs
                )
                val guard = canDispatchAction
                if (guard != null && !guard(action)) {
                    withContext(Dispatchers.Main) { onBlocked?.invoke() }
                    ActionSequenceExecutor.stop()
                    return
                }
                service.dispatchGestureAwait(action)
            }
            is ProgramCommand.WaitCmd -> {
                delay(cmd.ms)
            }
            is ProgramCommand.RepeatCmd -> {
                if (cmd.count == 0) {
                    while (coroutineContext.isActive) {
                        for (subCmd in cmd.commands) {
                            if (!coroutineContext.isActive) return
                            executeCommand(service, subCmd, canDispatchAction, onBlocked)
                        }
                    }
                } else {
                    var r = 0
                    while (r < cmd.count && coroutineContext.isActive) {
                        for (subCmd in cmd.commands) {
                            if (!coroutineContext.isActive) return
                            executeCommand(service, subCmd, canDispatchAction, onBlocked)
                        }
                        r++
                    }
                }
            }
        }
    }
}
