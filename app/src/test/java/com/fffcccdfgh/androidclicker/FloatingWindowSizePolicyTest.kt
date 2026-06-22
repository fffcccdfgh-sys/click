package com.fffcccdfgh.androidclicker

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingWindowSizePolicyTest {
    @Test
    fun expandedControlUsesLandscapeRatios() {
        val size = FloatingWindowSizePolicy.expandedControlSize(
            screenWidthPx = 2560,
            screenHeightPx = 1600
        )

        assertEquals(1280, size.widthPx)
        assertEquals(320, size.heightPx)
    }

    @Test
    fun expandedControlUsesPortraitRatios() {
        val size = FloatingWindowSizePolicy.expandedControlSize(
            screenWidthPx = 1600,
            screenHeightPx = 2560
        )

        assertEquals(1280, size.widthPx)
        assertEquals(384, size.heightPx)
    }

    @Test
    fun collapsedControlUsesLandscapeRatios() {
        val size = FloatingWindowSizePolicy.collapsedControlSize(
            screenWidthPx = 2560,
            screenHeightPx = 1600
        )

        assertEquals(1024, size.widthPx)
        assertEquals(240, size.heightPx)
    }

    @Test
    fun collapsedControlUsesPortraitRatios() {
        val size = FloatingWindowSizePolicy.collapsedControlSize(
            screenWidthPx = 1600,
            screenHeightPx = 2560
        )

        assertEquals(960, size.widthPx)
        assertEquals(205, size.heightPx)
    }

    @Test
    fun calibrationPanelUsesLandscapeRatios() {
        val size = FloatingWindowSizePolicy.calibrationPanelSize(
            screenWidthPx = 2560,
            screenHeightPx = 1600
        )

        assertEquals(896, size.widthPx)
        assertEquals(960, size.heightPx)
    }

    @Test
    fun calibrationPanelUsesPortraitRatios() {
        val size = FloatingWindowSizePolicy.calibrationPanelSize(
            screenWidthPx = 1600,
            screenHeightPx = 2560
        )

        assertEquals(800, size.widthPx)
        assertEquals(1536, size.heightPx)
    }

    @Test
    fun saveConfirmPanelUsesCurrentEstimatedContentSize() {
        val size = FloatingWindowSizePolicy.saveConfirmPanelEstimatedSize(density = 2f)

        assertEquals(520, size.widthPx)
        assertEquals(232, size.heightPx)
    }

    @Test
    fun centeredPositionKeepsSaveConfirmPanelCentered() {
        val position = FloatingWindowSizePolicy.centeredPosition(
            screenWidthPx = 1600,
            screenHeightPx = 2560,
            windowSize = FloatingWindowSize(widthPx = 520, heightPx = 232)
        )

        assertEquals(540, position.xPx)
        assertEquals(1164, position.yPx)
    }
}
