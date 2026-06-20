package com.fffcccdfgh.androidclicker.feature.pvz.calibration

enum class PvzCalibrationAreaCorner {
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight
}

data class PvzCalibrationAreaBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

object PvzCalibrationAreaResizePolicy {
    private const val MIN_EDGE_GAP_PX = 0.5f

    fun resize(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        corner: PvzCalibrationAreaCorner,
        dx: Float,
        dy: Float,
        width: Float,
        height: Float
    ): PvzCalibrationAreaBounds {
        var newLeft = left
        var newTop = top
        var newRight = right
        var newBottom = bottom
        when (corner) {
            PvzCalibrationAreaCorner.TopLeft -> {
                newLeft = (left + dx).coerceIn(0f, right - MIN_EDGE_GAP_PX)
                newTop = (top + dy).coerceIn(0f, bottom - MIN_EDGE_GAP_PX)
            }
            PvzCalibrationAreaCorner.TopRight -> {
                newRight = (right + dx).coerceIn(left + MIN_EDGE_GAP_PX, width)
                newTop = (top + dy).coerceIn(0f, bottom - MIN_EDGE_GAP_PX)
            }
            PvzCalibrationAreaCorner.BottomLeft -> {
                newLeft = (left + dx).coerceIn(0f, right - MIN_EDGE_GAP_PX)
                newBottom = (bottom + dy).coerceIn(top + MIN_EDGE_GAP_PX, height)
            }
            PvzCalibrationAreaCorner.BottomRight -> {
                newRight = (right + dx).coerceIn(left + MIN_EDGE_GAP_PX, width)
                newBottom = (bottom + dy).coerceIn(top + MIN_EDGE_GAP_PX, height)
            }
        }
        return PvzCalibrationAreaBounds(newLeft, newTop, newRight, newBottom)
    }
}

object PvzCalibrationPointColorPreviewPolicy {
    fun shouldShowColorPreview(key: String): Boolean {
        return key == PvzCalibrationStorage.CARDS_POKER ||
            key == PvzCalibrationStorage.FINAL_WAVE_RED
    }
}
