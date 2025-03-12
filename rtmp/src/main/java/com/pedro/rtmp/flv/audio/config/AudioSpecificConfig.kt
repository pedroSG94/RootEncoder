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

package com.pedro.rtmp.flv.audio.config

import com.pedro.common.AudioUtils

/**
 * Created by pedro on 29/04/21.
 *
 * ISO 14496-3
 */
class AudioSpecificConfig(private val type: Int, private val sampleRate: Int, private val channels: Int) {

  val size = 9

  fun write(buffer: ByteArray, offset: Int) {
    writeConfig(buffer, offset)
    val adts = AudioUtils.createAdtsHeader(type, buffer.size, sampleRate, channels)
    adts.get(buffer, offset + 2, adts.capacity())
  }

  private fun writeConfig(buffer: ByteArray, offset: Int) {
    val frequency = AudioUtils.getFrequency(sampleRate)
    buffer[offset] = ((type shl 3) or (frequency shr 1)).toByte()
    buffer[offset + 1] = (frequency shl 7 and 0x80).plus(channels shl 3 and 0x78).toByte()
  }
}