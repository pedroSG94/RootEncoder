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

/**
 * Created by pedro on 29/04/21.
 *
 * ISO 14496-3
 */
class AudioSpecificConfig(private val type: Int, private val sampleRate: Int, private val channels: Int) {

  /** supported sampleRates.  */
  private val AUDIO_SAMPLING_RATES = intArrayOf(
      96000,  // 0
      88200,  // 1
      64000,  // 2
      48000,  // 3
      44100,  // 4
      32000,  // 5
      24000,  // 6
      22050,  // 7
      16000,  // 8
      12000,  // 9
      11025,  // 10
      8000,  // 11
      7350,  // 12
  )

  val size = 9

  fun write(buffer: ByteArray, offset: Int) {
    writeConfig(buffer, offset)
    writeAdts(buffer, offset + 2)
  }

  private fun writeConfig(buffer: ByteArray, offset: Int) {
    val frequency = getFrequency()
    buffer[offset] = ((type shl 3) or (frequency shr 1)).toByte()
    buffer[offset + 1] = (frequency shl 7 and 0x80).plus(channels shl 3 and 0x78).toByte()
  }

  private fun writeAdts(buffer: ByteArray, offset: Int) {
    val frequency = getFrequency()
    buffer[offset] = 0xFF.toByte()
    buffer[offset + 1] = 0xF9.toByte()
    buffer[offset + 2] = (((type - 1) shl 6) or (frequency shl 2) or (channels shr 2)).toByte()
    buffer[offset + 3] = (((channels and 3) shl 6) or (buffer.size shr 11)).toByte()
    buffer[offset + 4] = ((buffer.size and 0x7FF) shr 3).toByte()
    buffer[offset + 5] = (((buffer.size and 7) shl 5).toByte()).plus(0x1F).toByte()
    buffer[offset + 6] = 0xFC.toByte()
  }

  private fun getFrequency(): Int {
    var frequency = AUDIO_SAMPLING_RATES.indexOf(sampleRate)
    //sane check, if samplerate not found using default 44100
    if (frequency == -1) frequency = 4
    return frequency
  }
}