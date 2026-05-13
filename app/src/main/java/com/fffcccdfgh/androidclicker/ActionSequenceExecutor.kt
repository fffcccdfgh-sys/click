package com.fffcccdfgh.androidclicker

import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object ActionSequenceExecutor {
    private const val STEP_GAP_MS = 500L

    fun execute(context: Context, sequence: List<ActionStep>) {
        if (sequence.isEmpty()) {
            Toast.makeText(context, R.string.no_action_set, Toast.LENGTH_SHORT).show()
            return
        }

        val service = ClickAccessibilityService.instance
        if (service == null || !ClickAccessibilityService.isRunning) {
            Toast.makeText(context, R.string.tap_service_disabled, Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(context, context.getString(R.string.sequence_executing_step, 1, sequence.size), Toast.LENGTH_SHORT).show()
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
            return
        }

        val action = sequence[index]

        if (action.type == ActionStep.TYPE_WAIT) {
            val durationMs = action.durationMs ?: 1000L
            Handler(Looper.getMainLooper()).postDelayed({
                executeStep(context, sequence, index + 1, service)
            }, durationMs)
            return
        }

        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                scheduleNextStep(context, sequence, index + 1, service)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                scheduleNextStep(context, sequence, index + 1, service)
            }
        }

        val dispatched = when (action.type) {
            ActionStep.TYPE_TAP -> service.performTap(action.x!!, action.y!!, callback)
            ActionStep.TYPE_SWIPE -> service.performSwipe(
                action.startX!!, action.startY!!, action.endX!!, action.endY!!, 300, callback
            )
            else -> false
        }

        if (!dispatched) {
            scheduleNextStep(context, sequence, index + 1, service)
        }
    }

    private fun scheduleNextStep(
        context: Context,
        sequence: List<ActionStep>,
        nextIndex: Int,
        service: ClickAccessibilityService
    ) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (nextIndex < sequence.size) {
                Toast.makeText(context, context.getString(R.string.sequence_executing_step, nextIndex + 1, sequence.size), Toast.LENGTH_SHORT).show()
            }
            executeStep(context, sequence, nextIndex, service)
        }, STEP_GAP_MS)
    }
}
