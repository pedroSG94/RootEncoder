package com.pedro.library.util.sources

import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.view.Surface
import androidx.annotation.RequiresApi

/**
 * Class to avoid stop media projection if it is in use by other source
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class MediaProjectionHandler {
    var mediaProjection: MediaProjection? = null
    private var running = false
    private var instancesRunning = 0

    fun createVirtualDisplay(
        name: String, width: Int, height: Int, dpi: Int, flags: Int,
        surface: Surface?, callback: VirtualDisplay.Callback?, handler: Handler?
    ): VirtualDisplay? {
        return mediaProjection?.createVirtualDisplay(name, width, height, dpi, flags, surface, callback, handler)
    }

    fun start() {
        running = true
        instancesRunning++
    }

    fun stop() {
        if (!running) return
        instancesRunning--
        if (instancesRunning <= 0) {
            running = false
            mediaProjection?.stop()
        }
    }

    fun running() = running

    fun registerCallback(callback: MediaProjection.Callback, handler: Handler?) {
        mediaProjection?.registerCallback(callback, handler)
    }

    fun unregisterCallback(callback: MediaProjection.Callback) {
        mediaProjection?.unregisterCallback(callback)
    }
}