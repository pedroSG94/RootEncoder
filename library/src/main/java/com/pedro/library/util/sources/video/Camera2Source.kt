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
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.util.Range
import android.util.Size
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.video.Camera2ApiManager
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.input.video.facedetector.FaceDetectorCallback

/**
 * Created by pedro on 11/1/24.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class Camera2Source(context: Context): VideoSource() {

  private val camera = Camera2ApiManager(context)
  private var facing = CameraHelper.Facing.BACK

  override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
    val result = checkResolutionSupported(width, height)
    if (!result) {
      throw IllegalArgumentException("Unsupported resolution: ${width}x$height")
    }
    return true
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

  override fun release() {}

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

  fun switchCamera() {
    facing = if (facing == CameraHelper.Facing.BACK) {
      CameraHelper.Facing.FRONT
    } else {
      CameraHelper.Facing.BACK
    }
    if (isRunning()) {
      stop()
      surfaceTexture?.let {
        start(it)
      }
    }
  }

  fun getCameraFacing(): CameraHelper.Facing = facing

  fun getCameraResolutions(facing: CameraHelper.Facing): List<Size> {
    val resolutions = if (facing == CameraHelper.Facing.FRONT) {
      camera.cameraResolutionsFront
    } else {
      camera.cameraResolutionsBack
    }
    return resolutions.toList()
  }

  fun setExposure(level: Int) {
    if (isRunning()) camera.exposure = level
  }

  fun getExposure(): Int {
    return if (isRunning()) camera.exposure else 0
  }

  fun enableLantern() {
    if (isRunning()) camera.enableLantern()
  }

  fun disableLantern() {
    if (isRunning()) camera.disableLantern()
  }

  fun isLanternEnabled(): Boolean {
    return if (isRunning()) camera.isLanternEnabled else false
  }

  fun enableAutoFocus(): Boolean {
    if (isRunning()) return camera.enableAutoFocus()
    return false
  }

  fun disableAutoFocus(): Boolean {
    if (isRunning()) return camera.disableAutoFocus()
    return false
  }

  fun isAutoFocusEnabled(): Boolean {
    return if (isRunning()) camera.isAutoFocusEnabled else false
  }

  fun tapToFocus(event: MotionEvent): Boolean {
    return camera.tapToFocus(event)
  }

  fun setZoom(event: MotionEvent) {
    if (isRunning()) camera.setZoom(event)
  }

  fun setZoom(level: Float) {
    if (isRunning()) camera.zoom = level
  }

  fun getZoomRange(): Range<Float> = camera.zoomRange

  fun getZoom(): Float = camera.zoom

  fun enableFaceDetection(callback: FaceDetectorCallback): Boolean {
    return if (isRunning()) camera.enableFaceDetection(callback) else false
  }

  fun disableFaceDetection() {
    if (isRunning()) camera.disableFaceDetection()
  }

  fun isFaceDetectionEnabled() = camera.isFaceDetectionEnabled()

  fun camerasAvailable(): Array<String> = camera.camerasAvailable

  fun getCurrentCameraId() = camera.getCurrentCameraId()

  fun openCameraId(id: String) {
    if (isRunning()) stop()
    camera.openCameraId(id)
  }

  fun enableOpticalVideoStabilization(): Boolean {
    return if (isRunning()) camera.enableOpticalVideoStabilization() else false
  }

  fun disableOpticalVideoStabilization() {
    if (isRunning()) camera.disableOpticalVideoStabilization()
  }

  fun isOpticalVideoStabilizationEnabled() = camera.isOpticalStabilizationEnabled

  fun enableVideoStabilization(): Boolean {
    return if (isRunning()) camera.enableVideoStabilization() else false
  }

  fun disableVideoStabilization() {
    if (isRunning()) camera.disableVideoStabilization()
  }

  fun isVideoStabilizationEnabled() = camera.isVideoStabilizationEnabled
}