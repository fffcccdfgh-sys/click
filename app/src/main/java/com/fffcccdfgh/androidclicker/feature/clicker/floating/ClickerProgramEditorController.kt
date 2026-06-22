package com.fffcccdfgh.androidclicker.feature.clicker.floating

class ClickerProgramEditorController(
    private val host: FloatingControlService
) {
    fun updateProgramEditorSizeForCurrentDisplay() =
        host.updateProgramEditorSizeForCurrentDisplayImpl()

    fun updateProgramTemplatePanelSizeForCurrentDisplay() =
        host.updateProgramTemplatePanelSizeForCurrentDisplayImpl()
}
