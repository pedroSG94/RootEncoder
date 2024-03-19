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

package com.pedro.library.util.sources.audio

import android.media.AudioAttributes
import android.media.AudioPlaybackCaptureConfiguration
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.RequiresApi
import com.pedro.encoder.Frame
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.audio.MicrophoneManager

/**
 * Created by pedro on 12/1/24.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class InternalSource(private val mediaProjection: MediaProjection): AudioSource(), GetMicrophoneData {

  private val microphone = MicrophoneManager(this)
  private val handlerThread = HandlerThread("InternalSource")

  override fun create(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean): Boolean {
    //create microphone to confirm valid parameters
    val result = microphone.createMicrophone(sampleRate, isStereo, echoCanceler, noiseSuppressor)
    if (!result) {
      throw IllegalArgumentException("Some parameters specified are not valid");
    }
    return true
  }

  override fun start(getMicrophoneData: GetMicrophoneData) {
    this.getMicrophoneData = getMicrophoneData
    if (!isRunning()) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val mediaProjectionCallback = object : MediaProjection.Callback() {}
        handlerThread.start()
        mediaProjection.registerCallback(mediaProjectionCallback, Handler(handlerThread.looper))
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
          .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
          .addMatchingUsage(AudioAttributes.USAGE_GAME)
          .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build()
        val result = microphone.createInternalMicrophone(config, sampleRate, isStereo,
          echoCanceler, noiseSuppressor)
        if (!result) {
          throw IllegalArgumentException("Failed to create internal audio source")
        }
      } else {
        throw IllegalStateException("Using internal audio in a invalid Android version. Android 10+ is necessary")
      }
      microphone.start()
    }
  }

  override fun stop() {
    if (isRunning()) {
      this.getMicrophoneData = null
      microphone.stop()
      handlerThread.quitSafely()
    }
  }

  override fun isRunning(): Boolean = microphone.isRunning

  override fun release() {
    mediaProjection.stop()
  }

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