package com.fffcccdfgh.androidclicker

object LuaScriptCodec {
    private const val HEADER = "-- AndroidClicker Lua Script"

    fun exportScript(script: ScriptStorage.SavedScript): String {
        val body = exportActions(script.actions, "")
        val wrappedBody = wrapScriptLoop(body, script.loopCount, script.loopGapMs)
        return buildString {
            append(wrappedBody)
            if (!endsWith("\n")) appendLine()
        }
    }

    fun importScript(lua: String, fallbackName: String): ScriptStorage.SavedScript {
        val name = parseName(lua).ifBlank { fallbackName }
        return ScriptStorage.SavedScript(
            name = name,
            actions = listOf(
                ActionStep(
                    type = ActionStep.TYPE_PROGRAM,
                    code = stripCodecMetadata(lua).trim()
                )
            ),
            loopCount = 1,
            loopGapMs = 0L
        )
    }

    private fun wrapScriptLoop(body: String, loopCount: Int, loopGapMs: Long): String {
        val safeLoopGap = loopGapMs.coerceAtLeast(0L)
        return when {
            loopCount == 0 -> buildString {
                appendLine("while true do")
                append(indentBlock(body, "    "))
                if (safeLoopGap > 0) appendLine("    wait($safeLoopGap)")
                appendLine("end")
            }
            loopCount > 1 -> buildString {
                appendLine("for __loop = 1, $loopCount do")
                append(indentBlock(body, "    "))
                if (safeLoopGap > 0) {
                    appendLine("    if __loop < $loopCount then")
                    appendLine("        wait($safeLoopGap)")
                    appendLine("    end")
                }
                appendLine("end")
            }
            else -> body
        }
    }

    private fun exportActions(actions: List<ActionStep>, indent: String): String {
        return buildString {
            for (action in actions) {
                append(exportActionWithRepeat(action, indent))
            }
        }
    }

    private fun exportActionWithRepeat(action: ActionStep, indent: String): String {
        val repeatCount = action.repeatCount ?: 1
        return when {
            repeatCount == 0 -> buildString {
                appendLine("${indent}while true do")
                append(exportActionOnce(action.copy(repeatCount = null), "$indent    "))
                appendLine("${indent}end")
            }
            repeatCount > 1 -> buildString {
                appendLine("${indent}for __repeat = 1, $repeatCount do")
                append(exportActionOnce(action.copy(repeatCount = null), "$indent    "))
                appendLine("${indent}end")
            }
            else -> exportActionOnce(action, indent)
        }
    }

    private fun exportActionOnce(action: ActionStep, indent: String): String {
        val condition = conditionExpression(action)
        return buildString {
            if (condition != null) {
                appendLine("${indent}if $condition then")
                appendDelay(action, "$indent    ")
                appendActionBody(action, "$indent    ")
                appendLine("${indent}end")
            } else {
                appendDelay(action, indent)
                appendActionBody(action, indent)
            }
        }
    }

    private fun StringBuilder.appendDelay(action: ActionStep, indent: String) {
        val delayBefore = action.delayBeforeMs ?: 0L
        if (delayBefore > 0) appendLine("${indent}wait($delayBefore)")
    }

    private fun StringBuilder.appendActionBody(action: ActionStep, indent: String) {
        when (action.type) {
            ActionStep.TYPE_TAP -> {
                val duration = action.durationMs ?: 1L
                appendLine(
                    "${indent}tap(" +
                        "${percentArg(action.x)}, ${percentArg(action.y)}, $duration)"
                )
            }
            ActionStep.TYPE_SWIPE -> {
                val duration = action.durationMs ?: 300L
                appendLine(
                    "${indent}swipe(" +
                        "${percentArg(action.startX)}, ${percentArg(action.startY)}, " +
                        "${percentArg(action.endX)}, ${percentArg(action.endY)}, $duration)"
                )
            }
            ActionStep.TYPE_WAIT -> {
                appendLine("${indent}wait(${action.durationMs ?: 1L})")
            }
            ActionStep.TYPE_PROGRAM -> {
                val code = action.code.orEmpty().trim()
                if (code.isEmpty()) return
                for (line in code.lines()) {
                    appendLine("$indent$line")
                }
            }
        }
    }

    private fun conditionExpression(action: ActionStep): String? {
        return when (action.conditionType) {
            ActionStep.CONDITION_TEXT_CONTAINS ->
                textCondition("check_text", action)
            ActionStep.CONDITION_TEXT_NOT_CONTAINS ->
                textCondition("check_text_not", action)
            ActionStep.CONDITION_COLOR_MATCH ->
                colorCondition("check_color", action)
            ActionStep.CONDITION_COLOR_NOT_MATCH ->
                colorCondition("check_color_not", action)
            else -> null
        }
    }

    private fun textCondition(functionName: String, action: ActionStep): String {
        val text = luaString(action.conditionText.orEmpty())
        val left = action.conditionLeft ?: 0
        val top = action.conditionTop ?: 0
        val right = action.conditionRight ?: ProgramCoordinateAdapter.STORED_PERCENT_FULL
        val bottom = action.conditionBottom ?: ProgramCoordinateAdapter.STORED_PERCENT_FULL
        return "$functionName($text, ${percentArg(left)}, ${percentArg(top)}, ${percentArg(right)}, ${percentArg(bottom)})"
    }

    private fun colorCondition(functionName: String, action: ActionStep): String {
        val color = luaString(action.conditionColorHex ?: "#000000")
        val tolerance = action.conditionColorTolerance ?: 10
        val x = action.conditionColorX ?: 0
        val y = action.conditionColorY ?: 0
        return "$functionName($color, $tolerance, ${percentArg(x)}, ${percentArg(y)})"
    }

    private fun percentArg(value: Int?): String {
        return ProgramCoordinateAdapter.formatStoredPercentArg(value ?: 0)
    }

    private fun luaString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        return "\"$escaped\""
    }

    private fun indentBlock(block: String, indent: String): String {
        return buildString {
            for (line in block.lines()) {
                if (line.isEmpty()) {
                    appendLine()
                } else {
                    appendLine("$indent$line")
                }
            }
        }
    }

    private fun parseName(lua: String): String {
        return lua.lineSequence()
            .firstOrNull { it.trimStart().startsWith("-- name:") }
            ?.substringAfter("-- name:")
            ?.trim()
            .orEmpty()
    }

    private fun stripCodecMetadata(lua: String): String {
        return lua.lineSequence()
            .filterNot { line ->
                val trimmed = line.trimStart()
                trimmed == HEADER ||
                    trimmed.startsWith("-- name:") ||
                    trimmed.startsWith("-- loopCount:") ||
                    trimmed.startsWith("-- loopGapMs:")
            }
            .joinToString("\n")
    }
}
