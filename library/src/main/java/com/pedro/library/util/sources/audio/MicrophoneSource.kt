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

package com.pedro.library.util.sources.audio

import android.media.AudioDeviceInfo
import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.encoder.Frame
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.audio.MicrophoneManager

/**
 * Created by pedro on 12/1/24.
 */
class MicrophoneSource(
  var audioSource: Int = MediaRecorder.AudioSource.DEFAULT,
): AudioSource(), GetMicrophoneData {

  private val microphone = MicrophoneManager(this)
  private var preferredDevice: AudioDeviceInfo? = null

  override fun create(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean): Boolean {
    //create microphone to confirm valid parameters
    val result = microphone.createMicrophone(audioSource, sampleRate, isStereo, echoCanceler, noiseSuppressor)
    if (!result) {
      throw IllegalArgumentException("Some parameters specified are not valid");
    }
    return true
  }

  @RequiresApi(api = Build.VERSION_CODES.M)
  fun setPreferredDevice(deviceInfo: AudioDeviceInfo?): Boolean {
    preferredDevice = deviceInfo
    return microphone.setPreferredDevice(deviceInfo)
  }

  override fun start(getMicrophoneData: GetMicrophoneData) {
    this.getMicrophoneData = getMicrophoneData
    if (!isRunning()) {
      val result = microphone.createMicrophone(audioSource, sampleRate, isStereo, echoCanceler, noiseSuppressor)
      if (!result) {
        throw IllegalArgumentException("Failed to create microphone audio source")
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        microphone.setPreferredDevice(preferredDevice)
      }
      microphone.start()
    }
  }

  override fun stop() {
    if (isRunning()) {
      this.getMicrophoneData = null
      microphone.stop()
    }
  }

  override fun isRunning(): Boolean = microphone.isRunning

  override fun release() {}

  override fun getMaxInputSize(): Int = microphone.maxInputSize

  override fun setMaxInputSize(size: Int) {
    microphone.maxInputSize = size
  }

  override fun inputPCMData(frame: Frame) {
    getMicrophoneData?.inputPCMData(frame)
  }

  fun mute() {
    microphone.mute()
  }

  fun unMute() {
    microphone.unMute()
  }

  fun isMuted(): Boolean = microphone.isMuted
}