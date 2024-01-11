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
import android.hardware.Camera
import android.os.Build
import android.util.Size
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.video.Camera1ApiManager
import com.pedro.encoder.input.video.CameraHelper

/**
 * Created by pedro on 11/1/24.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class Camera1Source(context: Context): VideoSource {

  private val camera = Camera1ApiManager(null, context)
  private var surfaceTexture: SurfaceTexture? = null
  private var facing = CameraHelper.Facing.BACK
  private var width = 0
  private var height = 0
  private var fps = 0

  override fun create(width: Int, height: Int, fps: Int): Boolean {
    this.width = width
    this.height = height
    this.fps = fps
    return checkResolutionSupported(width, height)
  }

  override fun start(surfaceTexture: SurfaceTexture) {
    this.surfaceTexture = surfaceTexture
    if (!isRunning()) {
      surfaceTexture.setDefaultBufferSize(width, height)
      camera.setSurfaceTexture(surfaceTexture)
      camera.start(facing, width, height, fps)
      camera.setPreviewOrientation(90) // necessary to use the same orientation than camera2
    }
  }

  override fun stop() {
    if (isRunning()) camera.stop()
  }

  override fun isRunning(): Boolean = camera.isRunning

  private fun checkResolutionSupported(width: Int, height: Int): Boolean {
    if (width % 2 != 0 || height % 2 != 0) {
      throw IllegalArgumentException("width and height values must be divisible by 2")
    }
    val shouldRotate = width > height
    val w = if (shouldRotate) height else width
    val h = if (shouldRotate) width else height
    val size = Size(w, h)
    val resolutions = if (facing == CameraHelper.Facing.BACK) {
      camera.previewSizeBack
    } else camera.previewSizeFront
    return mapCamera1Resolutions(resolutions, shouldRotate).find { it.width == size.width && it.height == size.height } != null
  }

  @Suppress("DEPRECATION")
  private fun mapCamera1Resolutions(resolutions: List<Camera.Size>, shouldRotate: Boolean) = resolutions.map {
    if (shouldRotate) Size(it.height, it.width) else Size(it.width, it.height)
  }
}