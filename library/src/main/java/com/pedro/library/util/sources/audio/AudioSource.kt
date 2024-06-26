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

import com.pedro.encoder.input.audio.GetMicrophoneData

/**
 * Created by pedro on 11/1/24.
 */
abstract class AudioSource {

  protected var getMicrophoneData: GetMicrophoneData? = null
  var created = false
  var sampleRate = 0
  var isStereo = true
  var echoCanceler = false
  var noiseSuppressor = false

  fun init(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean): Boolean {
    this.sampleRate = sampleRate
    this.isStereo = isStereo
    this.echoCanceler = echoCanceler
    this.noiseSuppressor = noiseSuppressor
    created = create(sampleRate, isStereo, echoCanceler, noiseSuppressor)
    return created
  }

  protected abstract fun create(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean): Boolean
  abstract fun start(getMicrophoneData: GetMicrophoneData)
  abstract fun stop()
  abstract fun isRunning(): Boolean
  abstract fun release()
  abstract fun getMaxInputSize(): Int
  abstract fun setMaxInputSize(size: Int)
}