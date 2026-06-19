package com.fffcccdfgh.androidclicker

import com.fffcccdfgh.androidclicker.core.program.ProgramEditorTextActions
import com.fffcccdfgh.androidclicker.core.program.ProgramEditorTextResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgramEditorTextActionsTest {
    @Test
    fun copiesSelectedTextRegardlessOfSelectionDirection() {
        val copied = ProgramEditorTextActions.selectedText("tap(1, 2)", 8, 4)

        assertEquals("1, 2", copied)
    }

    @Test
    fun returnsNullWhenCopySelectionIsEmpty() {
        val copied = ProgramEditorTextActions.selectedText("tap(1, 2)", 4, 4)

        assertNull(copied)
    }

    @Test
    fun cutsSelectedTextAndMovesCursorToSelectionStart() {
        val result = ProgramEditorTextActions.cutSelection("tap(1, 2)", 4, 8)

        assertEquals(ProgramEditorTextResult("tap()", 4), result)
    }

    @Test
    fun pastesOverSelectedText() {
        val result = ProgramEditorTextActions.pasteText("tap(1, 2)", 4, 8, "99")

        assertEquals(ProgramEditorTextResult("tap(99)", 6), result)
    }
}
