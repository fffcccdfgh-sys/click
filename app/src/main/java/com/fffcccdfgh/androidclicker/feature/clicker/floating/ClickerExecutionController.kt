package com.fffcccdfgh.androidclicker.feature.clicker.floating

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.fffcccdfgh.androidclicker.R
import com.fffcccdfgh.androidclicker.core.execution.ActionSequenceExecutor
import com.fffcccdfgh.androidclicker.core.execution.ActionStep
import com.fffcccdfgh.androidclicker.core.execution.ControlZoneChecker

class ClickerExecutionController(
    private val context: Context,
    private val loadSequence: () -> List<ActionStep>,
    private val hideBeforeRun: () -> Unit,
    private val setFloatingTouchThrough: (Boolean) -> Unit,
    private val showExecutionStopButton: () -> Unit,
    private val hideExecutionStopButton: () -> Unit,
    private val updateStartStopButtons: (Boolean) -> Unit,
    private val controlZonePaddingPx: () -> Int
) {
    fun executeSequence() {
        hideBeforeRun()

        val count = FloatingControlService.getLoopCount(context)
        ActionSequenceExecutor.loopCount = count
        ActionSequenceExecutor.loopEnabled = count != 1
        ActionSequenceExecutor.loopGapMs = FloatingControlService.getLoopGapMs(context)
        Log.d(
            STOP_DEBUG_TAG,
            "ClickerExecutionController.executeSequence loopCount=$count loopEnabled=${ActionSequenceExecutor.loopEnabled} loopGapMs=${ActionSequenceExecutor.loopGapMs}"
        )

        ActionSequenceExecutor.onStarted = {
            Log.d(STOP_DEBUG_TAG, "ClickerExecutionController.onStarted fired")
            setFloatingTouchThrough(true)
            showExecutionStopButton()
            updateStartStopButtons(true)
        }
        ActionSequenceExecutor.onFinished = {
            Log.d(STOP_DEBUG_TAG, "ClickerExecutionController.onFinished fired")
            hideExecutionStopButton()
            setFloatingTouchThrough(false)
            updateStartStopButtons(false)
        }
        ActionSequenceExecutor.onStopped = {
            Log.d(STOP_DEBUG_TAG, "ClickerExecutionController.onStopped fired")
            hideExecutionStopButton()
            setFloatingTouchThrough(false)
            updateStartStopButtons(false)
        }

        val sequence = loadSequence()
        Log.d(STOP_DEBUG_TAG, "ClickerExecutionController.executeSequence sequenceSize=${sequence.size}")

        val paddingPx = controlZonePaddingPx()
        ActionSequenceExecutor.execute(
            context,
            sequence,
            canDispatchAction = { action ->
                !ControlZoneChecker.isActionInAnyZone(action, paddingPx)
            },
            onBlocked = {
                Toast.makeText(context, R.string.action_overlaps_control_stopped, Toast.LENGTH_SHORT).show()
            }
        )
    }

    fun stopExecution() {
        ActionSequenceExecutor.stop()
    }

    private companion object {
        const val STOP_DEBUG_TAG = "ClickerStopDebug"
    }
}
