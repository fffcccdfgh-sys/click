package com.fffcccdfgh.androidclicker.core.ocr

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

        if (pixelStride == 4) {
            return copyPackedRgbaRowsToBuffer(
                buffer = buffer,
                rowStride = rowStride,
                area = area
            )
        }

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

    private fun copyPackedRgbaRowsToBuffer(
        buffer: ByteBuffer,
        rowStride: Int,
        area: OcrAreaMapper.Area
    ): ByteBuffer {
        val packed = ByteBuffer.allocate(area.width * area.height * 4)
        val rowBytes = area.width * 4
        val source = buffer.duplicate()
        for (y in 0 until area.height) {
            val rowStart = (area.top + y) * rowStride + area.left * 4
            val row = source.duplicate()
            row.position(rowStart)
            row.limit(rowStart + rowBytes)
            packed.put(row)
        }
        packed.flip()
        return packed
    }

}
