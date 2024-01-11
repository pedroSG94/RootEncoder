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
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.util.Size
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.video.Camera2ApiManager
import com.pedro.encoder.input.video.CameraHelper

/**
 * Created by pedro on 11/1/24.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class Camera2Source(context: Context): VideoSource {

  private val camera = Camera2ApiManager(context)
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
      camera.prepareCamera(surfaceTexture, width, height, fps, facing)
      camera.openCameraFacing(facing)
    }
  }

  override fun stop() {
    if (isRunning()) camera.closeCamera()
  }

  override fun isRunning(): Boolean = camera.isRunning

  private fun checkResolutionSupported(width: Int, height: Int): Boolean {
    if (width % 2 != 0 || height % 2 != 0) {
      throw IllegalArgumentException("width and height values must be divisible by 2")
    }
    val size = Size(width, height)
    val resolutions = if (facing == CameraHelper.Facing.BACK) {
      camera.cameraResolutionsBack
    } else camera.cameraResolutionsFront
    return if (camera.levelSupported == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
      //this is a wrapper of camera1 api. Only listed resolutions are supported
      resolutions.contains(size)
    } else {
      val widthList = resolutions.map { size.width }
      val heightList = resolutions.map { size.height }
      val maxWidth = widthList.maxOrNull() ?: 0
      val maxHeight = heightList.maxOrNull() ?: 0
      val minWidth = widthList.minOrNull() ?: 0
      val minHeight = heightList.minOrNull() ?: 0
      size.width in minWidth..maxWidth && size.height in minHeight..maxHeight
    }
  }
}