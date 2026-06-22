package com.fffcccdfgh.androidclicker.feature.clicker.floating

class ClickerPickerCoordinator(
    private val host: FloatingControlService
) {
    fun hidePickerOverlay() = host.hidePickerOverlayImpl()

    fun hidePositionMarkers() = host.hidePositionMarkersImpl()
}
