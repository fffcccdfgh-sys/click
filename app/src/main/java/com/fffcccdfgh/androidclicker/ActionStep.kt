package com.fffcccdfgh.androidclicker

import org.json.JSONArray
import org.json.JSONObject

data class ActionStep(
    val type: String,
    val x: Int? = null,
    val y: Int? = null,
    val startX: Int? = null,
    val startY: Int? = null,
    val endX: Int? = null,
    val endY: Int? = null,
    val durationMs: Long? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        when (type) {
            TYPE_TAP -> {
                put("x", x)
                put("y", y)
            }
            TYPE_SWIPE -> {
                put("startX", startX)
                put("startY", startY)
                put("endX", endX)
                put("endY", endY)
            }
            TYPE_WAIT -> {
                put("durationMs", durationMs)
            }
        }
    }

    companion object {
        const val TYPE_TAP = "tap"
        const val TYPE_SWIPE = "swipe"
        const val TYPE_WAIT = "wait"

        fun fromJson(json: JSONObject): ActionStep {
            val type = json.getString("type")
            return when (type) {
                TYPE_TAP -> ActionStep(
                    type = TYPE_TAP,
                    x = json.getInt("x"),
                    y = json.getInt("y")
                )
                TYPE_SWIPE -> ActionStep(
                    type = TYPE_SWIPE,
                    startX = json.getInt("startX"),
                    startY = json.getInt("startY"),
                    endX = json.getInt("endX"),
                    endY = json.getInt("endY")
                )
                TYPE_WAIT -> ActionStep(
                    type = TYPE_WAIT,
                    durationMs = json.getLong("durationMs")
                )
                else -> throw IllegalArgumentException("Unknown action type: $type")
            }
        }

        fun listToJson(actions: List<ActionStep>): String {
            val arr = JSONArray()
            for (action in actions) {
                arr.put(action.toJson())
            }
            return arr.toString()
        }

        fun listFromJson(json: String): List<ActionStep> {
            val arr = JSONArray(json)
            val list = mutableListOf<ActionStep>()
            for (i in 0 until arr.length()) {
                list.add(fromJson(arr.getJSONObject(i)))
            }
            return list
        }
    }
}
