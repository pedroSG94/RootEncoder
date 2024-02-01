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
import android.os.Environment
import android.view.SurfaceView
import androidx.lifecycle.ViewModel
import com.pedro.common.ConnectChecker
import com.pedro.library.base.recording.RecordController
import com.pedro.library.generic.GenericStream
import com.pedro.library.util.SensorRotationManager
import com.pedro.library.util.sources.video.Camera2Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Created by pedro on 26/1/24.
 */
class MainViewModel(context: Context): ViewModel() {

  private val connectChecker = object: ConnectChecker {
    override fun onConnectionStarted(url: String) {
      _mainState.update {
        it.copy(streaming = true)
      }
    }

    override fun onConnectionSuccess() {
      _mainState.update {
        it.copy(streaming = true)
      }
    }

    override fun onConnectionFailed(reason: String) {
      _mainState.update {
        it.copy(streaming = false)
      }
    }

    override fun onNewBitrate(bitrate: Long) {

    }

    override fun onDisconnect() {
      _mainState.update {
        it.copy(streaming = false)
      }
    }

    override fun onAuthError() {
      _mainState.update {
        it.copy(streaming = false)
      }
    }

    override fun onAuthSuccess() {

    }
  }

  private val recordListener = RecordController.Listener { status ->
    if (status == null) return@Listener
    when (status) {
      RecordController.Status.STARTED,
      RecordController.Status.RECORDING,
      RecordController.Status.RESUMED -> {
        _mainState.update {
          it.copy(recording = true)
        }
      }
      RecordController.Status.STOPPED,
      RecordController.Status.PAUSED -> {
        _mainState.update {
          it.copy(recording = false)
        }
      }
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

  private val _mainState = MutableStateFlow(MainState(recording = false, streaming = false, url = ""))
  val mainState = _mainState.asStateFlow()

  fun init() {
    if (!initialized) sensorRotationManager.start()
    initialized = true
  }

  fun prepare(): Boolean {
    if (genericStream.isOnPreview || genericStream.isRecording || genericStream.isStreaming) return true
    return genericStream.prepareVideo(640, 480, 1_200_000) && genericStream.prepareAudio(32000, true, 128_000)
  }

  fun startPreview(surfaceView: SurfaceView) {
    genericStream.startPreview(surfaceView)
  }

  fun stopPreview() {
    genericStream.stopPreview()
  }

  fun onStreamClick() {
    if (genericStream.isStreaming) {
      genericStream.stopStream()
    } else {
      genericStream.startStream(_mainState.value.url.trim())
    }
  }

  fun onRecordClick() {
    if (genericStream.isRecording) {
      genericStream.stopRecord()
    } else {
      val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
      val currentDateAndTime = sdf.format(Date())
      genericStream.startRecord("${getRecordPath()}/${currentDateAndTime}.mp4", recordListener)
    }
  }

  fun switchCamera() {
    (genericStream.videoSource as? Camera2Source)?.switchCamera()
  }

  fun updateText(url: String) {
    _mainState.update {
      it.copy(url = url)
    }
  }

  fun release() {
    initialized = false
    sensorRotationManager.stop()
    if (genericStream.isRecording) genericStream.stopRecord()
    if (genericStream.isStreaming) genericStream.stopStream()
    if (genericStream.isOnPreview) genericStream.stopPreview()
  }

  private fun getRecordPath(): String {
    val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
    return File(storageDir.absolutePath + "/RootEncoder").absolutePath
  }
}

data class MainState(
  val recording: Boolean,
  val streaming: Boolean,
  val url: String
)