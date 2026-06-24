package com.fffcccdfgh.androidclicker.feature.pvz

import android.app.Service
import com.fffcccdfgh.androidclicker.feature.pvz.floating.PvzFloatingControlService

object PvzScriptLaunchPolicy {
    val directRunServiceClass: Class<out Service> = PvzFloatingControlService::class.java
}
