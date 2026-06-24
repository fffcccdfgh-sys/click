package com.fffcccdfgh.androidclicker.feature.pvz.floating

import com.fffcccdfgh.androidclicker.core.program.ProgramLuaGlobalRegistrar
import com.fffcccdfgh.androidclicker.feature.clicker.floating.RunFloatingControlService
import com.fffcccdfgh.androidclicker.feature.pvz.calibration.PvzCalibrationLuaBindings

object PvzRunExecutionPolicy {
    val extraLuaGlobalRegistrars: List<ProgramLuaGlobalRegistrar> = listOf(PvzCalibrationLuaBindings)
}

class PvzRunFloatingControlService : RunFloatingControlService() {
    override val serviceDebugName = "PvzRunFloatingControlService"
    override val notificationChannelId = CHANNEL_ID
    override val notificationId = NOTIFICATION_ID
    override val stopExecutionAction = ACTION_STOP_EXECUTION
    override val executionStopButtonZoneKey = "pvz_run_execution_stop_button"
    override val windowZonePrefix = "pvz_run_"
    override val extraLuaGlobalRegistrars = PvzRunExecutionPolicy.extraLuaGlobalRegistrars

    companion object {
        const val CHANNEL_ID = "pvz_run_floating_control_channel"
        const val NOTIFICATION_ID = 5
        const val ACTION_STOP_EXECUTION = "com.fffcccdfgh.androidclicker.PVZ_STOP_EXECUTION"
    }
}
