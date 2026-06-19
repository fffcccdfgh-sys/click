package com.fffcccdfgh.androidclicker.core.accessibility

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityServiceStatus {
    fun isEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ClickAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { component ->
                component.packageName == expected.packageName &&
                    component.className == expected.className
            }
    }

    fun connectedService(): ClickAccessibilityService? {
        return ClickAccessibilityService.instance?.takeIf { ClickAccessibilityService.isRunning }
    }
}
