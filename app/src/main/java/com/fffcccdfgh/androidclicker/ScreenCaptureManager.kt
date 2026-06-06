package com.fffcccdfgh.androidclicker

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log

object ScreenCaptureManager {
    private const val TAG = "ScreenCaptureMgr"
    private const val CAPTURE_POLL_INTERVAL_MS = 16L

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var captureWidth: Int = 0
    private var captureHeight: Int = 0
    private var captureDensityDpi: Int = 0
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    @Volatile
    var isReady: Boolean = false
        private set

    fun initialize(context: Context, resultCode: Int, data: Intent) {
        release()

        val displayInfo = ScreenCaptureDisplayReader.current(context)
        captureWidth = displayInfo.width
        captureHeight = displayInfo.height
        captureDensityDpi = displayInfo.densityDpi

        backgroundThread = HandlerThread("ScreenCapture").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)

        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = manager.getMediaProjection(resultCode, data)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection stopped by system")
                release()
            }
        }, backgroundHandler)

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            captureWidth, captureHeight, captureDensityDpi,
            0,  // No flags — use exact requested dimensions
            imageReader!!.surface, null, backgroundHandler
        )

        Log.d(TAG, "VirtualDisplay created: requested=${captureWidth}x${captureHeight} dpi=$captureDensityDpi")
        isReady = virtualDisplay != null
        Log.d(TAG, "Initialized: ${captureWidth}x${captureHeight} dpi=$captureDensityDpi ready=$isReady")
    }

    fun refreshDisplayMetrics(context: Context) {
        val projection = mediaProjection ?: return
        val handler = backgroundHandler ?: return
        val current = ScreenCaptureDisplayReader.current(context)
        if (!current.isValid) return
        if (
            current.width == captureWidth &&
            current.height == captureHeight &&
            current.densityDpi == captureDensityDpi &&
            imageReader != null &&
            virtualDisplay != null
        ) {
            return
        }

        Log.d(
            TAG,
            "Refreshing capture display: ${captureWidth}x${captureHeight}/$captureDensityDpi -> " +
                "${current.width}x${current.height}/${current.densityDpi}"
        )

        val newReader = ImageReader.newInstance(
            current.width,
            current.height,
            PixelFormat.RGBA_8888,
            2
        )
        try {
            val display = virtualDisplay
            if (display == null) {
                virtualDisplay = projection.createVirtualDisplay(
                    "ScreenCapture",
                    current.width,
                    current.height,
                    current.densityDpi,
                    0,  // No flags — use exact requested dimensions
                    newReader.surface,
                    null,
                    handler
                )
            } else {
                display.resize(current.width, current.height, current.densityDpi)
                display.setSurface(newReader.surface)
            }

            try {
                imageReader?.close()
            } catch (_: Exception) {}

            imageReader = newReader
            captureWidth = current.width
            captureHeight = current.height
            captureDensityDpi = current.densityDpi
            isReady = virtualDisplay != null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh capture display", e)
            try {
                newReader.close()
            } catch (_: Exception) {}
            isReady = false
        }
    }

    fun captureFrameSync(timeoutMs: Long = 2500L): Image? {
        val reader = imageReader ?: run {
            Log.d(TAG, "captureFrameSync: imageReader is null")
            return null
        }
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                val image = reader.acquireLatestImage()
                if (image != null) {
                    if (image.width != captureWidth || image.height != captureHeight) {
                        Log.w(TAG, "Frame resolution MISMATCH: image=${image.width}x${image.height} configured=${captureWidth}x${captureHeight} — AUTO_MIRROR may be the cause")
                    } else {
                        Log.d(TAG, "Frame acquired: image=${image.width}x${image.height} configured=${captureWidth}x${captureHeight}")
                    }
                    return image
                }
            } catch (e: IllegalStateException) {
                Log.d(TAG, "captureFrameSync: failed to acquire frame", e)
                return null
            } catch (e: UnsupportedOperationException) {
                Log.d(TAG, "captureFrameSync: unsupported image format", e)
                return null
            }
            try {
                Thread.sleep(CAPTURE_POLL_INTERVAL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
        }
        Log.d(TAG, "captureFrameSync: timed out waiting for frame")
        return null
    }

    fun readPixel(image: Image, x: Int, y: Int): Int {
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        val safeX = x.coerceIn(0, image.width - 1)
        val safeY = y.coerceIn(0, image.height - 1)
        val offset = safeY * rowStride + safeX * pixelStride
        buffer.position(offset)

        val r = buffer.get().toInt() and 0xFF
        val g = buffer.get().toInt() and 0xFF
        val b = buffer.get().toInt() and 0xFF
        val a = if (pixelStride >= 4) buffer.get().toInt() and 0xFF else 0xFF

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height

        buffer.rewind()

        val srcWidth = rowStride / pixelStride
        val srcBitmap = Bitmap.createBitmap(srcWidth, height, Bitmap.Config.ARGB_8888)
        srcBitmap.copyPixelsFromBuffer(buffer)

        return if (srcWidth == width) {
            srcBitmap
        } else {
            val cropped = Bitmap.createBitmap(srcBitmap, 0, 0, width, height)
            srcBitmap.recycle()
            cropped
        }
    }

    fun getCaptureWidth(): Int = captureWidth
    fun getCaptureHeight(): Int = captureHeight
    fun getCaptureDensityDpi(): Int = captureDensityDpi

    fun release() {
        isReady = false
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        virtualDisplay = null
        try {
            imageReader?.close()
        } catch (_: Exception) {}
        imageReader = null
        captureWidth = 0
        captureHeight = 0
        captureDensityDpi = 0
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null
        try {
            backgroundThread?.quitSafely()
        } catch (_: Exception) {}
        backgroundThread = null
        backgroundHandler = null
        Log.d(TAG, "Released")
    }
}
