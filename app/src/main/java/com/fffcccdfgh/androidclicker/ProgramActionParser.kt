package com.fffcccdfgh.androidclicker

object ProgramActionParser {

    class ParseException(message: String, val line: Int) : Exception(message)

    fun parse(code: String): Result<List<ProgramCommand>> {
        return try {
            val lines = code.lines()
            val blockResult = parseBlock(lines, 0)
            Result.success(blockResult.commands)
        } catch (e: ParseException) {
            Result.failure(e)
        }
    }

    private data class BlockResult(val commands: List<ProgramCommand>, val consumed: Int)

    private fun parseBlock(lines: List<String>, startLineNum: Int, untilBrace: Boolean = false): BlockResult {
        val commands = mutableListOf<ProgramCommand>()
        var i = 0
        while (i < lines.size) {
            val lineNum = startLineNum + i + 1
            val trimmed = lines[i].trim()

            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("--")) {
                i++
                continue
            }

            if (trimmed == "}") {
                if (!untilBrace) throw ParseException("Line $lineNum: extra '}'", lineNum)
                return BlockResult(commands, i + 1)
            }

            when {
                trimmed.startsWith("tap(") -> commands.add(parseTap(trimmed, lineNum))
                trimmed.startsWith("swipe(") -> commands.add(parseSwipe(trimmed, lineNum))
                trimmed.startsWith("wait(") -> commands.add(parseWait(trimmed, lineNum))
                trimmed.startsWith("cond(") -> commands.add(parseCond(trimmed, lineNum))
                trimmed.startsWith("repeat(") && trimmed.endsWith("{") -> {
                    val count = parseRepeatHeader(trimmed, lineNum)
                    val bodyResult = parseBlock(lines.drop(i + 1), lineNum, untilBrace = true)
                    commands.add(ProgramCommand.RepeatCmd(count, bodyResult.commands))
                    i += 1 + bodyResult.consumed
                }
                else -> throw ParseException("Line $lineNum: unknown command", lineNum)
            }
            i++
        }
        if (untilBrace) throw ParseException("Line $startLineNum: repeat block missing '}'", startLineNum)
        return BlockResult(commands, i)
    }

    private fun parseTap(line: String, lineNum: Int): ProgramCommand.TapCmd {
        val args = extractArgs(line, "tap", lineNum, 2, 3)
        val x = parsePercentCoordinate(args[0], "tap x", lineNum)
        val y = parsePercentCoordinate(args[1], "tap y", lineNum)
        val dur = if (args.size >= 3) parsePositiveLong(args[2], "tap durationMs", lineNum) else 1L
        return ProgramCommand.TapCmd(x, y, dur)
    }

    private fun parseSwipe(line: String, lineNum: Int): ProgramCommand.SwipeCmd {
        val args = extractArgs(line, "swipe", lineNum, 4, 5)
        val sx = parsePercentCoordinate(args[0], "swipe startX", lineNum)
        val sy = parsePercentCoordinate(args[1], "swipe startY", lineNum)
        val ex = parsePercentCoordinate(args[2], "swipe endX", lineNum)
        val ey = parsePercentCoordinate(args[3], "swipe endY", lineNum)
        val dur = if (args.size >= 5) parsePositiveLong(args[4], "swipe durationMs", lineNum) else 300L
        return ProgramCommand.SwipeCmd(sx, sy, ex, ey, dur)
    }

    private fun parseWait(line: String, lineNum: Int): ProgramCommand.WaitCmd {
        val args = extractArgs(line, "wait", lineNum, 1, 1)
        return ProgramCommand.WaitCmd(parsePositiveLong(args[0], "wait ms", lineNum))
    }

    private fun parseRepeatHeader(line: String, lineNum: Int): Int {
        val inner = line.substringAfter("repeat(").substringBefore(")").trim()
        val count = inner.toIntOrNull() ?: throw ParseException("Line $lineNum: repeat count must be an integer", lineNum)
        if (count < 0) throw ParseException("Line $lineNum: repeat count must be >= 0", lineNum)
        return count
    }

    private fun parseCond(line: String, lineNum: Int): ProgramCommand.ConditionCmd {
        val parts = extractArgs(line, "cond", lineNum, 5, 6).map { it.removeSurrounding("\"") }
        val condType = parts[0]

        return when (condType) {
            "text_contains", "text_not_contains" -> {
                if (parts.size != 6) {
                    throw ParseException("Line $lineNum: $condType needs 6 arguments", lineNum)
                }
                val text = parts[1].ifEmpty {
                    throw ParseException("Line $lineNum: $condType text cannot be empty", lineNum)
                }
                ProgramCommand.ConditionCmd(
                    conditionType = condType,
                    conditionText = text,
                    conditionLeft = parsePercentCoordinate(parts[2], "$condType left", lineNum),
                    conditionTop = parsePercentCoordinate(parts[3], "$condType top", lineNum),
                    conditionRight = parsePercentCoordinate(parts[4], "$condType right", lineNum),
                    conditionBottom = parsePercentCoordinate(parts[5], "$condType bottom", lineNum),
                    conditionColorHex = null,
                    conditionColorTolerance = null,
                    conditionColorX = null,
                    conditionColorY = null
                )
            }
            "color_match", "color_not_match" -> {
                if (parts.size != 5) {
                    throw ParseException("Line $lineNum: $condType needs 5 arguments", lineNum)
                }
                val hex = parts[1].ifEmpty {
                    throw ParseException("Line $lineNum: $condType color cannot be empty", lineNum)
                }
                ProgramCommand.ConditionCmd(
                    conditionType = condType,
                    conditionText = null,
                    conditionLeft = null,
                    conditionTop = null,
                    conditionRight = null,
                    conditionBottom = null,
                    conditionColorHex = hex,
                    conditionColorTolerance = parseInt(parts[2], "$condType tolerance", lineNum),
                    conditionColorX = parsePercentCoordinate(parts[3], "$condType x", lineNum),
                    conditionColorY = parsePercentCoordinate(parts[4], "$condType y", lineNum)
                )
            }
            else -> throw ParseException("Line $lineNum: unknown condition type '$condType'", lineNum)
        }
    }

    private fun extractArgs(
        line: String,
        cmdName: String,
        lineNum: Int,
        minCount: Int,
        maxCount: Int
    ): List<String> {
        val start = line.indexOf('(')
        val end = line.lastIndexOf(')')
        if (start == -1 || end == -1 || start >= end) {
            throw ParseException("Line $lineNum: $cmdName arguments are malformed", lineNum)
        }
        val parts = line.substring(start + 1, end).split(",").map { it.trim() }
        if (parts.size !in minCount..maxCount) {
            val expected = if (minCount == maxCount) "$minCount" else "$minCount-$maxCount"
            throw ParseException("Line $lineNum: $cmdName needs $expected arguments, got ${parts.size}", lineNum)
        }
        return parts
    }

    private fun parsePercentCoordinate(value: String, label: String, lineNum: Int): Int {
        return ProgramCoordinateAdapter.parseStoredPercentArg(value)
            ?: throw ParseException("Line $lineNum: $label must be a percent number", lineNum)
    }

    private fun parsePositiveLong(value: String, label: String, lineNum: Int): Long {
        val parsed = value.toLongOrNull() ?: throw ParseException("Line $lineNum: $label must be an integer", lineNum)
        if (parsed < 1) throw ParseException("Line $lineNum: $label must be >= 1", lineNum)
        return parsed
    }

    private fun parseInt(value: String, label: String, lineNum: Int): Int {
        return value.toIntOrNull() ?: throw ParseException("Line $lineNum: $label must be an integer", lineNum)
    }
}
