package com.fffcccdfgh.androidclicker

import org.luaj.vm2.LuaValue
import java.util.Locale
import kotlin.math.roundToInt

data class ProgramScreenSize(
    val width: Int,
    val height: Int
)

object ProgramCoordinateAdapter {
    private const val STORED_PERCENT_SCALE = 100
    private const val STORED_PERCENT_MAX = 100 * STORED_PERCENT_SCALE
    const val STORED_PERCENT_FULL = 10000

    fun pointPercentSnippet(x: Int, y: Int, screenWidth: Int, screenHeight: Int): Pair<String, String> {
        return formatPercent(x, screenWidth) to formatPercent(y, screenHeight)
    }

    fun pointToStoredPercent(value: Int, axisSize: Int): Int {
        val safeAxis = axisSize.coerceAtLeast(1)
        return (value.toDouble() * 100.0 * STORED_PERCENT_SCALE / safeAxis.toDouble())
            .roundToInt()
            .coerceIn(0, STORED_PERCENT_MAX)
    }

    fun storedPercentToPointPx(value: Int, axisSize: Int): Int {
        return storedPercentToPx(value, axisSize, allowEnd = false)
    }

    fun storedPercentToEdgePx(value: Int, axisSize: Int): Int {
        return storedPercentToPx(value, axisSize, allowEnd = true)
    }

    fun formatStoredPercent(value: Int): String {
        return String.format(Locale.US, "%.2f%%", value.toDouble() / STORED_PERCENT_SCALE.toDouble())
    }

    fun formatStoredPercentArg(value: Int): String {
        return String.format(Locale.US, "%.2f", value.toDouble() / STORED_PERCENT_SCALE.toDouble())
    }

    fun parseStoredPercentArg(value: String): Int? {
        val number = value.trim().removeSuffix("%").trim().toDoubleOrNull() ?: return null
        return (number * STORED_PERCENT_SCALE)
            .roundToInt()
            .coerceIn(0, STORED_PERCENT_MAX)
    }

    fun storedActionToRuntimePx(action: ActionStep, screen: ProgramScreenSize): ActionStep {
        return action.copy(
            x = action.x?.let { storedPercentToPointPx(it, screen.width) },
            y = action.y?.let { storedPercentToPointPx(it, screen.height) },
            startX = action.startX?.let { storedPercentToPointPx(it, screen.width) },
            startY = action.startY?.let { storedPercentToPointPx(it, screen.height) },
            endX = action.endX?.let { storedPercentToPointPx(it, screen.width) },
            endY = action.endY?.let { storedPercentToPointPx(it, screen.height) },
            conditionLeft = action.conditionLeft?.let { storedPercentToEdgePx(it, screen.width) },
            conditionTop = action.conditionTop?.let { storedPercentToEdgePx(it, screen.height) },
            conditionRight = action.conditionRight?.let { storedPercentToEdgePx(it, screen.width) },
            conditionBottom = action.conditionBottom?.let { storedPercentToEdgePx(it, screen.height) },
            conditionColorX = action.conditionColorX?.let { storedPercentToPointPx(it, screen.width) },
            conditionColorY = action.conditionColorY?.let { storedPercentToPointPx(it, screen.height) }
        )
    }

    fun xArgToPointPx(arg: LuaValue, screenWidth: Int): Int {
        return argToPx(arg, screenWidth, allowEnd = false)
    }

    fun yArgToPointPx(arg: LuaValue, screenHeight: Int): Int {
        return argToPx(arg, screenHeight, allowEnd = false)
    }

    fun xArgToEdgePx(arg: LuaValue, screenWidth: Int): Int {
        return argToPx(arg, screenWidth, allowEnd = true)
    }

    fun yArgToEdgePx(arg: LuaValue, screenHeight: Int): Int {
        return argToPx(arg, screenHeight, allowEnd = true)
    }

    private fun formatPercent(value: Int, axisSize: Int): String {
        val safeAxis = axisSize.coerceAtLeast(1)
        val percent = value.toDouble() * 100.0 / safeAxis.toDouble()
        return String.format(Locale.US, "%.2f", percent)
    }

    private fun argToPx(arg: LuaValue, axisSize: Int, allowEnd: Boolean): Int {
        val safeAxis = axisSize.coerceAtLeast(1)
        val max = if (allowEnd) safeAxis else safeAxis - 1
        val raw = (arg.checkdouble() * safeAxis / 100.0).roundToInt()
        return raw.coerceIn(0, max)
    }

    private fun storedPercentToPx(value: Int, axisSize: Int, allowEnd: Boolean): Int {
        val safeAxis = axisSize.coerceAtLeast(1)
        val max = if (allowEnd) safeAxis else safeAxis - 1
        val raw = (value.toDouble() * safeAxis.toDouble() / (100.0 * STORED_PERCENT_SCALE)).roundToInt()
        return raw.coerceIn(0, max)
    }
}
