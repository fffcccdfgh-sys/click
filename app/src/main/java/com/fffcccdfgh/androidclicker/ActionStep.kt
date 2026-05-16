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
    val durationMs: Long? = null,
    val delayBeforeMs: Long? = null,
    val repeatCount: Int? = null,
    val markerX: Int? = null,
    val markerY: Int? = null,
    val code: String? = null,
    val conditionType: String? = null,
    val conditionText: String? = null,
    val conditionUseArea: Boolean? = null,
    val conditionLeft: Int? = null,
    val conditionTop: Int? = null,
    val conditionRight: Int? = null,
    val conditionBottom: Int? = null,
    val conditionColorHex: String? = null,
    val conditionColorTolerance: Int? = null,
    val conditionColorX: Int? = null,
    val conditionColorY: Int? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        when (type) {
            TYPE_TAP -> {
                put("x", x)
                put("y", y)
                durationMs?.let { put("durationMs", it) }
            }
            TYPE_SWIPE -> {
                put("startX", startX)
                put("startY", startY)
                put("endX", endX)
                put("endY", endY)
                durationMs?.let { put("durationMs", it) }
            }
            TYPE_WAIT -> {
                put("durationMs", durationMs)
                markerX?.let { put("markerX", it) }
                markerY?.let { put("markerY", it) }
            }
            TYPE_PROGRAM -> {
                code?.let { put("code", it) }
                markerX?.let { put("markerX", it) }
                markerY?.let { put("markerY", it) }
            }
        }
        delayBeforeMs?.let { put("delayBeforeMs", it) }
        repeatCount?.let { put("repeatCount", it) }
        conditionType?.let { put("conditionType", it) }
        conditionText?.let { put("conditionText", it) }
        conditionUseArea?.let { put("conditionUseArea", it) }
        conditionLeft?.let { put("conditionLeft", it) }
        conditionTop?.let { put("conditionTop", it) }
        conditionRight?.let { put("conditionRight", it) }
        conditionBottom?.let { put("conditionBottom", it) }
        conditionColorHex?.let { put("conditionColorHex", it) }
        conditionColorTolerance?.let { put("conditionColorTolerance", it) }
        conditionColorX?.let { put("conditionColorX", it) }
        conditionColorY?.let { put("conditionColorY", it) }
    }

    companion object {
        const val TYPE_TAP = "tap"
        const val TYPE_SWIPE = "swipe"
        const val TYPE_WAIT = "wait"
        const val TYPE_PROGRAM = "program"

        const val CONDITION_TEXT_CONTAINS = "text_contains"
        const val CONDITION_TEXT_NOT_CONTAINS = "text_not_contains"
        const val CONDITION_COLOR_MATCH = "color_match"
        const val CONDITION_COLOR_NOT_MATCH = "color_not_match"

        fun fromJson(json: JSONObject): ActionStep {
            val type = json.getString("type")
            val delayBeforeMs = if (json.has("delayBeforeMs")) json.getLong("delayBeforeMs") else null
            val repeatCount = if (json.has("repeatCount")) json.getInt("repeatCount").coerceAtLeast(0) else null
            val conditionType = if (json.has("conditionType")) json.getString("conditionType") else null
            val conditionText = if (json.has("conditionText")) json.getString("conditionText") else null
            val conditionUseArea = if (json.has("conditionUseArea")) json.getBoolean("conditionUseArea") else null
            val conditionLeft = if (json.has("conditionLeft")) json.getInt("conditionLeft") else null
            val conditionTop = if (json.has("conditionTop")) json.getInt("conditionTop") else null
            val conditionRight = if (json.has("conditionRight")) json.getInt("conditionRight") else null
            val conditionBottom = if (json.has("conditionBottom")) json.getInt("conditionBottom") else null
            val conditionColorHex = if (json.has("conditionColorHex")) json.getString("conditionColorHex") else null
            val conditionColorTolerance = if (json.has("conditionColorTolerance")) json.getInt("conditionColorTolerance") else null
            val conditionColorX = if (json.has("conditionColorX")) json.getInt("conditionColorX") else null
            val conditionColorY = if (json.has("conditionColorY")) json.getInt("conditionColorY") else null
            return when (type) {
                TYPE_TAP -> ActionStep(
                    type = TYPE_TAP,
                    x = json.getInt("x"),
                    y = json.getInt("y"),
                    durationMs = if (json.has("durationMs")) json.getLong("durationMs") else null,
                    delayBeforeMs = delayBeforeMs,
                    repeatCount = repeatCount,
                    conditionType = conditionType,
                    conditionText = conditionText,
                    conditionUseArea = conditionUseArea,
                    conditionLeft = conditionLeft,
                    conditionTop = conditionTop,
                    conditionRight = conditionRight,
                    conditionBottom = conditionBottom,
                    conditionColorHex = conditionColorHex,
                    conditionColorTolerance = conditionColorTolerance,
                    conditionColorX = conditionColorX,
                    conditionColorY = conditionColorY
                )
                TYPE_SWIPE -> ActionStep(
                    type = TYPE_SWIPE,
                    startX = json.getInt("startX"),
                    startY = json.getInt("startY"),
                    endX = json.getInt("endX"),
                    endY = json.getInt("endY"),
                    durationMs = if (json.has("durationMs")) json.getLong("durationMs") else null,
                    delayBeforeMs = delayBeforeMs,
                    repeatCount = repeatCount,
                    conditionType = conditionType,
                    conditionText = conditionText,
                    conditionUseArea = conditionUseArea,
                    conditionLeft = conditionLeft,
                    conditionTop = conditionTop,
                    conditionRight = conditionRight,
                    conditionBottom = conditionBottom,
                    conditionColorHex = conditionColorHex,
                    conditionColorTolerance = conditionColorTolerance,
                    conditionColorX = conditionColorX,
                    conditionColorY = conditionColorY
                )
                TYPE_WAIT -> ActionStep(
                    type = TYPE_WAIT,
                    durationMs = json.getLong("durationMs"),
                    delayBeforeMs = delayBeforeMs,
                    repeatCount = repeatCount,
                    markerX = if (json.has("markerX")) json.getInt("markerX") else null,
                    markerY = if (json.has("markerY")) json.getInt("markerY") else null,
                    conditionType = conditionType,
                    conditionText = conditionText,
                    conditionUseArea = conditionUseArea,
                    conditionLeft = conditionLeft,
                    conditionTop = conditionTop,
                    conditionRight = conditionRight,
                    conditionBottom = conditionBottom,
                    conditionColorHex = conditionColorHex,
                    conditionColorTolerance = conditionColorTolerance,
                    conditionColorX = conditionColorX,
                    conditionColorY = conditionColorY
                )
                TYPE_PROGRAM -> ActionStep(
                    type = TYPE_PROGRAM,
                    code = if (json.has("code")) json.getString("code") else null,
                    delayBeforeMs = delayBeforeMs,
                    repeatCount = repeatCount,
                    markerX = if (json.has("markerX")) json.getInt("markerX") else null,
                    markerY = if (json.has("markerY")) json.getInt("markerY") else null,
                    conditionType = conditionType,
                    conditionText = conditionText,
                    conditionUseArea = conditionUseArea,
                    conditionLeft = conditionLeft,
                    conditionTop = conditionTop,
                    conditionRight = conditionRight,
                    conditionBottom = conditionBottom,
                    conditionColorHex = conditionColorHex,
                    conditionColorTolerance = conditionColorTolerance,
                    conditionColorX = conditionColorX,
                    conditionColorY = conditionColorY
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
