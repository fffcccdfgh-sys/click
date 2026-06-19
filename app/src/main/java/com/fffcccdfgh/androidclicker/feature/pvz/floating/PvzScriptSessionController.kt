package com.fffcccdfgh.androidclicker.feature.pvz.floating

import android.content.Context
import android.content.Intent
import com.fffcccdfgh.androidclicker.core.execution.ActionStep
import com.fffcccdfgh.androidclicker.feature.pvz.PvzScriptStorage

class PvzScriptSessionController(
    private val context: Context,
    private val updateTitle: () -> Unit
) {
    fun loadCurrentProgramCode(): String {
        return prefs().getString(KEY_PROGRAM_CODE, "").orEmpty()
    }

    fun saveCurrentProgramDraft(code: String) {
        prefs()
            .edit()
            .putString(KEY_PROGRAM_CODE, code)
            .apply()
    }

    fun saveCurrentProgramCode(name: String, code: String) {
        val prefs = prefs()
        val scriptName = prefs.getString(KEY_SCRIPT_NAME, null)
            ?.takeIf { it.isNotBlank() }
        if (scriptName != null && scriptName != name) {
            PvzScriptStorage.deleteScript(context, scriptName)
        }
        prefs.edit()
            .putString(KEY_PROGRAM_CODE, code)
            .putString(KEY_SCRIPT_NAME, name)
            .apply()
        PvzScriptStorage.saveNamedScript(
            context,
            name,
            listOf(
                ActionStep(
                    type = ActionStep.TYPE_PROGRAM,
                    code = code,
                    delayBeforeMs = 1L,
                    repeatCount = 1
                )
            ),
            loopCount = 1,
            loopGapMs = 0L
        )
        context.sendBroadcast(Intent(PvzScriptStorage.ACTION_SCRIPTS_CHANGED).apply {
            setPackage(context.packageName)
        })
        updateTitle()
    }

    fun getCurrentScriptName(): String? {
        return prefs().getString(KEY_SCRIPT_NAME, null)
            ?.takeIf { it.isNotBlank() }
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "pvz2_script_config"
        const val KEY_PROGRAM_CODE = "program_code"
        const val KEY_SCRIPT_NAME = "script_name"
        const val KEY_USB_SYNC_SIGNATURE = "usb_sync_signature"
    }
}
