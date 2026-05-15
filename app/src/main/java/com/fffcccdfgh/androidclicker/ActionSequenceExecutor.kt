package com.fffcccdfgh.androidclicker

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ActionSequenceExecutor {
    private const val TAG = "ActionSeqExecutor"
    private const val CONDITION_RETRY_DELAY_MS = 100L

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Volatile
    private var runningJob: Job? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    var loopEnabled = false
    var loopCount = 1
    var loopGapMs = 0L

    // Execution generation counter to prevent stale finally blocks from
    // finishing a newer execution after stop() followed by a quick restart.
    private var executionId = 0

    var onStarted: (() -> Unit)? = null
    var onFinished: (() -> Unit)? = null
    var onStopped: (() -> Unit)? = null

    private var canDispatchAction: ((ActionStep) -> Boolean)? = null
    private var onBlocked: (() -> Unit)? = null

    fun stop() {
        if (!isRunning) return
        runningJob?.cancel()
        runningJob = null
        isRunning = false
        canDispatchAction = null
        onBlocked = null
        onStopped?.invoke()
    }

    private fun finishAndNotify() {
        isRunning = false
        canDispatchAction = null
        onBlocked = null
        onFinished?.invoke()
    }

    fun execute(
        context: Context,
        sequence: List<ActionStep>,
        canDispatchAction: ((ActionStep) -> Boolean)? = null,
        onBlocked: (() -> Unit)? = null
    ) {
        if (isRunning) {
            Toast.makeText(context, R.string.execution_already_running, Toast.LENGTH_SHORT).show()
            return
        }

        if (sequence.isEmpty()) {
            Toast.makeText(context, R.string.no_action_set, Toast.LENGTH_SHORT).show()
            return
        }

        val service = ClickAccessibilityService.instance
        if (service == null || !ClickAccessibilityService.isRunning) {
            Toast.makeText(context, R.string.tap_service_disabled, Toast.LENGTH_SHORT).show()
            return
        }

        this.canDispatchAction = canDispatchAction
        this.onBlocked = onBlocked

        executionId++
        val thisExecutionId = executionId
        isRunning = true
        onStarted?.invoke()

        val shouldLoop = loopEnabled
        val maxLoops = loopCount
        val loopGap = loopGapMs.coerceAtLeast(0L)

        runningJob = scope.launch {
            try {
                executeLoop(service, sequence, shouldLoop, maxLoops, loopGap)
            } catch (_: CancellationException) {
                // stop() already fired onStopped
            } finally {
                if (isRunning && executionId == thisExecutionId) {
                    finishAndNotify()
                }
            }
        }
    }

    private suspend fun executeLoop(
        service: ClickAccessibilityService,
        sequence: List<ActionStep>,
        shouldLoop: Boolean,
        maxLoops: Int,
        loopGap: Long
    ) {
        var loop = 0
        while (coroutineContext.isActive) {
            var i = 0
            while (i < sequence.size && coroutineContext.isActive) {
                val action = sequence[i]
                val repeatCount = action.repeatCount ?: 1

                if (repeatCount == 0) {
                    // Infinite repeat until stopped
                    while (coroutineContext.isActive) {
                        val executed = executeSingleAction(service, action)
                        if (!executed) {
                            delay(CONDITION_RETRY_DELAY_MS)
                        }
                    }
                    return
                }

                // Finite repeats
                var r = 0
                while (r < repeatCount && coroutineContext.isActive) {
                    executeSingleAction(service, action)
                    r++
                }
                if (!coroutineContext.isActive) return

                i++
            }

            if (!shouldLoop) break
            if (maxLoops > 0 && loop + 1 >= maxLoops) break
            if (loopGap > 0) delay(loopGap)
            loop++
        }
    }

    private suspend fun executeSingleAction(
        service: ClickAccessibilityService,
        action: ActionStep
    ): Boolean {
        if (!service.checkCondition(action)) {
            return false
        }

        val delayBefore = action.delayBeforeMs ?: 1L
        if (delayBefore > 0) {
            delay(delayBefore)
            if (!coroutineContext.isActive) return false
        }

        when (action.type) {
            ActionStep.TYPE_WAIT -> {
                delay((action.durationMs ?: 1L).coerceAtLeast(1L))
            }
            ActionStep.TYPE_PROGRAM -> {
                val code = action.code
                if (code != null) {
                    val result = ProgramActionParser.parse(code)
                    if (result.isSuccess) {
                        ProgramActionExecutor.execute(
                            service,
                            result.getOrThrow(),
                            canDispatchAction,
                            onBlocked
                        )
                    }
                }
            }
            else -> {
                val guard = canDispatchAction
                if (guard != null && !guard(action)) {
                    withContext(Dispatchers.Main) { onBlocked?.invoke() }
                    stop()
                    return true
                }
                service.dispatchGestureAwait(action)
            }
        }
        return true
    }
}
