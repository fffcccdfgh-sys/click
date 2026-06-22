package com.fffcccdfgh.androidclicker

import kotlin.math.roundToInt

data class FloatingWindowSize(
    val widthPx: Int,
    val heightPx: Int
)

data class FloatingWindowPosition(
    val xPx: Int,
    val yPx: Int
)

object FloatingWindowSizePolicy {
    fun expandedControlSize(screenWidthPx: Int, screenHeightPx: Int): FloatingWindowSize =
        percentSize(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            landscapeWidthRatio = EXPANDED_CONTROL_LANDSCAPE_WIDTH_RATIO,
            landscapeHeightRatio = EXPANDED_CONTROL_LANDSCAPE_HEIGHT_RATIO,
            portraitWidthRatio = EXPANDED_CONTROL_PORTRAIT_WIDTH_RATIO,
            portraitHeightRatio = EXPANDED_CONTROL_PORTRAIT_HEIGHT_RATIO
        )

    fun collapsedControlSize(screenWidthPx: Int, screenHeightPx: Int): FloatingWindowSize =
        percentSize(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            landscapeWidthRatio = COLLAPSED_CONTROL_LANDSCAPE_WIDTH_RATIO,
            landscapeHeightRatio = COLLAPSED_CONTROL_LANDSCAPE_HEIGHT_RATIO,
            portraitWidthRatio = COLLAPSED_CONTROL_PORTRAIT_WIDTH_RATIO,
            portraitHeightRatio = COLLAPSED_CONTROL_PORTRAIT_HEIGHT_RATIO
        )

    fun calibrationPanelSize(screenWidthPx: Int, screenHeightPx: Int): FloatingWindowSize =
        percentSize(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            landscapeWidthRatio = CALIBRATION_PANEL_LANDSCAPE_WIDTH_RATIO,
            landscapeHeightRatio = CALIBRATION_PANEL_HEIGHT_RATIO,
            portraitWidthRatio = CALIBRATION_PANEL_PORTRAIT_WIDTH_RATIO,
            portraitHeightRatio = CALIBRATION_PANEL_HEIGHT_RATIO
        )

    fun programEditorSize(screenWidthPx: Int, screenHeightPx: Int, density: Float): FloatingWindowSize {
        val safeWidth = screenWidthPx.coerceAtLeast(1)
        val safeHeight = screenHeightPx.coerceAtLeast(1)
        val marginPx = dp(PROGRAM_EDITOR_SCREEN_MARGIN_DP, density)
        val availableWidth = (safeWidth - marginPx * 2).coerceAtLeast(1)
        val availableHeight = (safeHeight - marginPx * 2).coerceAtLeast(1)
        val landscape = safeWidth > safeHeight
        val widthRatio = if (landscape) PROGRAM_EDITOR_LANDSCAPE_WIDTH_RATIO else PROGRAM_EDITOR_PORTRAIT_WIDTH_RATIO
        val heightRatio = if (landscape) PROGRAM_EDITOR_LANDSCAPE_HEIGHT_RATIO else PROGRAM_EDITOR_PORTRAIT_HEIGHT_RATIO

        return FloatingWindowSize(
            widthPx = (safeWidth * widthRatio).roundToInt().coerceAtMost(availableWidth),
            heightPx = (safeHeight * heightRatio).roundToInt().coerceAtMost(availableHeight)
        )
    }

    fun programEditorSizeForDisplay(
        displayWidthPx: Int,
        displayHeightPx: Int,
        resourceWidthPx: Int,
        resourceHeightPx: Int,
        density: Float
    ): FloatingWindowSize {
        val width = displayWidthPx.takeIf { it > 0 } ?: resourceWidthPx
        val height = displayHeightPx.takeIf { it > 0 } ?: resourceHeightPx
        return programEditorSize(width, height, density)
    }

    fun programEditorCodePanelHeight(editorSize: FloatingWindowSize): Int =
        (editorSize.heightPx.coerceAtLeast(1) * PROGRAM_EDITOR_CODE_PANEL_HEIGHT_RATIO).roundToInt()

    fun programTemplateSize(screenWidthPx: Int, screenHeightPx: Int, density: Float): FloatingWindowSize {
        val editorSize = programEditorSize(screenWidthPx, screenHeightPx, density)
        val safeWidth = screenWidthPx.coerceAtLeast(1)
        val safeHeight = screenHeightPx.coerceAtLeast(1)
        val landscape = safeWidth > safeHeight

        return FloatingWindowSize(
            widthPx = (editorSize.widthPx * PROGRAM_TEMPLATE_WIDTH_RATIO_OF_EDITOR).roundToInt(),
            heightPx = if (landscape) {
                editorSize.heightPx
            } else {
                (editorSize.heightPx * PROGRAM_TEMPLATE_PORTRAIT_HEIGHT_RATIO_OF_EDITOR).roundToInt()
            }
        )
    }

