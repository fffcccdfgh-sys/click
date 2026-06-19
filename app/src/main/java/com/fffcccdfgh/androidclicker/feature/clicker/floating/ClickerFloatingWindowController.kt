package com.fffcccdfgh.androidclicker.feature.clicker.floating

class ClickerFloatingWindowController(
    private val host: FloatingControlService
) {
    fun showFloatingControl() = host.showFloatingControlImpl()

    fun hideFloatingControl() = host.hideFloatingControlImpl()

    fun updateFloatingControlSizeForCurrentDisplay() =
        host.updateFloatingControlSizeForCurrentDisplayImpl()

    fun setFloatingTouchThrough(enabled: Boolean) = host.setFloatingTouchThroughImpl(enabled)
}
