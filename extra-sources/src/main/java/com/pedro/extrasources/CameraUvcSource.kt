/*
 *
 *  * Copyright (C) 2024 pedroSG94.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.pedro.extrasources

import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.view.Surface
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.pedro.encoder.input.sources.OrientationForced
import com.pedro.encoder.input.sources.video.VideoSource
import com.pedro.encoder.input.video.Camera2ResolutionCalculator
import com.serenegiant.usb.Size
import kotlin.math.abs


/**
 * Created by pedro on 10/9/24.
 */
class CameraUvcSource: VideoSource() {

  private var cameraHelper: ICameraHelper? = null
  private var running = false
  private var surface: Surface? = null

  override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
    return true
  }

  override fun start(surfaceTexture: SurfaceTexture) {
    this.surfaceTexture = surfaceTexture
    surface = Surface(surfaceTexture)
    cameraHelper = CameraHelper()
    cameraHelper?.setStateCallback(stateCallback)
    running = true
  }

  override fun stop() {
    surface?.let { cameraHelper?.removeSurface(it) }
    surface?.release()
    surface = null
    cameraHelper?.release()
    cameraHelper = null
    running = false
  }

  override fun release() {
  }

  override fun isRunning(): Boolean = running

  override fun getOrientationConfig(): OrientationForced = OrientationForced.LANDSCAPE

  private val stateCallback: ICameraHelper.StateCallback = object : ICameraHelper.StateCallback {
    override fun onAttach(device: UsbDevice) {
      cameraHelper?.selectDevice(device)
    }

    override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
      cameraHelper?.openCamera()
    }

    override fun onCameraOpen(device: UsbDevice) {
      cameraHelper?.startPreview()
      surface?.let { cameraHelper?.addSurface(it, false) }
    }

    override fun onCameraClose(device: UsbDevice) {

    }

    override fun onDeviceClose(device: UsbDevice) {}

    override fun onDetach(device: UsbDevice) {}

    override fun onCancel(device: UsbDevice) {}
  }
}