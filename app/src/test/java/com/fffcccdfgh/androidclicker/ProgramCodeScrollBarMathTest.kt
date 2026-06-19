package com.fffcccdfgh.androidclicker

import com.fffcccdfgh.androidclicker.core.program.ProgramCodeScrollBarMath
import com.fffcccdfgh.androidclicker.core.program.ProgramLineNumberMath
import com.fffcccdfgh.androidclicker.core.program.ProgramTemplateMenuLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgramCodeScrollBarMathTest {
    @Test
    fun calculatesThumbFromScrollPosition() {
        val thumb = ProgramCodeScrollBarMath.thumb(
            trackHeight = 300,
            contentHeight = 1000,
            viewportHeight = 250,
            scrollY = 375
        )

        assertEquals(75, thumb.height)
        assertEquals(112, thumb.top)
    }

    @Test
    fun mapsThumbDragToScrollPosition() {
        val scrollY = ProgramCodeScrollBarMath.scrollYForThumbTop(
            thumbTop = 112,
            trackHeight = 300,
            contentHeight = 1000,
            viewportHeight = 250
        )

        assertEquals(373, scrollY)
    }

    @Test
    fun clampsTemplateMenuHeightToVisibleRows() {
        val tallMenuHeight = ProgramTemplateMenuLayout.popupHeight(
            itemCount = 9,
            itemHeightPx = 44,
            verticalPaddingPx = 16,
            maxVisibleRows = 5
        )
        val shortMenuHeight = ProgramTemplateMenuLayout.popupHeight(
            itemCount = 3,
            itemHeightPx = 44,
            verticalPaddingPx = 16,
            maxVisibleRows = 5
        )

        assertEquals(236, tallMenuHeight)
        assertEquals(148, shortMenuHeight)
    }

    @Test
    fun countsProgramEditorLines() {
        assertEquals(1, ProgramLineNumberMath.lineCount(""))
        assertEquals(1, ProgramLineNumberMath.lineCount("tap(1, 2)"))
        assertEquals(3, ProgramLineNumberMath.lineCount("while true do\nwait(500)\nend"))
        assertEquals(2, ProgramLineNumberMath.lineCount("tap(1, 2)\n"))
    }
}
