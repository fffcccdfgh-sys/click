package com.fffcccdfgh.androidclicker

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FloatingControlLayoutTest {
    @Test
    fun actionListHasScrollableContentAndDragBar() {
        val layout = File("src/main/res/layout/floating_control.xml").readText()

        assertTrue(layout.contains("@+id/actionListScroll"))
        assertTrue(layout.contains("@+id/actionListScrollBar"))
        assertTrue(layout.contains("com.fffcccdfgh.androidclicker.ProgramTemplateMenuScrollBar"))
    }
}
