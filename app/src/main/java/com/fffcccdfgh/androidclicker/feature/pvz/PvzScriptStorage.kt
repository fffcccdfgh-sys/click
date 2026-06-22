package com.fffcccdfgh.androidclicker.feature.pvz

import android.content.Context
import com.fffcccdfgh.androidclicker.core.execution.ActionStep
import com.fffcccdfgh.androidclicker.core.storage.LuaScriptCodec
import com.fffcccdfgh.androidclicker.core.storage.ScriptStorage
import org.json.JSONArray

object PvzScriptStorage {
    const val ACTION_SCRIPTS_CHANGED = "com.fffcccdfgh.androidclicker.PVZ_SCRIPTS_CHANGED"

    private const val PREFS_NAME = "pvz_script_storage"
    private const val KEY_SCRIPTS = "saved_scripts"

    fun nextAutoName(context: Context): String {
        val existingNames = loadAllScripts(context).map { it.name }.toSet()
        var index = 1
        var name: String
        do {
            name = "PVZ2脚本 $index"
            index++
        } while (name in existingNames)
        return name
    }

    fun listScripts(context: Context): List<ScriptStorage.SavedScript> = loadAllScripts(context)

    fun getScript(context: Context, name: String): ScriptStorage.SavedScript? =
        loadAllScripts(context).firstOrNull { it.name == name }

    fun deleteScript(context: Context, name: String) {
        val scripts = loadAllScripts(context).toMutableList()
        scripts.removeAll { it.name == name }
        saveAllScripts(context, scripts)
    }

    fun saveNamedScript(
        context: Context,
        name: String,
        actions: List<ActionStep>,
        loopCount: Int = 1,
        loopGapMs: Long = 0L
    ) {
        val scripts = loadAllScripts(context).toMutableList()
        scripts.removeAll { it.name == name }
        scripts.add(ScriptStorage.SavedScript(name, actions, loopCount.coerceAtLeast(0), loopGapMs.coerceAtLeast(0L)))
        saveAllScripts(context, scripts)
    }

    fun importScriptFromText(context: Context, text: String, fallbackName: String? = null): String {
        val trimmed = text.trimStart()
        val imported = if (trimmed.startsWith("{")) {
            importScriptFromJson(text)
        } else {
            val fallback = fallbackName?.substringBeforeLast('.')?.removePrefix("pvz2_")?.takeIf { it.isNotBlank() }
                ?: nextAutoName(context)
            LuaScriptCodec.importScript(text, fallback)
        }
        saveNamedScript(context, imported.name, imported.actions, imported.loopCount, imported.loopGapMs)
        return imported.name
    }

    fun exportScriptToLua(script: ScriptStorage.SavedScript): String {
        return LuaScriptCodec.exportScript(script)
    }

    private fun importScriptFromJson(json: String): ScriptStorage.SavedScript {
        val root = org.json.JSONObject(json)
        val actionsJson = root.getJSONArray("actions")
        val actions = mutableListOf<ActionStep>()
        for (i in 0 until actionsJson.length()) {
            actions.add(ActionStep.fromJson(actionsJson.getJSONObject(i)))
        }
        val name = if (root.has("name")) root.getString("name") else "PVZ2脚本"
        val loopCount = if (root.has("loopCount")) root.getInt("loopCount").coerceAtLeast(0) else 1
        val loopGapMs = if (root.has("loopGapMs")) root.getLong("loopGapMs").coerceAtLeast(0L) else 0L
        return ScriptStorage.SavedScript(name, actions, loopCount, loopGapMs)
    }

    private fun loadAllScripts(context: Context): List<ScriptStorage.SavedScript> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SCRIPTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<ScriptStorage.SavedScript>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val name = obj.getString("name")
                val actionsJson = obj.getString("actions")
                val actions = ActionStep.listFromJson(actionsJson)
                val loopCount = if (obj.has("loopCount")) obj.getInt("loopCount").coerceAtLeast(0) else 1
                val loopGapMs = if (obj.has("loopGapMs")) obj.getLong("loopGapMs").coerceAtLeast(0L) else 0L
                list.add(ScriptStorage.SavedScript(name, actions, loopCount, loopGapMs))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAllScripts(context: Context, scripts: List<ScriptStorage.SavedScript>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        for (script in scripts) {
            val obj = org.json.JSONObject()
            obj.put("name", script.name)
            obj.put("actions", ActionStep.listToJson(script.actions))
            obj.put("loopCount", script.loopCount)
            obj.put("loopGapMs", script.loopGapMs)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_SCRIPTS, arr.toString()).apply()
    }
}
