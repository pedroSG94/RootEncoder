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
import android.util.Range
import android.util.Size
import android.view.MotionEvent
import android.view.View
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.video.Camera1ApiManager
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.input.video.facedetector.FaceDetectorCallback

/**
 * Created by pedro on 11/1/24.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class Camera1Source(context: Context): VideoSource() {

  private val camera = Camera1ApiManager(null, context)
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
      camera.setSurfaceTexture(surfaceTexture)
      camera.start(facing, width, height, fps)
      camera.setPreviewOrientation(90) // necessary to use the same orientation than camera2
    }
  }

  override fun stop() {
    if (isRunning()) camera.stop()
  }

  override fun release() {}

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
      camera.previewSizeFront
    } else {
      camera.previewSizeBack
    }
    return mapCamera1Resolutions(resolutions, false)
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

  fun enableAutoFocus() {
    if (isRunning()) {
      camera.enableAutoFocus()
    }
  }

  fun disableAutoFocus() {
    if (isRunning()) camera.disableAutoFocus()
  }

  fun isAutoFocusEnabled(): Boolean {
    return if (isRunning()) camera.isAutoFocusEnabled else false
  }

  fun tapToFocus(view: View, event: MotionEvent) {
    camera.tapToFocus(view, event)
  }

  fun setZoom(event: MotionEvent) {
    if (isRunning()) camera.setZoom(event)
  }

  fun setZoom(level: Int) {
    if (isRunning()) camera.zoom = level
  }

  fun getZoomRange(): Range<Int> = Range(camera.minZoom, camera.maxZoom)

  fun getZoom(): Int = camera.zoom

  fun enableFaceDetection(callback: FaceDetectorCallback): Boolean {
    return if (isRunning()) camera.enableFaceDetection(callback) else false
  }

  fun disableFaceDetection() {
    if (isRunning()) camera.disableFaceDetection()
  }

  fun isFaceDetectionEnabled() = camera.isFaceDetectionEnabled

  fun openCameraId(id: Int) {
    camera.switchCamera(id)
  }

  fun enableVideoStabilization(): Boolean {
    return if (isRunning()) camera.enableVideoStabilization() else false
  }

  fun disableVideoStabilization() {
    if (isRunning()) camera.disableVideoStabilization()
  }

  fun isVideoStabilizationEnabled() = camera.isVideoStabilizationEnabled
}