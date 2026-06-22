package com.fffcccdfgh.androidclicker.core.program

import com.fffcccdfgh.androidclicker.core.accessibility.ClickAccessibilityService
import org.luaj.vm2.Globals

fun interface ProgramLuaGlobalRegistrar {
    fun register(service: ClickAccessibilityService, globals: Globals)
}
