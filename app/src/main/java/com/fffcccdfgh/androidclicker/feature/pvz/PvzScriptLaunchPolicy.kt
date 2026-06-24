package com.fffcccdfgh.androidclicker.feature.pvz

import android.app.Service
import com.fffcccdfgh.androidclicker.feature.pvz.floating.PvzFloatingControlService
import com.fffcccdfgh.androidclicker.feature.pvz.floating.PvzRunFloatingControlService

object PvzScriptLaunchPolicy {
    val directRunServiceClass: Class<out Service> = PvzRunFloatingControlService::class.java
    val editorServiceClass: Class<out Service> = PvzFloatingControlService::class.java
}
