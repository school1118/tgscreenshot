package com.example.tgscreenshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ScreenshotHelper(
    private val ctx: Context,
    private val projection: MediaProjection,
    private val metrics: DisplayMetrics
) {
    @Volatile private var lastBitmap: Bitmap? = null

    fun capture(): Bitmap? {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!pm.isInteractive) return null

        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val dpi = metrics.densityDpi

        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        val vd = projection.createVirtualDisplay(
            "cap", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, null)

        val latch = CountDownLatch(1)
        reader.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = img.planes[0]
                val buffer: ByteBuffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * w
                val bmp = Bitmap.createBitmap(
                    w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(buffer)
                lastBitmap = Bitmap.createBitmap(bmp, 0, 0, w, h)
                bmp.recycle()
            } finally {
                img.close()
                latch.countDown()
            }
        }, null)

        latch.await(2, TimeUnit.SECONDS)
        reader.close()
        vd.release()
        return lastBitmap
    }
}
