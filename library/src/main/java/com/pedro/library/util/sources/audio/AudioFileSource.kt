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

import android.content.Context
import android.net.Uri
import com.pedro.encoder.Frame
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.decoder.AudioDecoder
import com.pedro.encoder.input.decoder.DecoderInterface

/**
 * Created by pedro on 12/1/24.
 */
class AudioFileSource(
  private val context: Context,
  private val path: Uri
): AudioSource(), GetMicrophoneData {

  private var running = false
  private val audioDecoder = AudioDecoder(null, {}, object: DecoderInterface {
    override fun onLoop() {

    }
  }).apply { isLoopMode = true }

  override fun create(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean): Boolean {
    //create extractor to confirm valid parameters
    val result = audioDecoder.initExtractor(context, path, null)
    if (!result) {
      throw IllegalArgumentException("Audio file track not found")
    }
    if (audioDecoder.sampleRate != sampleRate) {
      throw IllegalArgumentException("Audio file sample rate (${audioDecoder.sampleRate}) is different than the configured: $sampleRate")
    }
    if (audioDecoder.isStereo != isStereo) {
      throw IllegalArgumentException("Audio file isStereo (${audioDecoder.isStereo}) is different than the configured: $isStereo")
    }
    return true
  }

  override fun start(getMicrophoneData: GetMicrophoneData) {
    this.getMicrophoneData = getMicrophoneData
    audioDecoder.setGetMicrophoneData(getMicrophoneData)
    audioDecoder.prepareAudio()
    audioDecoder.start()
    running = true
  }

  override fun stop() {
    running = false
    audioDecoder.stop()
  }

  override fun isRunning(): Boolean = running

  override fun release() {}

  override fun getMaxInputSize(): Int = audioDecoder.size

  override fun setMaxInputSize(size: Int) { }

  override fun inputPCMData(frame: Frame) {
    getMicrophoneData?.inputPCMData(frame)
  }

  fun mute() {
    audioDecoder.mute()
  }

  fun unMute() {
    audioDecoder.unMute()
  }

  fun isMuted(): Boolean = audioDecoder.isMuted

  fun moveTo(time: Double) {
    audioDecoder.moveTo(time)
  }
}