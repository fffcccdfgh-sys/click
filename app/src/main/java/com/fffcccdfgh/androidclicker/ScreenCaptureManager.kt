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
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.nio.ByteOrder

object ScreenCaptureManager {
    private const val TAG = "ScreenCaptureMgr"
    private const val CAPTURE_POLL_INTERVAL_MS = 16L

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var captureWidth: Int = 0
    private var captureHeight: Int = 0
    private var coordinateWidth: Int = 0
    private var coordinateHeight: Int = 0
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    @Volatile
    var isReady: Boolean = false
        private set

    fun initialize(context: Context, resultCode: Int, data: Intent) {
        release()

        val dm = context.resources.displayMetrics
        coordinateWidth = dm.widthPixels
        coordinateHeight = dm.heightPixels

        val realMetrics = getRealDisplayMetrics(context, dm)
        captureWidth = realMetrics.widthPixels.coerceAtLeast(coordinateWidth)
        captureHeight = realMetrics.heightPixels.coerceAtLeast(coordinateHeight)
        val densityDpi = realMetrics.densityDpi.takeIf { it > 0 } ?: dm.densityDpi

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
            captureWidth, captureHeight, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, backgroundHandler
        )

        isReady = virtualDisplay != null
        Log.d(
            TAG,
            "Initialized capture=${captureWidth}x${captureHeight} coordinate=${coordinateWidth}x${coordinateHeight} ready=$isReady"
        )
    }

    private fun getRealDisplayMetrics(context: Context, fallback: DisplayMetrics): DisplayMetrics {
        val out = DisplayMetrics().apply { setTo(fallback) }
        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.maximumWindowMetrics.bounds
                out.widthPixels = bounds.width()
                out.heightPixels = bounds.height()
            } else {
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(out)
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "getRealDisplayMetrics failed; using resource display metrics", e)
            out
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
                    Log.d(TAG, "captureFrameSync: acquired frame ${image.width}x${image.height}")
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
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        val safeX = x.coerceIn(0, image.width - 1)
        val safeY = y.coerceIn(0, image.height - 1)
        val offset = safeY * rowStride + safeX * pixelStride

        if (pixelStride >= 4 && offset + 4 <= buffer.limit()) {
            return try {
                val intBuffer = buffer.duplicate().order(ByteOrder.nativeOrder())
                intBuffer.position(offset)
                intBuffer.int
            } catch (_: Exception) {
                readPixelRgbaBytes(image, safeX, safeY)
            }
        }

        return readPixelRgbaBytes(image, safeX, safeY)
    }

    private fun readPixelRgbaBytes(image: Image, x: Int, y: Int): Int {
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        val offset = y * rowStride + x * pixelStride
        buffer.position(offset)

        val r = buffer.get().toInt() and 0xFF
        val g = if (pixelStride >= 2) buffer.get().toInt() and 0xFF else r
        val b = if (pixelStride >= 3) buffer.get().toInt() and 0xFF else g
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

    fun getCaptureWidth(): Int = if (coordinateWidth > 0) coordinateWidth else captureWidth
    fun getCaptureHeight(): Int = if (coordinateHeight > 0) coordinateHeight else captureHeight

    fun getImageWidth(): Int = captureWidth
    fun getImageHeight(): Int = captureHeight

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
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null
        try {
            backgroundThread?.quitSafely()
        } catch (_: Exception) {}
        backgroundThread = null
        backgroundHandler = null
        captureWidth = 0
        captureHeight = 0
        coordinateWidth = 0
        coordinateHeight = 0
        Log.d(TAG, "Released")
    }
}
