/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.library.util.sources.video

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.annotation.RequiresApi
import com.pedro.library.util.sources.MediaProjectionHandler

/**
 * Created by pedro on 11/1/24.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class ScreenSource @JvmOverloads constructor(
  context: Context,
  mediaProjection: MediaProjection,
  mediaProjectionCallback: MediaProjection.Callback? = null,
  virtualDisplayCallback: VirtualDisplay.Callback? = null
): VideoSource() {

  private val TAG = "ScreenSource"
  private var virtualDisplay: VirtualDisplay? = null
  private var handlerThread = HandlerThread(TAG)
  private val mediaProjectionCallback = mediaProjectionCallback ?: object : MediaProjection.Callback() {}
  private val virtualDisplayCallback = virtualDisplayCallback ?: object : VirtualDisplay.Callback() {}
  private val dpi = context.resources.displayMetrics.densityDpi

  init {
      MediaProjectionHandler.mediaProjection = mediaProjection
  }

  override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
    return checkResolutionSupported(width, height)
  }

  override fun start(surfaceTexture: SurfaceTexture) {
    this.surfaceTexture = surfaceTexture
    if (!isRunning()) {
      val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
      //Adapt MediaProjection render to stream resolution
      val shouldRotate = rotation == 90 || rotation == 270
      val displayWidth = if (shouldRotate) height else width
      val displayHeight = if (shouldRotate) width else height
      if (shouldRotate) {
        surfaceTexture.setDefaultBufferSize(height, width)
      }
      handlerThread = HandlerThread(TAG)
      handlerThread.start()
      MediaProjectionHandler.mediaProjection?.registerCallback(mediaProjectionCallback, Handler(handlerThread.looper))
      virtualDisplay = MediaProjectionHandler.mediaProjection?.createVirtualDisplay(TAG,
        displayWidth, displayHeight, dpi, flags,
        Surface(surfaceTexture), virtualDisplayCallback, Handler(handlerThread.looper)
      )
      if (virtualDisplay == null) {
        throw IllegalArgumentException("Failed to create internal virtual display")
      }
    }
  }

  override fun stop() {
    if (isRunning()) {
      virtualDisplay?.release()
      virtualDisplay = null
      handlerThread.quitSafely()
    }
  }

  override fun release() {
    MediaProjectionHandler.mediaProjection?.unregisterCallback(mediaProjectionCallback)
  }

  override fun isRunning(): Boolean = virtualDisplay != null

  private fun checkResolutionSupported(width: Int, height: Int): Boolean {
    if (width % 2 != 0 || height % 2 != 0) {
      throw IllegalArgumentException("width and height values must be divisible by 2")
    }
    return true
  }

  fun resize(width: Int, height: Int) {
    virtualDisplay?.resize(width, height, dpi)
  }
}