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
package com.pedro.encoder.input.audio

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log

/**
 * Created by pedro on 11/05/17.
 */
class AudioPostProcessEffect(private val microphoneId: Int) {
  private val TAG = "AudioPostProcessEffect"
  private var acousticEchoCanceler: AcousticEchoCanceler? = null
  private var automaticGainControl: AutomaticGainControl? = null
  private var noiseSuppressor: NoiseSuppressor? = null

  fun enableAutoGainControl() {
    if (AutomaticGainControl.isAvailable() && automaticGainControl == null) {
      automaticGainControl = AutomaticGainControl.create(microphoneId)
      automaticGainControl?.apply {
        enabled = true
        Log.i(TAG, "AutoGainControl enabled")
      } ?: run {
        Log.e(TAG, "This device doesn't implement AutoGainControl")
      }
    }
  }

  private fun releaseAutoGainControl() {
    automaticGainControl?.enabled = false
    automaticGainControl?.release()
    automaticGainControl = null
  }

  fun enableEchoCanceler() {
    if (AcousticEchoCanceler.isAvailable() && acousticEchoCanceler == null) {
      acousticEchoCanceler = AcousticEchoCanceler.create(microphoneId)
      acousticEchoCanceler?.apply {
        enabled = true
        Log.i(TAG, "EchoCanceler enabled")
      } ?: run {
        Log.e(TAG, "This device doesn't implement EchoCanceler")
      }
    }
  }

  private fun releaseEchoCanceler() {
    acousticEchoCanceler?.enabled = false
    acousticEchoCanceler?.release()
    acousticEchoCanceler = null
  }

  fun enableNoiseSuppressor() {
    if (NoiseSuppressor.isAvailable() && noiseSuppressor == null) {
      noiseSuppressor = NoiseSuppressor.create(microphoneId)
      noiseSuppressor?.apply {
        enabled = true
        Log.i(TAG, "NoiseSuppressor enabled")
      } ?: run {
        Log.e(TAG, "This device doesn't implement NoiseSuppressor")
      }
    }
  }

  private fun releaseNoiseSuppressor() {
    noiseSuppressor?.enabled = false
    noiseSuppressor?.release()
    noiseSuppressor = null
  }

  fun release() {
    releaseAutoGainControl()
    releaseEchoCanceler()
    releaseNoiseSuppressor()
  }
}