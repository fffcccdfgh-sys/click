package com.fffcccdfgh.androidclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenCaptureGeometryTest {
    @Test
    fun mapsLandscapeTabletPointUsingCaptureDisplaySize() {
        val point = ScreenCapturePointMapper.mapScreenPointToCapturePoint(
            screenX = 1200,
            screenY = 1400,
            screenWidth = 2560,
            screenHeight = 1600,
            captureWidth = 2560,
            captureHeight = 1600
        )

        assertEquals(ScreenCapturePoint(1200, 1400), point)
    }

    @Test
    fun scalesPointWhenCaptureSurfaceUsesDifferentResolution() {
        val point = ScreenCapturePointMapper.mapScreenPointToCapturePoint(
            screenX = 1200,
            screenY = 800,
            screenWidth = 2400,
            screenHeight = 1600,
            captureWidth = 1200,
            captureHeight = 800
        )

        assertEquals(ScreenCapturePoint(600, 400), point)
    }

    @Test
    fun returnsNullWhenDimensionsAreInvalid() {
        val point = ScreenCapturePointMapper.mapScreenPointToCapturePoint(
            screenX = 100,
            screenY = 100,
            screenWidth = 0,
            screenHeight = 1600,
            captureWidth = 2560,
            captureHeight = 1600
        )

        assertNull(point)
    }
}
