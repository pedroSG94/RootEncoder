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

@file:Suppress("DEPRECATION")

package com.pedro.encoder.input.sources.video

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Build
import android.util.Range
import android.util.Size
import android.view.MotionEvent
import android.view.View
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.video.Camera1ApiManager
import com.pedro.encoder.input.video.CameraCallbacks
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
    val shouldRotate = width < height
    val w = if (shouldRotate) height else width
    val h = if (shouldRotate) width else height
    val size = Size(w, h)
    val resolutions = if (facing == CameraHelper.Facing.BACK) {
      camera.previewSizeBack
    } else camera.previewSizeFront
    return resolutions.map { Size(it.width, it.height) }.find { it.width == size.width && it.height == size.height } != null
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
    return resolutions.map { Size(it.width, it.height) }
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

  fun tapToFocus(view: View, event: MotionEvent): Boolean {
    return camera.tapToFocus(view, event)
  }

  @JvmOverloads
  fun setZoom(event: MotionEvent, delta: Int = 1) {
    if (isRunning()) camera.setZoom(event, delta)
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

  fun setCameraCallback(callbacks: CameraCallbacks?) {
    camera.setCameraCallbacks(callbacks)
  }

  /**
   * @param mode values from Camera.Parameters.WHITE_BALANCE_*
   */
  fun enableAutoWhiteBalance(mode: String) = camera.enableAutoWhiteBalance(mode)

  fun getAutoWhiteBalanceModesAvailable() = camera.autoWhiteBalanceModesAvailable

  fun getWhiteBalance() = camera.whiteBalance
}