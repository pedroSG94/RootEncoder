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

package com.pedro.jcsample

import android.content.Context
import android.view.SurfaceView
import androidx.lifecycle.ViewModel
import com.pedro.common.ConnectChecker
import com.pedro.library.generic.GenericStream
import com.pedro.library.util.SensorRotationManager
import com.pedro.library.util.sources.video.Camera2Source

/**
 * Created by pedro on 26/1/24.
 */
class MainViewModel(context: Context): ViewModel() {

  private val connectChecker = object: ConnectChecker {
    override fun onConnectionStarted(url: String) {
      TODO("Not yet implemented")
    }

    override fun onConnectionSuccess() {
      TODO("Not yet implemented")
    }

    override fun onConnectionFailed(reason: String) {
      TODO("Not yet implemented")
    }

    override fun onNewBitrate(bitrate: Long) {
      TODO("Not yet implemented")
    }

    override fun onDisconnect() {
      TODO("Not yet implemented")
    }

    override fun onAuthError() {
      TODO("Not yet implemented")
    }

    override fun onAuthSuccess() {
      TODO("Not yet implemented")
    }
  }

  private val genericStream = GenericStream(context, connectChecker)

  private var initialized = false
  private var currentOrientation = -1
  private val sensorRotationManager = SensorRotationManager(context) {
    //0 = portrait, 90 = landscape, 180 = reverse portrait, 270 = reverse landscape
    if (currentOrientation != it) {
      genericStream.setOrientation(it)
      currentOrientation = it
    }
  }

  fun init() {
    if (!initialized) sensorRotationManager.start()
    initialized = true
  }

  fun startPreview(surfaceView: SurfaceView) {
    genericStream.prepareVideo(640, 480, 1_200_000)
    genericStream.prepareAudio(32000, true, 128_000)
    genericStream.startPreview(surfaceView)
  }

  fun stopPreview() {
    genericStream.stopPreview()
  }

  fun switchCamera() {
    (genericStream.videoSource as? Camera2Source)?.switchCamera()
  }

  fun release() {
    initialized = false
    sensorRotationManager.stop()
    if (genericStream.isRecording) genericStream.stopRecord()
    if (genericStream.isStreaming) genericStream.stopStream()
    if (genericStream.isOnPreview) genericStream.stopPreview()
  }
}