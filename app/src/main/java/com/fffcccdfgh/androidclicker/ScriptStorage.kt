package com.fffcccdfgh.androidclicker

import android.content.Context
import org.json.JSONArray

object ScriptStorage {
    const val ACTION_SCRIPTS_CHANGED = "com.fffcccdfgh.androidclicker.SCRIPTS_CHANGED"

    private const val PREFS_NAME = "script_storage"
    private const val KEY_SCRIPTS = "saved_scripts"

    data class SavedScript(val name: String, val actions: List<ActionStep>)

    fun nextAutoName(context: Context): String {
        val existingNames = loadAllScripts(context).map { it.name }.toSet()
        var index = 1
        var name: String
        do {
            name = "脚本 $index"
            index++
        } while (name in existingNames)
        return name
    }

    fun saveAutoNamedScript(context: Context, actions: List<ActionStep>): String {
        val name = nextAutoName(context)
        val scripts = loadAllScripts(context).toMutableList()
        scripts.add(SavedScript(name, actions))
        saveAllScripts(context, scripts)
        return name
    }

    fun listScripts(context: Context): List<SavedScript> = loadAllScripts(context)

    fun getScript(context: Context, name: String): SavedScript? =
        loadAllScripts(context).firstOrNull { it.name == name }

    fun deleteScript(context: Context, name: String) {
        val scripts = loadAllScripts(context).toMutableList()
        scripts.removeAll { it.name == name }
        saveAllScripts(context, scripts)
    }

    fun exportScriptToJson(script: SavedScript): String {
        val arr = JSONArray()
        for (action in script.actions) {
            arr.put(action.toJson())
        }
        val root = org.json.JSONObject()
        root.put("name", script.name)
        root.put("actions", arr)
        return root.toString(2)
    }

    fun importScriptFromJson(context: Context, json: String): String {
        val root = org.json.JSONObject(json)
        val actionsJson = root.getJSONArray("actions")
        val actions = mutableListOf<ActionStep>()
        for (i in 0 until actionsJson.length()) {
            actions.add(ActionStep.fromJson(actionsJson.getJSONObject(i)))
        }
        val name = if (root.has("name")) root.getString("name") else null
        return if (name != null) {
            saveNamedScript(context, name, actions)
            name
        } else {
            saveAutoNamedScript(context, actions)
        }
    }

    fun saveNamedScript(context: Context, name: String, actions: List<ActionStep>) {
        val scripts = loadAllScripts(context).toMutableList()
        scripts.removeAll { it.name == name }
        scripts.add(SavedScript(name, actions))
        saveAllScripts(context, scripts)
    }

    private fun loadAllScripts(context: Context): List<SavedScript> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SCRIPTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<SavedScript>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val name = obj.getString("name")
                val actionsJson = obj.getString("actions")
                val actions = ActionStep.listFromJson(actionsJson)
                list.add(SavedScript(name, actions))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAllScripts(context: Context, scripts: List<SavedScript>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        for (script in scripts) {
            val obj = org.json.JSONObject()
            obj.put("name", script.name)
            obj.put("actions", ActionStep.listToJson(script.actions))
            arr.put(obj)
        }
        prefs.edit().putString(KEY_SCRIPTS, arr.toString()).apply()
    }
}