    fun saveConfirmPanelEstimatedSize(density: Float): FloatingWindowSize =
        FloatingWindowSize(
            widthPx = dp(SAVE_CONFIRM_PANEL_ESTIMATED_WIDTH_DP, density),
            heightPx = dp(SAVE_CONFIRM_PANEL_ESTIMATED_HEIGHT_DP, density)
        )

    fun centeredPosition(
        screenWidthPx: Int,
        screenHeightPx: Int,
        windowSize: FloatingWindowSize
    ): FloatingWindowPosition {
        val safeWidth = screenWidthPx.coerceAtLeast(1)
        val safeHeight = screenHeightPx.coerceAtLeast(1)

        return FloatingWindowPosition(
            xPx = ((safeWidth - windowSize.widthPx) / 2).coerceAtLeast(0),
            yPx = ((safeHeight - windowSize.heightPx) / 2).coerceAtLeast(0)
        )
    }

    private fun percentSize(
        screenWidthPx: Int,
        screenHeightPx: Int,
        landscapeWidthRatio: Float,
        landscapeHeightRatio: Float,
        portraitWidthRatio: Float,
        portraitHeightRatio: Float
    ): FloatingWindowSize {
        val safeWidth = screenWidthPx.coerceAtLeast(1)
        val safeHeight = screenHeightPx.coerceAtLeast(1)
        val landscape = safeWidth > safeHeight
        val widthRatio = if (landscape) landscapeWidthRatio else portraitWidthRatio
        val heightRatio = if (landscape) landscapeHeightRatio else portraitHeightRatio

        return FloatingWindowSize(
            widthPx = (safeWidth * widthRatio).roundToInt().coerceAtLeast(1),
            heightPx = (safeHeight * heightRatio).roundToInt().coerceAtLeast(1)
        )
    }

    private fun dp(value: Float, density: Float): Int {
        val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        return (value * safeDensity).roundToInt()
    }

    private const val EXPANDED_CONTROL_LANDSCAPE_WIDTH_RATIO = 0.50f
    private const val EXPANDED_CONTROL_LANDSCAPE_HEIGHT_RATIO = 0.20f
    private const val EXPANDED_CONTROL_PORTRAIT_WIDTH_RATIO = 0.80f
    private const val EXPANDED_CONTROL_PORTRAIT_HEIGHT_RATIO = 0.15f
    private const val COLLAPSED_CONTROL_LANDSCAPE_WIDTH_RATIO = 0.40f
    private const val COLLAPSED_CONTROL_LANDSCAPE_HEIGHT_RATIO = 0.15f
    private const val COLLAPSED_CONTROL_PORTRAIT_WIDTH_RATIO = 0.60f
    private const val COLLAPSED_CONTROL_PORTRAIT_HEIGHT_RATIO = 0.08f
    private const val CALIBRATION_PANEL_LANDSCAPE_WIDTH_RATIO = 0.35f
    private const val CALIBRATION_PANEL_PORTRAIT_WIDTH_RATIO = 0.50f
    private const val CALIBRATION_PANEL_HEIGHT_RATIO = 0.60f
    private const val PROGRAM_EDITOR_SCREEN_MARGIN_DP = 16f
    private const val PROGRAM_EDITOR_LANDSCAPE_WIDTH_RATIO = 0.60f
    private const val PROGRAM_EDITOR_LANDSCAPE_HEIGHT_RATIO = 0.80f
    private const val PROGRAM_EDITOR_PORTRAIT_WIDTH_RATIO = 0.80f
    private const val PROGRAM_EDITOR_PORTRAIT_HEIGHT_RATIO = 0.60f
    private const val PROGRAM_EDITOR_CODE_PANEL_HEIGHT_RATIO = 0.40f
    private const val PROGRAM_TEMPLATE_WIDTH_RATIO_OF_EDITOR = 0.50f
    private const val PROGRAM_TEMPLATE_PORTRAIT_HEIGHT_RATIO_OF_EDITOR = 2f / 3f
    private const val SAVE_CONFIRM_PANEL_ESTIMATED_WIDTH_DP = 260f
    private const val SAVE_CONFIRM_PANEL_ESTIMATED_HEIGHT_DP = 116f
}
