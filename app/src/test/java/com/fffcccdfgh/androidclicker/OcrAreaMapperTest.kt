package com.fffcccdfgh.androidclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrAreaMapperTest {
    @Test
    fun mapsScreenAreaToScaledCaptureArea() {
        val mapped = OcrAreaMapper.mapScreenAreaToCaptureArea(
            left = 100,
            top = 50,
            right = 300,
            bottom = 250,
            screenWidth = 400,
            screenHeight = 300,
            captureWidth = 800,
            captureHeight = 600
        )

        assertEquals(OcrAreaMapper.Area(200, 100, 600, 500), mapped)
    }

    @Test
    fun clampsScreenAreaInsideCaptureBounds() {
        val mapped = OcrAreaMapper.mapScreenAreaToCaptureArea(
            left = -10,
            top = 20,
            right = 500,
            bottom = 320,
            screenWidth = 400,
            screenHeight = 300,
            captureWidth = 800,
            captureHeight = 600
        )

        assertEquals(OcrAreaMapper.Area(0, 40, 800, 600), mapped)
    }

    @Test
    fun roundsRightAndBottomOutwardToAvoidCroppingTextEdges() {
        val mapped = OcrAreaMapper.mapScreenAreaToCaptureArea(
            left = 1,
            top = 1,
            right = 2,
            bottom = 2,
            screenWidth = 3,
            screenHeight = 3,
            captureWidth = 10,
            captureHeight = 10
        )

        assertEquals(OcrAreaMapper.Area(3, 3, 7, 7), mapped)
    }

    @Test
    fun returnsNullForEmptyArea() {
        val mapped = OcrAreaMapper.mapScreenAreaToCaptureArea(
            left = 100,
            top = 50,
            right = 100,
            bottom = 120,
            screenWidth = 400,
            screenHeight = 300,
            captureWidth = 800,
            captureHeight = 600
        )

        assertNull(mapped)
    }
}
