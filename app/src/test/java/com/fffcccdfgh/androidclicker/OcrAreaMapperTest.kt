package com.fffcccdfgh.androidclicker

import com.fffcccdfgh.androidclicker.core.ocr.OcrAreaMapper
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

    @Test
    fun mapsPhoneLandscapeAreaWhenDisplayMetricsAreStillPortrait() {
        val mapped = OcrAreaMapper.mapScreenAreaToCaptureAreaAllowingRotation(
            left = 2800,
            top = 200,
            right = 3180,
            bottom = 520,
            screenWidth = 1440,
            screenHeight = 3200,
            captureWidth = 3200,
            captureHeight = 1440
        )

        assertEquals(OcrAreaMapper.Area(2800, 200, 3180, 520), mapped)
    }

    @Test
    fun keepsLandscapeCaptureCoordinatesWhenRotatedAreaAlsoFitsPortraitWidth() {
        val mapped = OcrAreaMapper.mapScreenAreaToCaptureAreaAllowingRotation(
            left = 1000,
            top = 200,
            right = 1300,
            bottom = 520,
            screenWidth = 1440,
            screenHeight = 3200,
            captureWidth = 3200,
            captureHeight = 1440
        )

        assertEquals(OcrAreaMapper.Area(1000, 200, 1300, 520), mapped)
    }

    @Test
    fun mapsFullPortraitScreenAreaToFullLandscapeCaptureWhenDimensionsAreSwapped() {
        val mapped = OcrAreaMapper.mapScreenAreaToCaptureAreaAllowingRotation(
            left = 0,
            top = 0,
            right = 1440,
            bottom = 3200,
            screenWidth = 1440,
            screenHeight = 3200,
            captureWidth = 3200,
            captureHeight = 1440
        )

        assertEquals(OcrAreaMapper.Area(0, 0, 3200, 1440), mapped)
    }

    @Test
    fun mapsPortraitPercentAreaToLandscapeCaptureWhenDimensionsAreSwapped() {
        val mapped = OcrAreaMapper.mapScreenAreaToCaptureAreaAllowingRotation(
            left = 360,
            top = 800,
            right = 720,
            bottom = 1600,
            screenWidth = 1440,
            screenHeight = 3200,
            captureWidth = 3200,
            captureHeight = 1440
        )

        assertEquals(OcrAreaMapper.Area(800, 360, 1600, 720), mapped)
    }

    @Test
    fun keepsNormalScalingWhenDisplayAndCaptureHaveSameOrientation() {
        val mapped = OcrAreaMapper.mapScreenAreaToCaptureAreaAllowingRotation(
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
}
