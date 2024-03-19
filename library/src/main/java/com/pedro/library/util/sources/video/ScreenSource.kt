/*
 * Copyright (C) 2023 pedroSG94.
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

/**
 * Created by pedro on 11/1/24.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class ScreenSource @JvmOverloads constructor(
  context: Context,
  private val mediaProjection: MediaProjection,
  mediaProjectionCallback: MediaProjection.Callback? = null,
  virtualDisplayCallback: VirtualDisplay.Callback? = null
): VideoSource() {

  private var virtualDisplay: VirtualDisplay? = null
  private val handlerThread = HandlerThread("ScreenSource")
  private val mediaProjectionCallback = mediaProjectionCallback ?: object : MediaProjection.Callback() {}
  private val virtualDisplayCallback = virtualDisplayCallback ?: object : VirtualDisplay.Callback() {}
  private val dpi = context.resources.displayMetrics.densityDpi

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
      handlerThread.start()
      mediaProjection.registerCallback(mediaProjectionCallback, Handler(handlerThread.looper))
      virtualDisplay = mediaProjection.createVirtualDisplay("ScreenSource",
        displayWidth, displayHeight, dpi, flags,
        Surface(surfaceTexture), virtualDisplayCallback, Handler(handlerThread.looper)
      )
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
    mediaProjection.unregisterCallback(mediaProjectionCallback)
    mediaProjection.stop()
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