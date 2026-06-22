package com.fffcccdfgh.androidclicker

import com.fffcccdfgh.androidclicker.core.ocr.OcrAreaMapper
import com.fffcccdfgh.androidclicker.core.ocr.OcrImageCropper
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrImageCropperTest {
    @Test
    fun packedRgbaCropUsesRowBulkCopyFastPath() {
        val source = file("app/src/main/java/com/fffcccdfgh/androidclicker/core/ocr/OcrImageCropper.kt")
            .readText()
        val packedFastPathFunction = source.substringAfter("private fun copyPackedRgbaRowsToBuffer")

        assertTrue(packedFastPathFunction.contains("packed.put(row)"))
        assertFalse(packedFastPathFunction.contains("for (x in 0 until area.width)"))
    }

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

    private fun file(path: String): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val root = if (File(cwd, "settings.gradle.kts").exists()) {
            cwd
        } else {
            cwd.parentFile!!
        }
        return File(root, path)
    }
}
