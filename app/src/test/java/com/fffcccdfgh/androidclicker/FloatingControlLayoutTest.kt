package com.fffcccdfgh.androidclicker

import com.fffcccdfgh.androidclicker.core.overlay.FloatingPanelController
import com.fffcccdfgh.androidclicker.core.program.ProgramTemplateMenuScrollBar
import com.fffcccdfgh.androidclicker.feature.clicker.floating.FloatingControlService
import com.fffcccdfgh.androidclicker.feature.pvz.floating.PvzFloatingControlService
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingControlLayoutTest {
    @Test
    fun actionListHasScrollableContentAndDragBar() {
        val layout = File("src/main/res/layout/floating_action_list_panel.xml").readText()

        assertTrue(layout.contains("@+id/actionListPanelHeader"))
        assertTrue(layout.contains("@+id/actionListCloseButton"))
        assertTrue(layout.contains("@+id/actionListScroll"))
        assertTrue(layout.contains("@+id/actionListScrollBar"))
        assertTrue(layout.contains("com.fffcccdfgh.androidclicker.core.program.ProgramTemplateMenuScrollBar"))
    }

    @Test
    fun conditionPickerDoesNotExposeOcrFilters() {
        val layout = File("src/main/res/layout/condition_picker.xml").readText()

        assertFalse(layout.contains("@+id/conditionOcrFilterRow"))
        assertFalse(layout.contains("@+id/condOcrFilterDropdown"))
    }

    @Test
    fun areaPickerActionButtonsAreVisibleByDefault() {
        val layout = File("src/main/res/layout/area_picker.xml").readText()

        val buttonsIndex = layout.indexOf("@+id/areaPickerButtons")
        val buttonsEndIndex = layout.indexOf("</LinearLayout>", buttonsIndex)
        val buttonsBlock = layout.substring(buttonsIndex, buttonsEndIndex)

        assertTrue(buttonsIndex >= 0)
        assertFalse(buttonsBlock.contains("android:visibility=\"gone\""))
    }

    @Test
    fun saveConfirmPanelUsesIndependentOverlayWindow() {
        val layout = File("src/main/res/layout/floating_save_confirm_panel.xml").readText()
        val service = File(
            "src/main/java/com/fffcccdfgh/androidclicker/feature/clicker/floating/FloatingControlService.kt"
        ).readText()
        val pvzService = File(
            "src/main/java/com/fffcccdfgh/androidclicker/feature/pvz/floating/PvzFloatingControlService.kt"
        ).readText()
        val pvzProgramEditorController = File(
            "src/main/java/com/fffcccdfgh/androidclicker/feature/pvz/floating/PvzProgramEditorController.kt"
        ).readText()

        assertTrue(layout.contains("@+id/savePanelRoot"))
        assertTrue(layout.contains("@+id/savePanelNameInput"))
        assertTrue(layout.contains("@+id/savePanelConfirmButton"))
        assertTrue(layout.contains("@+id/savePanelCancelButton"))

        assertTrue(service.contains("saveConfirmPanelView"))
        assertTrue(service.contains("floatingPanelController.show"))
        assertTrue(service.contains("removePanel(saveConfirmPanelView, \"floating_save_confirm_panel\")"))
        assertTrue(pvzService.contains("showSavePanel = { programEditorController.showSaveConfirmPanel() }"))
        assertTrue(pvzProgramEditorController.contains("saveConfirmPanelView"))
        assertTrue(pvzProgramEditorController.contains("showPanel("))
        assertTrue(pvzProgramEditorController.contains("removePanel(saveConfirmPanelView, SAVE_CONFIRM_ZONE_KEY)"))
    }

}
