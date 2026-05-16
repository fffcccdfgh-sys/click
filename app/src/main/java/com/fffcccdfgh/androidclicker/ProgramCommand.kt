package com.fffcccdfgh.androidclicker

sealed class ProgramCommand {
    data class TapCmd(val x: Int, val y: Int, val durationMs: Long) : ProgramCommand()
    data class SwipeCmd(
        val startX: Int, val startY: Int,
        val endX: Int, val endY: Int,
        val durationMs: Long
    ) : ProgramCommand()
    data class WaitCmd(val ms: Long) : ProgramCommand()
    data class RepeatCmd(val count: Int, val commands: List<ProgramCommand>) : ProgramCommand()
    data class ConditionCmd(
        val conditionType: String,
        val conditionText: String?,
        val conditionLeft: Int?,
        val conditionTop: Int?,
        val conditionRight: Int?,
        val conditionBottom: Int?,
        val conditionColorHex: String?,
        val conditionColorTolerance: Int?,
        val conditionColorX: Int?,
        val conditionColorY: Int?
    ) : ProgramCommand()
}
