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

            if (trimmed.isEmpty()) { i++; continue }
            if (trimmed.startsWith("//")) { i++; continue }

            if (trimmed == "}") {
                if (!untilBrace) {
                    throw ParseException("第${lineNum}行：多余的 '}'", lineNum)
                }
                return BlockResult(commands, i + 1)
            }

            when {
                trimmed.startsWith("tap(") -> {
                    commands.add(parseTap(trimmed, lineNum))
                }
                trimmed.startsWith("swipe(") -> {
                    commands.add(parseSwipe(trimmed, lineNum))
                }
                trimmed.startsWith("wait(") -> {
                    commands.add(parseWait(trimmed, lineNum))
                }
                trimmed.startsWith("cond(") -> {
                    commands.add(parseCond(trimmed, lineNum))
                }
                trimmed.startsWith("repeat(") && trimmed.endsWith("{") -> {
                    val count = parseRepeatHeader(trimmed, lineNum)
                    val bodyLines = lines.drop(i + 1)
                    val bodyResult = parseBlock(bodyLines, lineNum, untilBrace = true)
                    commands.add(ProgramCommand.RepeatCmd(count, bodyResult.commands))
                    i += 1 + bodyResult.consumed
                }
                else -> throw ParseException("第${lineNum}行：无法识别的命令", lineNum)
            }
            i++
        }
        if (untilBrace) {
            throw ParseException("第${startLineNum}行：repeat 块缺少 '}'", startLineNum)
        }
        return BlockResult(commands, i)
    }

    private fun parseTap(line: String, lineNum: Int): ProgramCommand.TapCmd {
        val args = extractArgs(line, "tap", 3, lineNum)
        val x = args[0].toIntOrNull() ?: throw ParseException("第${lineNum}行：tap x 必须为整数", lineNum)
        val y = args[1].toIntOrNull() ?: throw ParseException("第${lineNum}行：tap y 必须为整数", lineNum)
        val dur = args[2].toLongOrNull() ?: throw ParseException("第${lineNum}行：tap durationMs 必须为整数", lineNum)
        if (dur < 1) throw ParseException("第${lineNum}行：durationMs 必须 >= 1", lineNum)
        return ProgramCommand.TapCmd(x, y, dur)
    }

    private fun parseSwipe(line: String, lineNum: Int): ProgramCommand.SwipeCmd {
        val args = extractArgs(line, "swipe", 5, lineNum)
        val sx = args[0].toIntOrNull() ?: throw ParseException("第${lineNum}行：swipe startX 必须为整数", lineNum)
        val sy = args[1].toIntOrNull() ?: throw ParseException("第${lineNum}行：swipe startY 必须为整数", lineNum)
        val ex = args[2].toIntOrNull() ?: throw ParseException("第${lineNum}行：swipe endX 必须为整数", lineNum)
        val ey = args[3].toIntOrNull() ?: throw ParseException("第${lineNum}行：swipe endY 必须为整数", lineNum)
        val dur = args[4].toLongOrNull() ?: throw ParseException("第${lineNum}行：swipe durationMs 必须为整数", lineNum)
        if (dur < 1) throw ParseException("第${lineNum}行：durationMs 必须 >= 1", lineNum)
        return ProgramCommand.SwipeCmd(sx, sy, ex, ey, dur)
    }

    private fun parseWait(line: String, lineNum: Int): ProgramCommand.WaitCmd {
        val args = extractArgs(line, "wait", 1, lineNum)
        val ms = args[0].toLongOrNull() ?: throw ParseException("第${lineNum}行：wait ms 必须为整数", lineNum)
        if (ms < 1) throw ParseException("第${lineNum}行：ms 必须 >= 1", lineNum)
        return ProgramCommand.WaitCmd(ms)
    }

    private fun parseRepeatHeader(line: String, lineNum: Int): Int {
        val inner = line.substringAfter("repeat(").substringBefore(")").trim()
        val count = inner.toIntOrNull() ?: throw ParseException("第${lineNum}行：repeat count 必须为整数", lineNum)
        if (count < 0) throw ParseException("第${lineNum}行：repeat count 必须 >= 0", lineNum)
        return count
    }

    private fun parseCond(line: String, lineNum: Int): ProgramCommand.ConditionCmd {
        val start = line.indexOf('(')
        val end = line.lastIndexOf(')')
        if (start == -1 || end == -1 || start >= end) {
            throw ParseException("第${lineNum}行：cond 参数格式错误", lineNum)
        }
        val inner = line.substring(start + 1, end)
        val parts = inner.split(",").map { it.trim().removeSurrounding("\"") }

        val condType = parts.getOrNull(0)
            ?: throw ParseException("第${lineNum}行：cond 缺少条件类型", lineNum)

        return when {
            condType == "text_contains" || condType == "text_not_contains" -> {
                if (parts.size != 6) throw ParseException(
                    "第${lineNum}行：$condType 需要 6 个参数 (类型, 文字, left, top, right, bottom), 实际 ${parts.size}", lineNum)
                val text = parts[1].ifEmpty { throw ParseException("第${lineNum}行：$condType 匹配文字不能为空", lineNum) }
                val l = parts[2].toIntOrNull() ?: throw ParseException("第${lineNum}行：$condType left 必须为整数", lineNum)
                val t = parts[3].toIntOrNull() ?: throw ParseException("第${lineNum}行：$condType top 必须为整数", lineNum)
                val r = parts[4].toIntOrNull() ?: throw ParseException("第${lineNum}行：$condType right 必须为整数", lineNum)
                val b = parts[5].toIntOrNull() ?: throw ParseException("第${lineNum}行：$condType bottom 必须为整数", lineNum)
                ProgramCommand.ConditionCmd(condType, text, l, t, r, b, null, null, null, null)
            }
            condType == "color_match" || condType == "color_not_match" -> {
                if (parts.size != 5) throw ParseException(
                    "第${lineNum}行：$condType 需要 5 个参数 (类型, 颜色值, 容差%, x, y), 实际 ${parts.size}", lineNum)
                val hex = parts[1].ifEmpty { throw ParseException("第${lineNum}行：$condType 颜色值不能为空", lineNum) }
                val tol = parts[2].toIntOrNull() ?: throw ParseException("第${lineNum}行：$condType 容差必须为整数", lineNum)
                val x = parts[3].toIntOrNull() ?: throw ParseException("第${lineNum}行：$condType x 必须为整数", lineNum)
                val y = parts[4].toIntOrNull() ?: throw ParseException("第${lineNum}行：$condType y 必须为整数", lineNum)
                ProgramCommand.ConditionCmd(condType, null, null, null, null, null, hex, tol, x, y)
            }
            else -> throw ParseException("第${lineNum}行：未知的条件类型 \"$condType\"", lineNum)
        }
    }

    private fun extractArgs(line: String, cmdName: String, expectedCount: Int, lineNum: Int): List<String> {
        val start = line.indexOf('(')
        val end = line.lastIndexOf(')')
        if (start == -1 || end == -1 || start >= end) {
            throw ParseException("第${lineNum}行：$cmdName 参数格式错误", lineNum)
        }
        val inner = line.substring(start + 1, end)
        val parts = inner.split(",").map { it.trim() }
        if (parts.size != expectedCount) {
            throw ParseException("第${lineNum}行：$cmdName 需要 $expectedCount 个参数，实际 ${parts.size} 个", lineNum)
        }
        return parts
    }
}
