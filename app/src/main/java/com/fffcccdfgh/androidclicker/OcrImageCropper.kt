package com.fffcccdfgh.androidclicker

import android.graphics.Bitmap
import android.media.Image
import java.nio.ByteBuffer

object OcrImageCropper {
    fun cropImageAreaToBitmap(image: Image, area: OcrAreaMapper.Area): Bitmap? {
        if (area.width <= 0 || area.height <= 0) return null
        val plane = image.planes.firstOrNull() ?: return null
        val cropBuffer = copyRgbaCropToPackedBuffer(
            buffer = plane.buffer,
            rowStride = plane.rowStride,
            pixelStride = plane.pixelStride,
            area = area
        )
        return Bitmap.createBitmap(area.width, area.height, Bitmap.Config.ARGB_8888).apply {
            copyPixelsFromBuffer(cropBuffer)
        }
    }

    fun copyRgbaCropToPackedBuffer(
        buffer: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        area: OcrAreaMapper.Area
    ): ByteBuffer {
        require(rowStride > 0) { "rowStride must be positive" }
        require(pixelStride >= 3) { "pixelStride must contain RGB channels" }
        require(area.width > 0 && area.height > 0) { "area must not be empty" }

        val source = buffer.duplicate()
        val packed = ByteBuffer.allocate(area.width * area.height * 4)
        for (y in 0 until area.height) {
            val sourceY = area.top + y
            val rowOffset = sourceY * rowStride
            for (x in 0 until area.width) {
                val sourceX = area.left + x
                val offset = rowOffset + sourceX * pixelStride
                packed.put(source.get(offset))
                packed.put(source.get(offset + 1))
                packed.put(source.get(offset + 2))
                packed.put(if (pixelStride >= 4) source.get(offset + 3) else 0xFF.toByte())
            }
        }
        packed.flip()
        return packed
    }

    fun copyRgbaCropToArgbPixels(
        buffer: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        area: OcrAreaMapper.Area
    ): IntArray {
        require(rowStride > 0) { "rowStride must be positive" }
        require(pixelStride >= 3) { "pixelStride must contain RGB channels" }
        require(area.width > 0 && area.height > 0) { "area must not be empty" }

        val source = buffer.duplicate()
        val pixels = IntArray(area.width * area.height)
        for (y in 0 until area.height) {
            val sourceY = area.top + y
            val rowOffset = sourceY * rowStride
            for (x in 0 until area.width) {
                val sourceX = area.left + x
                val offset = rowOffset + sourceX * pixelStride
                val red = source.get(offset).toInt() and 0xFF
                val green = source.get(offset + 1).toInt() and 0xFF
                val blue = source.get(offset + 2).toInt() and 0xFF
                val alpha = if (pixelStride >= 4) {
                    source.get(offset + 3).toInt() and 0xFF
                } else {
                    0xFF
                }
                pixels[y * area.width + x] =
                    (alpha shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        return pixels
    }
}
