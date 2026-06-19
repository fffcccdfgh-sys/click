package com.fffcccdfgh.androidclicker.feature.clicker.floating

import android.content.Context
import com.fffcccdfgh.androidclicker.core.execution.ActionStep
import com.fffcccdfgh.androidclicker.core.storage.ScriptStorage

class ClickerScriptController(
    private val context: Context
) {
    private var pendingInsertIndex: Int? = null

    fun loadSequence(): List<ActionStep> {
        val json = prefs().getString(FloatingControlService.KEY_ACTION_SEQUENCE, null) ?: return emptyList()
        return try {
            ActionStep.listFromJson(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveSequence(sequence: List<ActionStep>) {
        prefs()
            .edit()
            .putString(FloatingControlService.KEY_ACTION_SEQUENCE, ActionStep.listToJson(sequence))
            .apply()
    }

    fun appendToSequence(action: ActionStep) {
        val sequence = loadSequence().toMutableList()
        val insertAt = pendingInsertIndex
        if (insertAt != null && insertAt >= 0 && insertAt <= sequence.size) {
            sequence.add(insertAt, action)
            pendingInsertIndex = null
        } else {
            sequence.add(action)
        }
        saveSequence(sequence)
    }

    fun getEditingScriptName(): String? =
        prefs().getString(FloatingControlService.KEY_EDITING_SCRIPT_NAME, null)

    private fun setEditingScriptName(name: String?) {
        val editor = prefs().edit()
        if (name != null) {
            editor.putString(FloatingControlService.KEY_EDITING_SCRIPT_NAME, name)
        } else {
            editor.remove(FloatingControlService.KEY_EDITING_SCRIPT_NAME)
        }
        editor.apply()
    }

    fun saveCurrentSequenceWithConfirmedName(name: String, sequence: List<ActionStep>) {
        ScriptStorage.saveNamedScript(
            context,
            name,
            sequence,
            FloatingControlService.getLoopCount(context),
            FloatingControlService.getLoopGapMs(context)
        )
        setEditingScriptName(name)
    }

    fun nextAutoName(): String = ScriptStorage.nextAutoName(context)

    fun hasSavedScript(name: String): Boolean = ScriptStorage.getScript(context, name) != null

    fun scriptsChangedAction(): String = ScriptStorage.ACTION_SCRIPTS_CHANGED

    fun setPendingInsertIndex(index: Int) {
        pendingInsertIndex = index
    }

    fun clearPendingInsertIndex() {
        pendingInsertIndex = null
    }

    private fun prefs() = context.getSharedPreferences(
        FloatingControlService.PREFS_NAME,
        Context.MODE_PRIVATE
    )
}
