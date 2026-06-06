package com.fffcccdfgh.androidclicker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FloatingControlLayoutTest {
    @Test
    fun actionListHasScrollableContentAndDragBar() {
        val layout = File("src/main/res/layout/floating_action_list_panel.xml").readText()

        assertTrue(layout.contains("@+id/actionListPanelHeader"))
        assertTrue(layout.contains("@+id/actionListCloseButton"))
        assertTrue(layout.contains("@+id/actionListScroll"))
        assertTrue(layout.contains("@+id/actionListScrollBar"))
        assertTrue(layout.contains("com.fffcccdfgh.androidclicker.ProgramTemplateMenuScrollBar"))
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
}
