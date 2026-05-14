package com.fffcccdfgh.androidclicker

import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.GestureDescription
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

object ActionSequenceExecutor {
    private const val TAG = "ActionSeqExecutor"
    private const val BATCH_MAX_ESTIMATED_MS = 40L
    private const val BATCH_MAX_COUNT = 20

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Volatile
    private var runningJob: Job? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    var loopEnabled = false
    var loopCount = 1
    var loopGapMs = 0L

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
                if (isRunning) {
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

                val delayBefore = action.delayBeforeMs ?: 1L
                if (delayBefore > 0) {
                    delay(delayBefore)
                    if (!coroutineContext.isActive) return
                }

                when (action.type) {
                    ActionStep.TYPE_WAIT -> {
                        delay((action.durationMs ?: 1L).coerceAtLeast(1L))
                        i++
                    }
                    else -> {
                        val guard = canDispatchAction
                        if (guard != null && !guard(action)) {
                            withContext(Dispatchers.Main) { onBlocked?.invoke() }
                            stop()
                            return
                        }

                        // Batch consecutive non-wait actions for speed.
                        // Each batch is capped so stop() latency stays low.
                        val batchStart = i
                        var batchMs = action.durationMs ?: 1L
                        var batchCount = 1
                        i++

                        while (i < sequence.size && coroutineContext.isActive) {
                            val next = sequence[i]
                            if (next.type == ActionStep.TYPE_WAIT) break

                            val nextGuard = canDispatchAction
                            if (nextGuard != null && !nextGuard(next)) break

                            val nd = next.durationMs ?: 1L
                            if (batchMs + nd > BATCH_MAX_ESTIMATED_MS) break
                            if (batchCount >= BATCH_MAX_COUNT) break

                            batchMs += nd
                            batchCount++
                            i++
                        }

                        val batch = sequence.subList(batchStart, i)
                        dispatchBatch(service, batch)

                        val gap = action.stepGapMs ?: 1L
                        if (gap > 0) delay(gap)
                    }
                }
            }

            if (!shouldLoop) break
            if (maxLoops > 0 && loop + 1 >= maxLoops) break
            if (loopGap > 0) delay(loopGap)
            loop++
        }
    }

    private suspend fun dispatchBatch(
        service: ClickAccessibilityService,
        batch: List<ActionStep>
    ) {
        if (batch.size == 1) {
            service.dispatchGestureAwait(batch[0])
            return
        }

        try {
            suspendCancellableCoroutine<Unit> { cont ->
                val pending = AtomicInteger(batch.size)

                for (action in batch) {
                    val cb = object : GestureResultCallback() {
                        override fun onCompleted(desc: GestureDescription?) {
                            if (pending.decrementAndGet() == 0 && cont.isActive) {
                                cont.resume(Unit)
                            }
                        }

                        override fun onCancelled(desc: GestureDescription?) {
                            if (pending.decrementAndGet() == 0 && cont.isActive) {
                                cont.resume(Unit)
                            }
                        }
                    }

                    val ok = when (action.type) {
                        ActionStep.TYPE_TAP -> service.performTap(
                            action.x!!, action.y!!,
                            action.durationMs ?: 1L, cb
                        )
                        ActionStep.TYPE_SWIPE -> service.performSwipe(
                            action.startX!!, action.startY!!,
                            action.endX!!, action.endY!!,
                            action.durationMs ?: 1L, cb
                        )
                        else -> false
                    }

                    if (!ok && pending.decrementAndGet() == 0 && cont.isActive) {
                        cont.resume(Unit)
                    }
                }
            }
        } catch (_: CancellationException) {
            // Coroutine cancelled — already-dispatched gestures will still
            // execute in the system, but the batch was capped small.
            throw CancellationException("batch cancelled")
        }
    }
}
