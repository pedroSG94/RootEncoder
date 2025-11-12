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

package com.pedro.encoder.input.sources.audio

import com.pedro.common.TimeUtils
import com.pedro.encoder.Frame
import com.pedro.encoder.audio.AudioEncoder
import com.pedro.encoder.input.audio.GetMicrophoneData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Created by pedro on 25/7/25.
 */
class SilenceAudioSource: AudioSource(), GetMicrophoneData {

  private var running = false
  private var job: Job? = null
  private var sleepTime = 0L
  private val buffer = ByteArray(AudioEncoder.inputSize / 4)

  override fun create(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean): Boolean {
    val channels = if (isStereo) 2 else 1
    sleepTime = ((buffer.size.toDouble() / (sampleRate * channels * 2L)) * 1000000L).toLong()
    return true
  }

  override fun start(getMicrophoneData: GetMicrophoneData) {
    this.getMicrophoneData = getMicrophoneData
    running = true
    job = CoroutineScope(Dispatchers.IO).launch {
      while (running) {
        getMicrophoneData.inputPCMData(Frame(buffer, 0, buffer.size, TimeUtils.getCurrentTimeMicro()))
        delay(sleepTime)
      }
    }
  }

  override fun stop() {
    running = false
    runBlocking { job?.cancelAndJoin() }
  }

  override fun isRunning(): Boolean = running

  override fun release() {}

  override fun inputPCMData(frame: Frame) {
    getMicrophoneData?.inputPCMData(frame)
  }
}