package com.fffcccdfgh.androidclicker

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class OcrImageCropperTest {
    @Test
    fun copiesRequestedAreaToPackedRgbaBuffer() {
        val sourceWidth = 4
        val sourceHeight = 3
        val pixelStride = 4
        val rowStride = 20
        val buffer = ByteBuffer.allocate(rowStride * sourceHeight)

        for (y in 0 until sourceHeight) {
            for (x in 0 until sourceWidth) {
                val offset = y * rowStride + x * pixelStride
                buffer.put(offset, (10 + x).toByte())
                buffer.put(offset + 1, (20 + y).toByte())
                buffer.put(offset + 2, (30 + x + y).toByte())
                buffer.put(offset + 3, (40 + x + y).toByte())
            }
        }

        val cropBuffer = OcrImageCropper.copyRgbaCropToPackedBuffer(
            buffer = buffer,
            rowStride = rowStride,
            pixelStride = pixelStride,
            area = OcrAreaMapper.Area(left = 1, top = 1, right = 3, bottom = 3)
        )
        val cropBytes = ByteArray(cropBuffer.remaining())
        cropBuffer.get(cropBytes)

        assertArrayEquals(
            byteArrayOf(
                11, 21, 32, 42,
                12, 21, 33, 43,
                11, 22, 33, 43,
                12, 22, 34, 44
            ),
            cropBytes
        )
    }

    @Test
    fun copiesOnlyRequestedAreaFromPaddedRgbaBuffer() {
        val sourceWidth = 4
        val sourceHeight = 3
        val pixelStride = 4
        val rowStride = 20
        val buffer = ByteBuffer.allocate(rowStride * sourceHeight)

        for (y in 0 until sourceHeight) {
            for (x in 0 until sourceWidth) {
                val offset = y * rowStride + x * pixelStride
                buffer.put(offset, (10 + x).toByte())
                buffer.put(offset + 1, (20 + y).toByte())
                buffer.put(offset + 2, (30 + x + y).toByte())
                buffer.put(offset + 3, 0xFF.toByte())
            }
        }

        val pixels = OcrImageCropper.copyRgbaCropToArgbPixels(
            buffer = buffer,
            rowStride = rowStride,
            pixelStride = pixelStride,
            area = OcrAreaMapper.Area(left = 1, top = 1, right = 4, bottom = 3)
        )

        assertArrayEquals(
            intArrayOf(
                argb(11, 21, 32),
                argb(12, 21, 33),
                argb(13, 21, 34),
                argb(11, 22, 33),
                argb(12, 22, 34),
                argb(13, 22, 35)
            ),
            pixels
        )
    }

    private fun argb(red: Int, green: Int, blue: Int): Int {
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
