package com.fffcccdfgh.androidclicker

import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object ActionSequenceExecutor {
    private const val DEFAULT_DURATION_MS = 1L
    private const val DEFAULT_DELAY_BEFORE_MS = 1L
    private const val DEFAULT_STEP_GAP_MS = 1L

    @Volatile
    private var isExecuting = false

    private fun isDuplicateStart(): Boolean {
        if (isExecuting) return true
        isExecuting = true
        return false
    }

    private fun finishExecution() {
        isExecuting = false
    }

    fun execute(context: Context, sequence: List<ActionStep>) {
        if (isDuplicateStart()) {
            Toast.makeText(context, R.string.execution_already_running, Toast.LENGTH_SHORT).show()
            return
        }

        if (sequence.isEmpty()) {
            finishExecution()
            Toast.makeText(context, R.string.no_action_set, Toast.LENGTH_SHORT).show()
            return
        }

        val service = ClickAccessibilityService.instance
        if (service == null || !ClickAccessibilityService.isRunning) {
            finishExecution()
            Toast.makeText(context, R.string.tap_service_disabled, Toast.LENGTH_SHORT).show()
            return
        }

        executeStep(context, sequence, 0, service)
    }

    private fun executeStep(
        context: Context,
        sequence: List<ActionStep>,
        index: Int,
        service: ClickAccessibilityService
    ) {
        if (index >= sequence.size) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, R.string.sequence_done, Toast.LENGTH_SHORT).show()
            }
            finishExecution()
            return
        }

        val action = sequence[index]

        val delayBeforeMs = action.delayBeforeMs ?: DEFAULT_DELAY_BEFORE_MS
        val stepGapMs = action.stepGapMs ?: DEFAULT_STEP_GAP_MS

        val handler = Handler(Looper.getMainLooper())

        val effectiveDelay = delayBeforeMs.coerceAtLeast(1L)
        handler.postDelayed({
            performAction(context, sequence, index, service, stepGapMs.coerceAtLeast(1L))
        }, effectiveDelay)
    }

    private fun performAction(
        context: Context,
        sequence: List<ActionStep>,
        index: Int,
        service: ClickAccessibilityService,
        stepGapMs: Long
    ) {
        val action = sequence[index]
        val handler = Handler(Looper.getMainLooper())

        if (action.type == ActionStep.TYPE_WAIT) {
            val durationMs = (action.durationMs ?: DEFAULT_DURATION_MS).coerceAtLeast(1L)
            handler.postDelayed({
                scheduleNextStep(context, sequence, index + 1, service, stepGapMs)
            }, durationMs)
            return
        }

        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                scheduleNextStep(context, sequence, index + 1, service, stepGapMs)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                scheduleNextStep(context, sequence, index + 1, service, stepGapMs)
            }
        }

        val dispatched = when (action.type) {
            ActionStep.TYPE_TAP -> {
                val durationMs = (action.durationMs ?: DEFAULT_DURATION_MS).coerceAtLeast(1L)
                service.performTap(action.x!!, action.y!!, durationMs, callback)
            }
            ActionStep.TYPE_SWIPE -> {
                val durationMs = (action.durationMs ?: DEFAULT_DURATION_MS).coerceAtLeast(1L)
                service.performSwipe(
                    action.startX!!, action.startY!!, action.endX!!, action.endY!!,
                    durationMs, callback
                )
            }
            else -> false
        }

        if (!dispatched) {
            scheduleNextStep(context, sequence, index + 1, service, stepGapMs)
        }
    }

    private fun scheduleNextStep(
        context: Context,
        sequence: List<ActionStep>,
        nextIndex: Int,
        service: ClickAccessibilityService,
        stepGapMs: Long
    ) {
        Handler(Looper.getMainLooper()).postDelayed({
            executeStep(context, sequence, nextIndex, service)
        }, stepGapMs)
    }
}
